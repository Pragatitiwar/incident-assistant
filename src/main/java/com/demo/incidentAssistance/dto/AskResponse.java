package com.demo.incidentAssistance.dto;

import java.util.List;

public record AskResponse(String answer, List<Source> sources) {
}
