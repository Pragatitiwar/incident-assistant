package com.demo.incidentAssistance.service;

import com.demo.incidentAssistance.dto.AskRequest;
import com.demo.incidentAssistance.dto.AskResponse;
import com.demo.incidentAssistance.dto.SearchResult;
import com.demo.incidentAssistance.dto.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import javax.smartcardio.ATR;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class AskService {
    private static final Logger log = LoggerFactory.getLogger(AskService.class);

    private static final String SYSTEM_PROMPT = """
            You are an incident-response assistant for backend engineers.
            Answer using ONLY the runbook excerpts provided in the user message.

            Rules:
            - If the excerpts do not contain the answer, reply exactly:
              "I couldn't find this in the runbooks." and stop. Do not use outside
              knowledge, even if you know the answer.
            - Be concise and practical. Quote commands, flags, and thresholds exactly
              as written in the excerpts.
            - Cite the excerpts you used by number in square brackets, like [1] or [2][3].
            - Never invent commands, metrics, feature flags, or names that are not
              in the excerpts.
            """;

    private final RetrievalService retrievalService;
    private final ChatClient chatClient;

    public AskService(RetrievalService retrievalService, ChatClient.Builder builder) {
        this.retrievalService = retrievalService;
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
    }

    public AskResponse ask(String question) {

        List<SearchResult> results = retrievalService.search(question, 4);

        String context = IntStream.range(0, results.size())
                .mapToObj(i -> "[" + (i+1) + "]" + results.get(i).text())
                .collect(Collectors.joining("\n\n---\n\n"));

        String userPrompt = """
                Runbook excerpts:

                %s

                Question: %s
                """.formatted(context, question);
        
        ChatResponse response = chatClient.prompt()
                .user(userPrompt)
                .call()
                .chatResponse();

        log.info("finishReason={}, usage={}, generations={}",
                response.getResult().getMetadata().getFinishReason(),
                response.getMetadata().getUsage(),
                response.getResults().size());

        response.getResults().forEach(g ->
                log.info("generation text length={}",
                        g.getOutput().getText() == null ? 0 : g.getOutput().getText().length()));

        String answer = response.getResults().stream()
                .map(g -> g.getOutput().getText())
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining("\n"));

        List<Source> sources = results.stream()
                .map(r -> new Source(r.source(), r.section()))
                .toList();

        return new AskResponse(answer,sources);

    }
}
