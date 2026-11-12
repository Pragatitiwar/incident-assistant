package com.demo.incidentAssistance.service;

import com.demo.incidentAssistance.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class EvalService {
    private static final Logger log = LoggerFactory.getLogger(EvalService.class);

    private final AskService askService;
    private final ObjectMapper mapper;

    public EvalService(AskService askService, ObjectMapper mapper) {
        this.askService = askService;
        this.mapper = mapper;
    }

    public EvalReport run(String set) throws IOException {
        if (!set.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("Invalid question set name");
        }
        EvalQuestion[] questions;
        try (InputStream in = new ClassPathResource("eval/" + set + ".json").getInputStream()) {
            questions = mapper.readValue(in, EvalQuestion[].class);
        }

        List<EvalCaseResult> results = new ArrayList<>();
        for (EvalQuestion q : questions) {
            try {
                results.add(evaluate(q));
            } catch (Exception e) {
                log.error("Eval question {} failed", q.id(), e);
                results.add(new EvalCaseResult(q.id(), q.type(), q.question(),
                        false, 0.0, false, false, false,
                        List.of("ERROR"), "ERROR: " + e.getMessage()));
            }
        }

        List<EvalCaseResult> answerable = results.stream().filter(r -> !isUnanswerable(r, questions)).toList();
        List<EvalCaseResult> unanswerable = results.stream().filter(r -> isUnanswerable(r, questions)).toList();
        double avgChunkRecall = Math.round(1000.0 * answerable.stream()
                .mapToDouble(EvalCaseResult::chunkRecall).average().orElse(0)) / 10.0;

        return new EvalReport(
                results.size(),
                (int) results.stream().filter(EvalCaseResult::passed).count(),
                rate(answerable, EvalCaseResult::retrievalHit),
                avgChunkRecall,
                rate(answerable, EvalCaseResult::factsPresent),
                rate(unanswerable, EvalCaseResult::refused),
                results);
    }

    private EvalCaseResult evaluate(EvalQuestion q) {
        AskResponse response = askService.ask(q.question());
        String answer = response.answer() == null ? "" : response.answer();
        String normalized = normalize(answer);

        boolean refused = normalized.contains("find this in the runbooks");
        Set<String> retrieved = response.sources().stream()
                .map(Source::source)
                .collect(Collectors.toSet());

        Set<String> retrievedChunks = response.sources().stream()
                .map(s -> s.source() + "#" + s.section())
                .collect(Collectors.toSet());

        List<String> expectedChunks = q.expectedChunks() == null ? List.of() : q.expectedChunks();
        double chunkRecall = expectedChunks.isEmpty() ? 1.0
                : (double) expectedChunks.stream().filter(retrievedChunks::contains).count()
                / expectedChunks.size();

        boolean retrievalHit;
        if (q.expectedSources().isEmpty()) {
            retrievalHit = true;
        } else if (q.type().equals("cross-runbook")) {
            retrievalHit = retrieved.containsAll(q.expectedSources());
        } else {
            retrievalHit = q.expectedSources().stream().anyMatch(retrieved::contains);
        }

        List<String> missing = q.mustContain().stream()
                .filter(fact -> !normalized.contains(normalize(fact)))
                .toList();
        boolean factsPresent = missing.isEmpty() && !refused;

        boolean passed = q.expectDontKnow() ? refused : factsPresent;

        return new EvalCaseResult(q.id(), q.type(), q.question(),
                retrievalHit, chunkRecall, factsPresent, refused, passed, missing, answer);

    }

    private boolean isUnanswerable(EvalCaseResult r, EvalQuestion[] questions) {
        return r.type().equals("unanswerable");
    }

    private double rate(List<EvalCaseResult> cases, Predicate<EvalCaseResult> test) {
        if (cases.isEmpty()) return 0;
        long ok = cases.stream().filter(test).count();
        return Math.round(1000.0 * ok / cases.size()) / 10.0;
    }

    private String normalize(String s) {
        return s.toLowerCase().replace('’', '\'');
    }
}
