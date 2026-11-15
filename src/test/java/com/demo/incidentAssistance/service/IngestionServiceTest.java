package com.demo.incidentAssistance.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IngestionServiceTest {
    // chunk() does not use the vector store or the resolver
    private final IngestionService service = new IngestionService(null, null);

    private static final String RUNBOOK = """
            # Runbook: Sample Service Problem

            **Service:** sample
            **Owner:** Team

            ## Symptoms
            - Something is wrong

            ## Fixes
            - Roll back with `kubectl rollout undo deployment/sample`

            ## Escalation
            - Page the lead
            """;

    @Test
    void producesAnOverviewPlusOneChunkPerSection() {
        List<Document> docs = service.chunk("sample-service", RUNBOOK);

        assertEquals(4, docs.size());
        assertEquals("Overview", docs.get(0).getMetadata().get("section"));
        assertEquals("Fixes", docs.get(2).getMetadata().get("section"));
    }

    @Test
    void chunkTextCarriesACleanTitleAndSection() {
        for (Document d : service.chunk("sample-service", RUNBOOK)) {
            assertTrue(d.getText().startsWith("Runbook: Sample Service Problem\nSection: "));
            assertFalse(d.getText().contains("Runbook: Runbook:"));
        }
    }

    @Test
    void metadataAndContentAreCorrect() {
        Document fixes = service.chunk("sample-service", RUNBOOK).get(2);

        assertEquals("sample-service", fixes.getMetadata().get("source"));
        assertTrue(fixes.getText().contains("kubectl rollout undo deployment/sample"));
    }

    @Test
    void idsAreStableAndUnique() {
        List<Document> first = service.chunk("sample-service", RUNBOOK);
        List<Document> second = service.chunk("sample-service", RUNBOOK);

        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).getId(), second.get(i).getId());
        }
        assertEquals(first.size(), new HashSet<>(first.stream().map(Document::getId).toList()).size());
    }

    @Test
    void everyShippedRunbookProducesSevenChunks() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver()
                .getResources("classpath:runbooks/*.md");

        assertEquals(8, files.length);
        for (Resource f : files) {
            String source = f.getFilename().replace(".md", "");
            List<Document> docs = service.chunk(source, f.getContentAsString(StandardCharsets.UTF_8));
            assertEquals(7, docs.size(), f.getFilename());
        }
    }
}
