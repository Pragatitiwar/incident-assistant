package com.demo.incidentAssistance.tools;

import com.demo.incidentAssistance.dto.SearchResult;
import com.demo.incidentAssistance.service.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class OpsTools {
    private final RetrievalService retrievalService;
    private final List<SearchResult> retrieved;

    public OpsTools(RetrievalService retrievalService, List<SearchResult> retrieved) {
        this.retrievalService = retrievalService;
        this.retrieved = retrieved;
    }

    private static final Logger log = LoggerFactory.getLogger(OpsTools.class);

    public record ServiceHealth(String service, String status, double errorRatePercent,
                                int p99LatencyMs, String podsReady, String note) {}

    public record Deployment(String service, String version, int hoursAgo, String deployedBy) {}

    private static final Map<String, ServiceHealth> HEALTH = Map.of(
            "payment-service", new ServiceHealth("payment-service", "DEGRADED", 7.2, 1850, "8/8",
                    "hikaricp_connections_pending=14, hikaricp_connections_active=20 of 20"),
            "order-processor", new ServiceHealth("order-processor", "DEGRADED", 0.4, 900, "6/12",
                    "kafka_consumergroup_lag=52000 on order-events"),
            "checkout-api", new ServiceHealth("checkout-api", "HEALTHY", 0.3, 640, "10/10",
                    "p99 within normal range"),
            "inventory-service", new ServiceHealth("inventory-service", "HEALTHY", 0.1, 120, "4/4",
                    "no issues"));

    private static final Map<String, List<String>> ERRORS = Map.of(
            "payment-service", List.of(
                    "ERROR HikariPool-1 - Connection is not available, request timed out after 30000ms",
                    "ERROR PaymentController - checkout failed: could not obtain DB connection",
                    "ERROR HikariPool-1 - Connection is not available, request timed out after 30000ms"),
            "order-processor", List.of(
                    "WARN  OrderConsumer - processing took 4200ms for order-events partition 3",
                    "WARN  KafkaConsumer - rebalance triggered, consumer group order-processor"),
            "checkout-api", List.of(),
            "inventory-service", List.of());

    private static final Map<String, Deployment> DEPLOYMENTS = Map.of(
            "payment-service", new Deployment("payment-service", "v2.41.0", 72, "ci-pipeline"),
            "order-processor", new Deployment("order-processor", "v1.18.3", 5, "ci-pipeline"),
            "checkout-api", new Deployment("checkout-api", "v3.07.1", 200, "ci-pipeline"),
            "inventory-service", new Deployment("inventory-service", "v0.92.4", 410, "ci-pipeline"));

    private static final Set<String> KNOWN = HEALTH.keySet();

    @Tool(description = "Get the current health snapshot of a service: status, error rate, "
            + "p99 latency, ready pods and notes. Read-only.")
    public ServiceHealth getServiceHealth(
            @ToolParam(description = "Service name, e.g. payment-service") String service) {
        String name = validate(service);
        log.info("TOOL getServiceHealth({})", name);
        return HEALTH.get(name);
    }

    @Tool(description = "Get recent error and warning log lines for a service. Read-only.")
    public List<String> getRecentErrors(
            @ToolParam(description = "Service name, e.g. payment-service") String service,
            @ToolParam(description = "Look-back window in minutes, between 1 and 60") int minutes) {
        String name = validate(service);
        int window = Math.max(1, Math.min(minutes, 60));
        log.info("TOOL getRecentErrors({}, {} min)", name, window);
        return ERRORS.get(name);
    }

    @Tool(description = "Get the most recent deployment of a service: version and hours ago. Read-only.")
    public Deployment lookupDeployment(
            @ToolParam(description = "Service name, e.g. payment-service") String service) {
        String name = validate(service);
        log.info("TOOL lookupDeployment({})", name);
        return DEPLOYMENTS.get(name);
    }

    private String validate(String service) {
        String name = service == null ? "" : service.trim().toLowerCase();
        if (!KNOWN.contains(name)) {
            throw new IllegalArgumentException(
                    "Unknown service '" + service + "'. Known services: " + String.join(", ", KNOWN));
        }
        return name;
    }

    @Tool(description = "Search the runbooks. Use after learning what is wrong, with specific "
            + "terms like the symptom or metric, to find diagnostic steps and fixes.")
    public List<String> searchRunbooks(
            @ToolParam(description = "Search query, e.g. 'connection pool exhausted fixes'") String query) {
        log.info("TOOL searchRunbooks({})", query);
        List<SearchResult> results = retrievalService.search(query, 5);
        retrieved.addAll(results);
        return results.stream()
                .map(r -> "[" + r.source() + " / " + r.section() + "] " + r.text())
                .toList();
    }
}