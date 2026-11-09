package com.pokemonoptimizer.pokemon_optimizer_backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The whole app against the dev database: what an uptime monitor sees when it polls. */
@SpringBootTest
@AutoConfigureMockMvc
public class HealthEndpointTest
{
    @Autowired
    private MockMvcTester mvc;

    @Test
    public void healthIsUpWhenTheDatabaseAnswersAndShowsNoDetails()
    {
        assertThat(mvc.get().uri("/actuator/health"))
            .hasStatusOk()
            .bodyJson().satisfies(json -> {
                assertThat(json).extractingPath("$.status").isEqualTo("UP");
                assertThat(json).doesNotHavePath("$.components");   // no database name, disk space or versions
                assertThat(json).doesNotHavePath("$.details");
            });
    }

    @Test
    public void noOtherActuatorEndpointIsReachable()
    {   // env, beans, heapdump and friends would hand out secrets and internals
        assertThat(mvc.get().uri("/actuator/env")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/actuator/heapdump")).hasStatus(HttpStatus.NOT_FOUND);
    }
}
