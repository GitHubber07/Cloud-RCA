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
        boolean startServer = false;
        int neighborsK = 5;
        double decayAlpha = 0.3;
        boolean runTuning = false;

        if (args.length == 0) {
            startServer = true;
        }

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
                case "--server":
                    startServer = true;
                    break;
                case "--neighbors":
                case "-k":
                    if (i + 1 < args.length) {
                        neighborsK = Integer.parseInt(args[++i]);
                    }
                    break;
                case "--alpha":
                case "-a":
                    if (i + 1 < args.length) {
                        decayAlpha = Double.parseDouble(args[++i]);
                    }
                    break;
                case "--tune":
                    runTuning = true;
                    break;
                default:
                    System.out.println("Unknown argument: " + args[i]);
                    break;
            }
        }

        try {
            // 1. Establish Database Connection
            DatabaseManager dbManager = new DatabaseManager();

            // 2. Start Server Mode if requested
            if (startServer) {
                System.out.println("Starting in SERVER mode on port 8080...");
                
                // Automatically initialize database schema if tables don't exist
                try {
                    dbManager.getAllIncidents();
                } catch (Exception e) {
                    System.out.println("Database tables not found. Automatically initializing schema from schema.sql...");
                    dbManager.initializeSchema("schema.sql");
                }
                
                // Ensure fasttext_model.vec exists
                String ftModelPath = "fasttext_model.vec";
                File f = new File(ftModelPath);
                if (!f.exists()) {
                    System.out.println("Generating default FastText vocabulary model...");
                    new MockDataGenerator().generateFastTextVecFile(ftModelPath);
                }

                // Start Dashboard Server
                com.rcacopilot.server.DashboardServer server = new com.rcacopilot.server.DashboardServer(8080, dbManager);
                server.start();

                // Seed workflows
                System.out.println("Seeding default workflows if database is empty...");
                Evaluator evaluator = new Evaluator();
                evaluator.seedDefaultWorkflows(dbManager);

                System.out.println("Server is running. Open http://localhost:8080 in your browser.");
                System.out.println("Press Ctrl+C to terminate the application.");
                
                // Keep main thread alive
                while (true) {
                    Thread.sleep(5000);
                }
            }

            // 3. Initialize schema if requested (CLI mode)
            if (initDb) {
                System.out.println("Initializing database schema from schema.sql...");
                dbManager.initializeSchema("schema.sql");
                System.out.println("Database schema initialized successfully.");
            }

            // 4. Generate Mock Data & FastText Model File
            String ftModelPath = "fasttext_model.vec";
            MockDataGenerator generator = new MockDataGenerator();
            
            System.out.println("Generating FastText vocabulary model: " + ftModelPath + "...");
            generator.generateFastTextVecFile(ftModelPath);
            
            if (initDb) {
                System.out.println("Populating database with mock telemetry data...");
                generator.generateAndInsertTelemetry(dbManager, numHistory, numTest);
                System.out.println("Mock data inserted successfully.");
            }

            // 5. Initialize FastText Embedder
            System.out.println("Loading FastText word vector model...");
            FastTextEmbedder embedder = new FastTextEmbedder();
            embedder.loadModel(ftModelPath);
            System.out.println("FastText model loaded (dimensions: " + embedder.getVectorSize() + ").");

            // 6. Initialize LLM Client
            LlmClient llmClient;
            if (useLiveLlm) {
                System.out.println("Configuring Live LLM Client (Llama 3 via Groq API)...");
                llmClient = new GroqLlmClient();
            } else {
                System.out.println("Configuring Offline Mock LLM Client (Deterministic template fallback)...");
                llmClient = new MockLlmClient();
            }

            // 7. Fetch Database Incidents
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
                System.out.println("\n[Error] Database is empty! Please run with --init-db or start the --server to generate mock incidents.");
                return;
            }

            // 8. Run Evaluator / Parameter Sweep
            Evaluator evaluator = new Evaluator();
            if (runTuning) {
                System.out.println("\n=======================================================");
                System.out.println("            RCACopilot Parameter Tuning Sweep          ");
                System.out.println("=======================================================");
                System.out.println("Running grid-search optimization sweep over (K, Alpha)...");
                System.out.printf("%-6s | %-10s | %-10s | %-10s\n", "K", "Alpha", "Micro-F1", "Macro-F1");
                System.out.println("-------|------------|------------|------------");

                int[] kValues = {1, 3, 5};
                double[] alphaValues = {0.0, 0.1, 0.3, 0.5, 0.8, 1.0};

                double bestMicro = -1.0;
                double bestMacro = -1.0;
                String bestParams = "";

                for (int kVal : kValues) {
                    for (double alphaVal : alphaValues) {
                        Evaluator.EvaluationResult res = evaluator.evaluate(
                                testIncidents, historicalIncidents, dbManager, embedder, llmClient, kVal, alphaVal, true
                        );
                        System.out.printf("%-6d | %-10.1f | %-10.3f | %-10.3f\n", kVal, alphaVal, res.microF1, res.macroF1);
                        
                        if (res.macroF1 > bestMacro || (res.macroF1 == bestMacro && res.microF1 > bestMicro)) {
                            bestMicro = res.microF1;
                            bestMacro = res.macroF1;
                            bestParams = "K=" + kVal + ", Alpha=" + alphaVal;
                        }
                    }
                }
                System.out.println("-------------------------------------------------------");
                System.out.println("Optimal Parameter Configuration: " + bestParams);
                System.out.printf("Best F1-Scores: Micro-F1=%.3f, Macro-F1=%.3f\n", bestMicro, bestMacro);
                System.out.println("=======================================================\n");
            } else {
                evaluator.evaluate(testIncidents, historicalIncidents, dbManager, embedder, llmClient, neighborsK, decayAlpha);
            }

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
