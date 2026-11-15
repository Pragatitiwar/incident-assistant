package com.demo.incidentAssistance.tools;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class OpsToolsTest {

    // match your constructor: new OpsTools(retrievalService, resultsList) or with a third AskMetrics argument
    private final OpsTools tools = new OpsTools(null, new ArrayList<>());
    @Test
    void returnsHealthForAKnownService() {
        assertEquals("DEGRADED", tools.getServiceHealth("payment-service").status());
    }

    @Test
    void normalizesCaseAndWhitespace() {
        assertEquals("payment-service", tools.getServiceHealth("  Payment-Service ").service());
    }

    @Test
    void rejectsAnUnknownServiceWithAHelpfulMessage() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> tools.getServiceHealth("billing-service"));
        assertTrue(e.getMessage().contains("Unknown service"));
        assertTrue(e.getMessage().contains("payment-service"));
    }

    @Test
    void rejectsNullAndBlankNames() {
        assertThrows(IllegalArgumentException.class, () -> tools.lookupDeployment(null));
        assertThrows(IllegalArgumentException.class, () -> tools.getRecentErrors("", 15));
    }

    @Test
    void outOfRangeMinutesAreClampedInsteadOfFailing() {
        assertDoesNotThrow(() -> tools.getRecentErrors("payment-service", 0));
        assertDoesNotThrow(() -> tools.getRecentErrors("payment-service", 100000));
    }
}