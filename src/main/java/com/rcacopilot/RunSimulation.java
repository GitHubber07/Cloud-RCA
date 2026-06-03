package com.rcacopilot;

import com.rcacopilot.db.DatabaseManager;
import com.rcacopilot.evaluator.Evaluator;
import com.rcacopilot.generator.MockDataGenerator;
import com.rcacopilot.llm.GroqLlmClient;
import com.rcacopilot.llm.LlmClient;
import com.rcacopilot.llm.MockLlmClient;
import com.rcacopilot.model.Incident;
import com.rcacopilot.similarity.FastTextEmbedder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RunSimulation {
    public static void main(String[] args) {
        System.out.println("Starting RCACopilot Simulator...");

        // Parse command line arguments
        int numHistory = 30;
        int numTest = 5;
        boolean useLiveLlm = false;
        boolean initDb = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--num-history":
                    if (i + 1 < args.length) {
                        numHistory = Integer.parseInt(args[++i]);
                    }
                    break;
                case "--num-test":
                    if (i + 1 < args.length) {
                        numTest = Integer.parseInt(args[++i]);
                    }
                    break;
                case "--live":
                    useLiveLlm = true;
                    break;
                case "--init-db":
                    initDb = true;
                    break;
                default:
                    System.out.println("Unknown argument: " + args[i]);
                    break;
            }
        }

        try {
            // 1. Establish Database Connection
            DatabaseManager dbManager = new DatabaseManager();

            // 2. Initialize schema if requested
            if (initDb) {
                System.out.println("Initializing database schema from schema.sql...");
                dbManager.initializeSchema("schema.sql");
                System.out.println("Database schema initialized successfully.");
            }

            // 3. Generate Mock Data & FastText Model File
            String ftModelPath = "fasttext_model.vec";
            MockDataGenerator generator = new MockDataGenerator();
            
            System.out.println("Generating FastText vocabulary model: " + ftModelPath + "...");
            generator.generateFastTextVecFile(ftModelPath);
            
            if (initDb) {
                System.out.println("Populating database with mock telemetry data...");
                generator.generateAndInsertTelemetry(dbManager, numHistory, numTest);
                System.out.println("Mock data inserted successfully.");
            }

            // 4. Initialize FastText Embedder
            System.out.println("Loading FastText word vector model...");
            FastTextEmbedder embedder = new FastTextEmbedder();
            embedder.loadModel(ftModelPath);
            System.out.println("FastText model loaded (dimensions: " + embedder.getVectorSize() + ").");

            // 5. Initialize LLM Client
            LlmClient llmClient;
            if (useLiveLlm) {
                System.out.println("Configuring Live LLM Client (Llama 3 via Groq API)...");
                llmClient = new GroqLlmClient();
            } else {
                System.out.println("Configuring Offline Mock LLM Client (Deterministic template fallback)...");
                llmClient = new MockLlmClient();
            }

            // 6. Fetch Database Incidents
            List<Incident> allIncidents = dbManager.getAllIncidents();
            List<Incident> testIncidents = new ArrayList<>();
            List<Incident> historicalIncidents = new ArrayList<>();

            for (Incident incident : allIncidents) {
                if (incident.getId().startsWith("INC-TEST-")) {
                    testIncidents.add(incident);
                } else if (incident.getId().startsWith("INC-HIST-")) {
                    historicalIncidents.add(incident);
                }
            }

            if (testIncidents.isEmpty() || historicalIncidents.isEmpty()) {
                System.out.println("\n[Error] Database is empty! Please run with --init-db to populate the database tables first.");
                return;
            }

            // 7. Run Evaluator
            Evaluator evaluator = new Evaluator();
            evaluator.evaluate(testIncidents, historicalIncidents, dbManager, embedder, llmClient);

        } catch (IllegalStateException e) {
            System.err.println("\n[Configuration Error] " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("\n[Execution Error] An unexpected error occurred during simulation:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
