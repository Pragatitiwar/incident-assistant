package com.demo.incidentAssistance.dto;

import java.util.List;

public record EvalQuestion(int id,
                           String type,
                           String question,
                           List<String> expectedSources,
                           List<String> mustContain,
                           boolean expectDontKnow,
                           List<String> expectedChunks) {
}
