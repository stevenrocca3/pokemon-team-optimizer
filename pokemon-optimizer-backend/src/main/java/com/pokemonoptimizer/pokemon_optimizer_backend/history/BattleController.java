package com.pokemonoptimizer.pokemon_optimizer_backend.history;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Battle history's way in. The browser reports each analysis it sees finish (POST /api/v1/battles {jobId}); a guest's
 * browser sends its session token (X-Guest-Token, a random UUID it keeps) so the battle has an owner until they log
 * in, and after logging in it claims them (POST /api/v1/battles/claim).
 */
@RestController
@RequestMapping("/api/v1/battles")
public class BattleController
{
    static final String GUEST_TOKEN = "X-Guest-Token";

    record ReportRequest(String jobId) {}

    record Claimed(int claimed) {}

    private final BattleHistoryService service;

    public BattleController(BattleHistoryService service)
    {
        this.service = service;
    }

    /** 201 when saved now, 200 when it was already in this player's history. */
    @PostMapping
    public ResponseEntity<Void> record(@RequestBody ReportRequest request, @AuthenticationPrincipal OidcUser user,
                                       @RequestHeader(name = GUEST_TOKEN, required = false) String guestToken)
    {
        if (!BattleHistoryService.isJobId(request.jobId()))
        {
            throw new BadRequest("That isn’t an analysis ID.");
        }
        UUID token = parseToken(guestToken);
        if (user == null && token == null)
        {
            throw new BadRequest("Log in, or send this browser’s guest token, to save a battle.");
        }
        boolean saved = service.record(request.jobId(), new BattleOwner(user == null ? null : user.getSubject(), token));
        return ResponseEntity.status(saved ? HttpStatus.CREATED : HttpStatus.OK).build();
    }

    /** Logged in only (SecurityConfig): this browser's guest battles become the Trainer's. */
    @PostMapping("/claim")
    public Claimed claim(@AuthenticationPrincipal OidcUser user,
                         @RequestHeader(name = GUEST_TOKEN, required = false) String guestToken)
    {
        UUID token = parseToken(guestToken);
        if (token == null)
        {
            throw new BadRequest("No guest token to claim battles from.");
        }
        return new Claimed(service.claim(user.getSubject(), token));
    }

    private static UUID parseToken(String token)
    {
        if (token == null || token.isBlank())
        {
            return null;
        }
        try
        {
            return UUID.fromString(token);
        }
        catch (IllegalArgumentException e)
        {
            throw new BadRequest("The guest token isn’t valid.");
        }
    }

    static class BadRequest extends RuntimeException
    {
        BadRequest(String message)
        {
            super(message);
        }
    }

    @ExceptionHandler(BadRequest.class)
    ProblemDetail badRequest(BadRequest e)
    {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** The browser reports it again once it's done. */
    @ExceptionHandler(AnalysisNotFinishedException.class)
    ProblemDetail notFinished(AnalysisNotFinishedException e)
    {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(AnalysisNotFoundException.class)
    ProblemDetail notFound(AnalysisNotFoundException e)
    {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(BattleOwnedElsewhereException.class)
    ProblemDetail ownedElsewhere(BattleOwnedElsewhereException e)
    {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
