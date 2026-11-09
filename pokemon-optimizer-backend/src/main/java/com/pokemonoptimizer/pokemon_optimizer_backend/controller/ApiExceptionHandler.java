package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Anything no other handler claimed is a bug. The log gets the whole stack trace under a short id; the browser
     * gets only that id, never the message, which can hold SQL, hostnames or file paths.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request)
    {
        String errorId = HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextInt());
        log.error("Unhandled error {} on {} {}", errorId, request.getMethod(), request.getRequestURI(), ex);

        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "Something went wrong on our side. If it keeps happening, send feedback with error " + errorId + ".");
        body.setProperty("errorId", errorId);
        return body;
    }

    // Adds an "errors" list so the client knows which fields failed @Valid, not just that the request was bad
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request)
    {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
            .toList();

        ProblemDetail body = ex.getBody();
        body.setDetail("Request has " + errors.size() + " invalid field(s).");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }
}
