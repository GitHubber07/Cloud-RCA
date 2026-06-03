package com.rcacopilot;

import com.rcacopilot.similarity.FastTextEmbedder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class FastTextEmbedderTest {
    private File tempModelFile;

    @BeforeEach
    public void setUp() throws IOException {
        tempModelFile = File.createTempFile("test_fasttext", ".vec");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempModelFile))) {
            writer.write("3 4\n"); // 3 words, 4 dimensions
            writer.write("connection 1.0 0.0 0.0 0.0\n");
            writer.write("socket 0.0 2.0 0.0 0.0\n");
            writer.write("con 0.0 0.0 3.0 0.0\n"); // subword for connection
        }
    }

    @AfterEach
    public void tearDown() {
        if (tempModelFile != null && tempModelFile.exists()) {
            tempModelFile.delete();
        }
    }

    @Test
    public void testLoadAndEmbed() throws IOException {
        FastTextEmbedder embedder = new FastTextEmbedder();
        embedder.loadModel(tempModelFile.getAbsolutePath());

        assertEquals(4, embedder.getVectorSize());

        // Test single word exact match
        double[] vecConnection = embedder.getSentenceVector("connection");
        assertArrayEquals(new double[]{1.0, 0.0, 0.0, 0.0}, vecConnection, 0.0001);

        // Test average centroid for multiple words
        double[] vecSentence = embedder.getSentenceVector("connection socket");
        // average of [1,0,0,0] and [0,2,0,0] is [0.5, 1.0, 0.0, 0.0]
        assertArrayEquals(new double[]{0.5, 1.0, 0.0, 0.0}, vecSentence, 0.0001);

        // Test subword fallback (e.g. "connectionxx" has subword "con")
        double[] vecSubword = embedder.getSentenceVector("connectionxx");
        assertArrayEquals(new double[]{0.0, 0.0, 3.0, 0.0}, vecSubword, 0.0001);
    }
}
