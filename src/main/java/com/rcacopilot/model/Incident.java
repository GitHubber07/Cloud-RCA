package com.rcacopilot.model;

import java.time.Instant;

public class Incident {
    private String id;
    private Instant timestamp;
    private String alertType;
    private String title;
    private String trueCategory;
    private String predictedCategory;
    private String explanation;

    public Incident() {}

    public Incident(String id, Instant timestamp, String alertType, String title, String trueCategory) {
        this.id = id;
        this.timestamp = timestamp;
        this.alertType = alertType;
        this.title = title;
        this.trueCategory = trueCategory;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getTrueCategory() { return trueCategory; }
    public void setTrueCategory(String trueCategory) { this.trueCategory = trueCategory; }

    public String getPredictedCategory() { return predictedCategory; }
    public void setPredictedCategory(String predictedCategory) { this.predictedCategory = predictedCategory; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    @Override
    public String toString() {
        return "Incident{" +
                "id='" + id + '\'' +
                ", alertType='" + alertType + '\'' +
                ", trueCategory='" + trueCategory + '\'' +
                ", predictedCategory='" + predictedCategory + '\'' +
                '}';
    }
}
