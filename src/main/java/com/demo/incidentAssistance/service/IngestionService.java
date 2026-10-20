package com.demo.incidentAssistance.service;

import com.demo.incidentAssistance.dto.IngestionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class IngestionService {
    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final VectorStore vectorStore;
    private final ResourcePatternResolver resolver;

    public IngestionService(VectorStore vectorStore, ResourcePatternResolver resolver) {
        this.vectorStore = vectorStore;
        this.resolver = resolver;
    }

    public IngestionResult ingestRunbook() throws IOException {
        Resource files [] = resolver.getResources("classpath:runbooks/*.md");
        List<Document> chunks = new ArrayList<>();

        for (Resource file : files) {
            String source = file.getFilename().replace(".md", "");
            String content = file.getContentAsString(StandardCharsets.UTF_8);

            chunks.addAll(chunk(source,content));
        }
        vectorStore.add(chunks);
        log.info("Ingested {} runbooks as {} chunks", files.length, chunks.size());

        return new IngestionResult(files.length, chunks.size());
    }

    private List<Document> chunk(String source, String content) {
        String[] parts = content.split("(?m)^## ");

        String title = parts[0].lines().findFirst().orElse(source).replaceFirst("^# ", "");
        List<Document> docs = new ArrayList<>();
        docs.add(toDocument(source, title, "Overview", parts[0]));

        for (int i=1; i<parts.length; i++) {
            String section = parts[i].lines().findFirst().orElse("Section " +i).trim();
            String body = parts[i].substring(parts[i].indexOf('\n') + 1).trim();

            if (!body.isBlank()) {
                docs.add(toDocument(source, title, section, body));
            }

        }

        return docs;
    }

    private Document toDocument(String source, String title, String section, String body) {

        String text = "Runbook: " + title + "\nSection:" + section + "\n\n" + body.trim();
        String id = UUID.nameUUIDFromBytes((source + "#" + section).getBytes(StandardCharsets.UTF_8)).toString();

        return new Document(id, text, Map.of("source", source, "title", title, "section", section));
    }

}
