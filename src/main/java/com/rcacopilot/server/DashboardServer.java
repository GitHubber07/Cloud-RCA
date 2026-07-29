package com.rcacopilot.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rcacopilot.db.DatabaseManager;
import com.rcacopilot.model.Incident;
import com.rcacopilot.model.TelemetryLog;
import com.rcacopilot.generator.MockDataGenerator;
import com.rcacopilot.similarity.FastTextEmbedder;
import com.rcacopilot.evaluator.Evaluator;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class DashboardServer {
    private final int port;
    private final DatabaseManager db;
    private HttpServer server;
    private static final Gson GSON = new com.google.gson.GsonBuilder()
            .registerTypeAdapter(java.time.Instant.class, (com.google.gson.JsonSerializer<java.time.Instant>) 
                (src, typeOfSrc, context) -> new com.google.gson.JsonPrimitive(src.toString()))
            .registerTypeAdapter(java.time.Instant.class, (com.google.gson.JsonDeserializer<java.time.Instant>) 
                (json, typeOfT, context) -> java.time.Instant.parse(json.getAsString()))
            .create();

    public DashboardServer(int port, DatabaseManager db) {
        this.port = port;
        this.db = db;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/incidents", new IncidentsHandler());
        server.createContext("/api/workflows", new WorkflowsHandler());
        server.createContext("/api/simulation/run", new SimulationRunHandler());
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("[DashboardServer] Started web server at http://localhost:" + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("[DashboardServer] Server stopped.");
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String responseText, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Content-Type", contentType);

        byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String pathStr = exchange.getRequestURI().getPath();
            if (pathStr.equals("/")) {
                pathStr = "/index.html";
            }

            pathStr = pathStr.replace("..", ""); // Basic directory traversal protection
            Path filePath = Paths.get("web", pathStr.substring(1));

            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                sendResponse(exchange, 404, "404 Not Found", "text/plain");
                return;
            }

            String contentType = "text/plain";
            if (pathStr.endsWith(".html")) contentType = "text/html";
            else if (pathStr.endsWith(".css")) contentType = "text/css";
            else if (pathStr.endsWith(".js")) contentType = "application/javascript";
            else if (pathStr.endsWith(".json")) contentType = "application/json";
            else if (pathStr.endsWith(".png")) contentType = "image/png";
            else if (pathStr.endsWith(".jpg") || pathStr.endsWith(".jpeg")) contentType = "image/jpeg";

            byte[] fileBytes = Files.readAllBytes(filePath);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, fileBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileBytes);
            }
        }
    }

    private class IncidentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "", "text/plain");
                return;
            }

            String pathStr = exchange.getRequestURI().getPath();
            try {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    if (pathStr.equals("/api/incidents") || pathStr.equals("/api/incidents/")) {
                        List<Incident> incidents = db.getAllIncidents();
                        sendResponse(exchange, 200, GSON.toJson(incidents), "application/json");
                    } else if (pathStr.startsWith("/api/incidents/") && pathStr.endsWith("/logs")) {
                        String[] parts = pathStr.split("/");
                        if (parts.length >= 4) {
                            String id = parts[3];
                            List<TelemetryLog> logs = db.getTelemetryLogs(id);
                            sendResponse(exchange, 200, GSON.toJson(logs), "application/json");
                        } else {
                            sendResponse(exchange, 400, "Bad Request", "text/plain");
                        }
                    } else {
                        sendResponse(exchange, 404, "Not Found", "text/plain");
                    }
                } else {
                    sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Database Error: " + e.getMessage(), "text/plain");
            }
        }
    }

    private class WorkflowsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "", "text/plain");
                return;
            }

            try {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    List<DatabaseManager.WorkflowData> workflows = db.getAllWorkflowHandlers();
                    sendResponse(exchange, 200, GSON.toJson(workflows), "application/json");
                } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr);
                    String body = br.lines().collect(Collectors.joining("\n"));

                    JsonObject root = GSON.fromJson(body, JsonObject.class);
                    String alertType = root.get("alertType").getAsString();
                    String startActionId = root.get("startActionId").getAsString();
                    String actionsJson = root.get("actions").toString();

                    db.saveWorkflowHandler(alertType, startActionId, actionsJson);
                    sendResponse(exchange, 200, "{\"status\":\"success\"}", "application/json");
                } else {
                    sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Server Error: " + e.getMessage(), "text/plain");
            }
        }
    }

    private class SimulationRunHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "", "text/plain");
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                new Thread(() -> {
                    try {
                        System.out.println("[Simulation] Triggering database clear and mock telemetry regeneration...");
                        db.clearDatabase();

                        MockDataGenerator generator = new MockDataGenerator();
                        generator.generateAndInsertTelemetry(db, 30, 5);

                        String ftModelPath = "fasttext_model.vec";
                        File f = new File(ftModelPath);
                        if (!f.exists()) {
                            generator.generateFastTextVecFile(ftModelPath);
                        }

                        FastTextEmbedder embedder = new FastTextEmbedder();
                        embedder.loadModel(ftModelPath);

                        List<Incident> allIncidents = db.getAllIncidents();
                        List<Incident> testIncidents = new java.util.ArrayList<>();
                        List<Incident> historicalIncidents = new java.util.ArrayList<>();

                        for (Incident incident : allIncidents) {
                            if (incident.getId().startsWith("INC-TEST-")) {
                                testIncidents.add(incident);
                            } else if (incident.getId().startsWith("INC-HIST-")) {
                                historicalIncidents.add(incident);
                            }
                        }

                        System.out.println("[Simulation] Starting evaluation loop...");
                        Evaluator evaluator = new Evaluator();
                        evaluator.evaluate(testIncidents, historicalIncidents, db, embedder, new com.rcacopilot.llm.MockLlmClient());
                        System.out.println("[Simulation] Evaluation run complete.");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();

                sendResponse(exchange, 200, "{\"status\":\"started\"}", "application/json");
            } else {
                sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            }
        }
    }
}
