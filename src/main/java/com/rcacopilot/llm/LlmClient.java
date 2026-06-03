package com.rcacopilot.llm;

import java.util.List;

public interface LlmClient {
    /**
     * Summarizes raw diagnostic telemetry logs into a concise summary (~120-140 words).
     */
    String summarize(String diagnosticInfo) throws Exception;

    /**
     * Predicts the root cause category and provides an explanation using few-shot neighbors.
     *
     * @param targetSummary the summarized diagnostic info of the incident to diagnose.
     * @param options a list of candidate demonstrations from similarity search.
     * @return the raw LLM output containing predicted category and explanation.
     */
    String predictRootCause(String targetSummary, List<String> options) throws Exception;
}
