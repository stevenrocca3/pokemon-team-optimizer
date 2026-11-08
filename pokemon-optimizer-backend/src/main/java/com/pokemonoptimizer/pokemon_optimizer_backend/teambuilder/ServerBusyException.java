package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Every Team Builder slot stayed taken for the whole wait: a 503 that tells the browser when to try again. */
public class ServerBusyException extends ErrorResponseException
{
    public ServerBusyException(long retryAfterSeconds)
    {
        super(HttpStatus.SERVICE_UNAVAILABLE, ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
            "The Team Builder is busy with other searches. Try again in " + retryAfterSeconds + " seconds."), null);
        getHeaders().set("Retry-After", String.valueOf(retryAfterSeconds));
    }
}
