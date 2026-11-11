package com.demo.incidentAssistance.dto;

import java.util.List;

public record EvalReport(int total,
                         int passed,
                         double retrievalHitRate,
                         double chunkRecall,
                         double answerAccuracy,
                         double correctRefusalRate,
                         List<EvalCaseResult> results) {
}
