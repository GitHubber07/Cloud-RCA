package com.rcacopilot.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class GroqLlmClient implements LlmClient {
    private final String apiKey;
    private final HttpClient httpClient;
    private final Gson gson;
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL_NAME = "llama3-8b-8192";

    public GroqLlmClient() {
        this.apiKey = System.getenv("GROQ_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Groq API key missing! Please set the GROQ_API_KEY environment variable.");
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.gson = new Gson();
    }

    @Override
    public String summarize(String diagnosticInfo) throws Exception {
        String prompt = "Please summarize the above input. Please note that the above input is incident diagnostic information. " +
                "The summary results should be about 120 words, no more than 140 words, and should cover important information as much as possible. " +
                "Just return the summary without any additional output.";

        String userContent = diagnosticInfo + "\n\n" + prompt;
        return executeChatCompletion("You are a helpful cloud reliability engineering assistant. Summarize telemetry logs precisely.", userContent);
    }

    @Override
    public String predictRootCause(String targetSummary, List<String> options) throws Exception {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Context: The following description shows the error log information of an incident. ")
                .append("Please select the incident information that is most likely to have the same root cause and give your explanation (just give one answer). ")
                .append("If not, please select the first item \"Unseen incident\".\n\n");

        promptBuilder.append("Input: ").append(targetSummary).append("\n\n");
        promptBuilder.append("Options:\n");
        promptBuilder.append("A: Unseen incident.\n");

        char optionLetter = 'B';
        for (String option : options) {
            promptBuilder.append(optionLetter).append(": ").append(option).append("\n");
            optionLetter++;
        }

        return executeChatCompletion("You are a cloud incident root cause analysis expert.", promptBuilder.toString());
    }

    private String executeChatCompletion(String systemMessage, String userMessage) throws Exception {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", MODEL_NAME);
        requestBody.addProperty("temperature", 0.0);

        JsonArray messages = new JsonArray();

        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemMessage);
        messages.add(sysMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);

        requestBody.add("messages", messages);

        String jsonPayload = gson.toJson(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Groq API request failed with HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        return responseJson.get("choices").getAsJsonArray()
                .get(0).getAsJsonObject()
                .get("message").getAsJsonObject()
                .get("content").getAsString();
    }
}
