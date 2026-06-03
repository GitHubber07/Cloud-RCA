package com.rcacopilot.similarity;

import com.rcacopilot.db.DatabaseManager;
import com.rcacopilot.model.Incident;
import com.rcacopilot.model.TelemetryLog;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class TemporalSimilaritySearch {

    /**
     * Calculates the Euclidean distance between two embedding vectors.
     */
    public static double calculateEuclideanDistance(double[] v1, double[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vector lengths do not match: " + v1.length + " vs " + v2.length);
        }

        double sum = 0.0;
        for (int i = 0; i < v1.length; i++) {
            double diff = v1[i] - v2[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    /**
     * Calculates the time-decayed similarity score between two incidents.
     * Formula: Similarity(a, b) = [1 / (1 + Distance(a, b))] * e^(-alpha * |T(a) - T(b)|)
     *
     * @param embA embedding vector of incident A
     * @param timeA timestamp of incident A
     * @param embB embedding vector of incident B
     * @param timeB timestamp of incident B
     * @param alpha the decay rate (default 0.3)
     */
    public static double calculateSimilarity(double[] embA, Instant timeA, double[] embB, Instant timeB, double alpha) {
        double distance = calculateEuclideanDistance(embA, embB);
        double spatialSim = 1.0 / (1.0 + distance);

        // Convert the time difference to fractional days
        double timeDiffDays = Math.abs(ChronoUnit.MILLIS.between(timeA, timeB)) / (1000.0 * 60.0 * 60.0 * 24.0);
        double temporalDecay = Math.exp(-alpha * timeDiffDays);

        return spatialSim * temporalDecay;
    }

    /**
     * Finds the top K nearest neighbors from different root cause categories.
     * This guarantees category diversity in the few-shot demonstration prompt.
     */
    public static List<Incident> findNearestNeighbors(
            Incident targetIncident,
            List<Incident> historicalIncidents,
            DatabaseManager db,
            FastTextEmbedder embedder,
            int K,
            double alpha) throws SQLException {

        // Get diagnostic text and embedding for the target incident
        String targetLogs = getAggregatedLogsText(targetIncident.getId(), db);
        double[] targetEmbedding = embedder.getSentenceVector(targetLogs);

        class ScoredIncident {
            final Incident incident;
            final double score;

            ScoredIncident(Incident incident, double score) {
                this.incident = incident;
                this.score = score;
            }
        }

        List<ScoredIncident> scoredList = new ArrayList<>();

        for (Incident hist : historicalIncidents) {
            // Exclude the target incident itself if it exists in history
            if (hist.getId().equals(targetIncident.getId())) {
                continue;
            }

            String histLogs = getAggregatedLogsText(hist.getId(), db);
            double[] histEmbedding = embedder.getSentenceVector(histLogs);

            double score = calculateSimilarity(
                    targetEmbedding, targetIncident.getTimestamp(),
                    histEmbedding, hist.getTimestamp(),
                    alpha
            );

            scoredList.add(new ScoredIncident(hist, score));
        }

        // Sort scored incidents in descending order
        scoredList.sort((a, b) -> Double.compare(b.score, a.score));

        // Filter: Select the top K, but ensure we only keep the highest-scored incident per root cause category
        List<Incident> neighbors = new ArrayList<>();
        Set<String> categoriesSeen = new HashSet<>();

        for (ScoredIncident scored : scoredList) {
            if (neighbors.size() >= K) {
                break;
            }

            String category = scored.incident.getTrueCategory();
            if (!categoriesSeen.contains(category)) {
                categoriesSeen.add(category);
                neighbors.add(scored.incident);
            }
        }

        return neighbors;
    }

    private static String getAggregatedLogsText(String incidentId, DatabaseManager db) throws SQLException {
        List<TelemetryLog> logs = db.getTelemetryLogs(incidentId);
        StringBuilder sb = new StringBuilder();
        for (TelemetryLog log : logs) {
            sb.append(log.getMessage()).append(" ");
        }
        return sb.toString().trim();
    }
}
