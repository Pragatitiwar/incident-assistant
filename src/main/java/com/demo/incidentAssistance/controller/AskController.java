package com.demo.incidentAssistance.controller;

import com.demo.incidentAssistance.dto.AskRequest;
import com.demo.incidentAssistance.dto.AskResponse;
import com.demo.incidentAssistance.service.AskService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AskController {

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest askRequest) {
        return askService.ask(askRequest.question());
    }
}
