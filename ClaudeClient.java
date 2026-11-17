package com.notesai.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Minimal client for the Anthropic Messages API (no extra SDK needed). */
@Service
public class ClaudeClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper mapper;

    @Value("${anthropic.api-key:}")
    private String apiKey;
    @Value("${anthropic.model}")
    private String model;
    @Value("${anthropic.max-tokens}")
    private int maxTokens;
    @Value("${anthropic.base-url}")
    private String baseUrl;

    public ClaudeClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Sends one prompt and returns the model's text reply. */
    public String complete(String system, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "ANTHROPIC_API_KEY is not set on the server.");
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", maxTokens);
            body.put("system", system);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "user").put("content", userPrompt);

            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(120))
                    .header("content-type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "AI service returned " + res.statusCode() + ": " + errorMessage(res.body()));
            }
            StringBuilder text = new StringBuilder();
            for (JsonNode block : mapper.readTree(res.body()).path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
            return text.toString();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach the AI service.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Request was interrupted.");
        }
    }

    private String errorMessage(String json) {
        try {
            return mapper.readTree(json).path("error").path("message").asText("unknown error");
        } catch (IOException e) {
            return "unknown error";
        }
    }
}