package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;

@WebMvcTest(MoveController.class)
@Import(SecurityConfig.class)
@ExtendWith(OutputCaptureExtension.class)
public class ApiExceptionHandlerTest
{
    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private MoveLookup moveLookup;

    @Test
    public void anUnexpectedErrorIsLoggedWithAnIdAndTheBrowserGetsOnlyTheId(CapturedOutput output) throws Exception
    {
        when(moveLookup.findAll()).thenThrow(new IllegalStateException("jdbc:postgresql://db.internal password=hunter2"));

        MvcTestResult result = mvc.get().uri("/api/moves").exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("hunter2").doesNotContain("postgresql").doesNotContain("IllegalStateException");
        String errorId = com.jayway.jsonpath.JsonPath.read(body, "$.errorId");
        assertThat(errorId).matches("[0-9a-f]{8}");
        assertThat(body).contains("Something went wrong");

        // the log keeps everything needed to find the cause from the id a user reports
        assertThat(output).contains(errorId).contains("GET /api/moves").contains("IllegalStateException").contains("hunter2");
    }
}
