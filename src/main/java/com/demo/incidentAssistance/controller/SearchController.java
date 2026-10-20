package com.demo.incidentAssistance.controller;

import com.demo.incidentAssistance.dto.SearchResult;
import com.demo.incidentAssistance.service.RetrievalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SearchController {

    private final RetrievalService retrievalService;

    public SearchController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @GetMapping("/search")
    public List<SearchResult> search(@RequestParam("q") String question,
                                     @RequestParam(name="k", defaultValue = "4") int topK) {
        return retrievalService.search(question,topK);
    }
}
