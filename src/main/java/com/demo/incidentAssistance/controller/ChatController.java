package com.demo.incidentAssistance.controller;


import com.demo.incidentAssistance.dto.ChatRequest;
import com.demo.incidentAssistance.dto.ChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    public final ChatClient chatClient;


    public ChatController(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("You are an assistant that helps backend engineers "
                        + "troubleshoot production incidents. Be concise and practical.")
                .build();;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String answer = chatClient.prompt()
                .user(request.question())
                .call().content();
        return new ChatResponse(answer);
    }
}
