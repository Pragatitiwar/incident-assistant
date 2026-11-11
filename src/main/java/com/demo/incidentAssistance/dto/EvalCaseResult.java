package com.demo.incidentAssistance.dto;

import java.util.List;

public record EvalCaseResult(int id,
                             String type,
                             String question,
                             boolean retrievalHit,
                             double chunkRecall,
                             boolean factsPresent,
                             boolean refused,
                             boolean passed,
                             List<String> missingFacts,
                             String answer) {
}
