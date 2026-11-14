package com.demo.incidentAssistance.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class AskMetrics {
    private final MeterRegistry registry;
    private final double inputPricePerMillion;
    private final double outputPricePerMillion;

    public AskMetrics(MeterRegistry registry,
                      @Value("${assistant.pricing.input-per-million}") double inputPricePerMillion,
                      @Value("${assistant.pricing.output-per-million}") double outputPricePerMillion) {
        this.registry = registry;
        this.inputPricePerMillion = inputPricePerMillion;
        this.outputPricePerMillion = outputPricePerMillion;
    }

    /** Records one finished request and returns its estimated cost in USD. */
    public double recordAsk(long nanos, long inputTokens, long outputTokens, boolean refused) {
        Timer.builder("assistant.ask.duration")
                .tag("outcome", refused ? "refused" : "answered")
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
        registry.counter("assistant.tokens", "type", "input").increment(inputTokens);
        registry.counter("assistant.tokens", "type", "output").increment(outputTokens);

        double cost = inputTokens * inputPricePerMillion / 1_000_000.0
                + outputTokens * outputPricePerMillion / 1_000_000.0;
        registry.counter("assistant.cost.usd").increment(cost);
        return cost;
    }

    public void recordFailure() {
        registry.counter("assistant.ask.failures").increment();
    }

    public void recordToolCall(String tool) {
        registry.counter("assistant.tool.calls", "tool", tool).increment();
    }
}
