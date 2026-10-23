package com.taskpilot.dto;

import java.util.List;

public class RecoveryResponse {

    private String explanation;
    private List<String> recommendedTasks;
    private List<String> deferredTasks;
    private List<String> assumptions;

    public RecoveryResponse() {}

    public RecoveryResponse(String explanation, List<String> recommendedTasks,
                           List<String> deferredTasks, List<String> assumptions) {
        this.explanation = explanation;
        this.recommendedTasks = recommendedTasks;
        this.deferredTasks = deferredTasks;
        this.assumptions = assumptions;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public List<String> getRecommendedTasks() {
        return recommendedTasks;
    }

    public void setRecommendedTasks(List<String> recommendedTasks) {
        this.recommendedTasks = recommendedTasks;
    }

    public List<String> getDeferredTasks() {
        return deferredTasks;
    }

    public void setDeferredTasks(List<String> deferredTasks) {
        this.deferredTasks = deferredTasks;
    }

    public List<String> getAssumptions() {
        return assumptions;
    }

    public void setAssumptions(List<String> assumptions) {
        this.assumptions = assumptions;
    }
}
