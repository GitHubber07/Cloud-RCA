package com.rcacopilot.similarity;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FastTextEmbedder {
    private final Map<String, double[]> wordVectors;
    private int vectorSize = 0;

    public FastTextEmbedder() {
        this.wordVectors = new HashMap<>();
    }

    public int getVectorSize() {
        return vectorSize;
    }

    /**
     * Loads word vectors from a space-separated .vec file.
     * The first line must contain <num_words> <vector_dimension>.
     */
    public void loadModel(String filePath) throws IOException {
        wordVectors.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String header = reader.readLine();
            if (header == null) {
                throw new IOException("Empty model file: " + filePath);
            }

            String[] headerParts = header.trim().split("\\s+");
            if (headerParts.length >= 2) {
                this.vectorSize = Integer.parseInt(headerParts[1]);
            } else {
                throw new IOException("Invalid model header format in " + filePath);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < vectorSize + 1) {
                    continue; // Skip malformed lines
                }

                String word = parts[0].toLowerCase();
                double[] vector = new double[vectorSize];
                for (int i = 0; i < vectorSize; i++) {
                    vector[i] = Double.parseDouble(parts[i + 1]);
                }
                wordVectors.put(word, vector);
            }
        }
    }

    /**
     * Tokenizes text and computes the centroid average of word vectors.
     */
    public double[] getSentenceVector(String sentence) {
        if (vectorSize == 0) {
            throw new IllegalStateException("FastTextEmbedder model has not been loaded yet.");
        }

        double[] centroid = new double[vectorSize];
        if (sentence == null || sentence.trim().isEmpty()) {
            return centroid;
        }

        // Clean and tokenize text
        String[] tokens = sentence.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s]", " ")
                .split("\\s+");

        int validTokenCount = 0;

        for (String token : tokens) {
            if (token.isEmpty()) continue;

            double[] vector = wordVectors.get(token);
            
            // Subword fallback: if exact match is missing, check if any sub-ngrams match (simulates FastText subwords)
            if (vector == null && token.length() > 3) {
                vector = trySubwordCentroid(token);
            }

            if (vector != null) {
                for (int i = 0; i < vectorSize; i++) {
                    centroid[i] += vector[i];
                }
                validTokenCount++;
            }
        }

        // Divide by total found tokens to get the average vector
        if (validTokenCount > 0) {
            for (int i = 0; i < vectorSize; i++) {
                centroid[i] /= validTokenCount;
            }
        }

        return centroid;
    }

    private double[] trySubwordCentroid(String token) {
        double[] subwordSum = new double[vectorSize];
        int subwordCount = 0;
        int n = 3; // Use 3-character n-grams

        for (int i = 0; i <= token.length() - n; i++) {
            String ngram = token.substring(i, i + n);
            double[] vec = wordVectors.get(ngram);
            if (vec != null) {
                for (int j = 0; j < vectorSize; j++) {
                    subwordSum[j] += vec[j];
                }
                subwordCount++;
            }
        }

        if (subwordCount > 0) {
            for (int j = 0; j < vectorSize; j++) {
                subwordSum[j] /= subwordCount;
            }
            return subwordSum;
        }

        return null;
    }
}
