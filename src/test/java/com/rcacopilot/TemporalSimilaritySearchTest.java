package com.rcacopilot;

import com.rcacopilot.similarity.TemporalSimilaritySearch;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

public class TemporalSimilaritySearchTest {

    @Test
    public void testDistanceCalculation() {
        double[] v1 = {1.0, 2.0, 3.0};
        double[] v2 = {1.0, 5.0, 7.0};

        // diffs: 0, 3, 4. squared sum: 0 + 9 + 16 = 25. sqrt: 5.0
        double dist = TemporalSimilaritySearch.calculateEuclideanDistance(v1, v2);
        assertEquals(5.0, dist, 0.0001);
    }

    @Test
    public void testSimilarityWithTemporalDecay() {
        double[] embA = {0.0, 0.0};
        double[] embB = {0.0, 0.0}; // Distance = 0. spatialSim = 1 / (1 + 0) = 1.0

        Instant now = Instant.now();
        Instant oneDayLater = now.plus(1, ChronoUnit.DAYS);

        // Alpha = 0.3. e^(-0.3 * 1.0 days) = e^(-0.3) = 0.7408
        double sim = TemporalSimilaritySearch.calculateSimilarity(embA, now, embB, oneDayLater, 0.3);
        assertEquals(Math.exp(-0.3), sim, 0.0001);
    }
}
