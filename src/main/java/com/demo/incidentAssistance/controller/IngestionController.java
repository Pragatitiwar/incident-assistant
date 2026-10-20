package com.demo.incidentAssistance.controller;

import com.demo.incidentAssistance.dto.IngestionResult;
import com.demo.incidentAssistance.service.IngestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/ingest")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/runbooks")
    public IngestionResult ingestRunbooks() throws IOException {
        return ingestionService.ingestRunbook();
    }
}
