package com.demo.incidentAssistance.service;

import com.demo.incidentAssistance.dto.SearchResult;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrievalService {

    private final VectorStore vectorStore;

    public RetrievalService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<SearchResult> search(String question, int topK) {

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(topK).build()
        );

        return docs.stream().map(
                d -> new SearchResult(
                        String.valueOf(d.getMetadata().get("source")),
                        String.valueOf(d.getMetadata().get("section")),
                        d.getScore(),
                        d.getText())).toList();
    }
}
