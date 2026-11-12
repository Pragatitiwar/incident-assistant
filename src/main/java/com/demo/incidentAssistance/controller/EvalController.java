package com.demo.incidentAssistance.controller;

import com.demo.incidentAssistance.dto.EvalReport;
import com.demo.incidentAssistance.service.EvalService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class EvalController {
    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    @PostMapping("/eval/run")
    public EvalReport run(@RequestParam(name = "set", defaultValue = "questions") String set)
            throws IOException {
        return evalService.run(set);
    }
}
