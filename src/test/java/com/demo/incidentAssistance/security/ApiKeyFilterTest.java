package com.demo.incidentAssistance.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ApiKeyFilterTest {
    private static final String USER = "user-key-0123456789abcdef";
    private static final String ADMIN = "admin-key-0123456789abcdef";

    @RestController
    static class StubController {
        @GetMapping({"/search", "/eval/run", "/actuator/health", "/actuator/metrics"})
        String ok() {
            return "ok";
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new StubController())
                .addFilters(new ApiKeyFilter(USER, ADMIN, 2))
                .build();
    }

    @Test
    void rejectsRequestsWithoutAKey() throws Exception {
        mvc.perform(get("/search")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAWrongKey() throws Exception {
        mvc.perform(get("/search").header("X-API-Key", "not-the-right-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userKeyOpensUserEndpoints() throws Exception {
        mvc.perform(get("/search").header("X-API-Key", USER)).andExpect(status().isOk());
    }

    @Test
    void userKeyIsForbiddenOnAdminEndpoints() throws Exception {
        mvc.perform(get("/eval/run").header("X-API-Key", USER)).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics").header("X-API-Key", USER)).andExpect(status().isForbidden());
    }

    @Test
    void adminKeyOpensAdminEndpoints() throws Exception {
        mvc.perform(get("/eval/run").header("X-API-Key", ADMIN)).andExpect(status().isOk());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void rateLimitsTheUserKeyAndSetsRetryAfter() throws Exception {
        mvc.perform(get("/search").header("X-API-Key", USER)).andExpect(status().isOk());
        mvc.perform(get("/search").header("X-API-Key", USER)).andExpect(status().isOk());
        mvc.perform(get("/search").header("X-API-Key", USER))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void doesNotRateLimitTheAdminKey() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/search").header("X-API-Key", ADMIN)).andExpect(status().isOk());
        }
    }

    @Test
    void refusesToStartWithWeakOrIdenticalKeys() {
        assertThrows(IllegalStateException.class, () -> new ApiKeyFilter("short", ADMIN, 10));
        assertThrows(IllegalStateException.class, () -> new ApiKeyFilter(USER, USER, 10));
    }
}
