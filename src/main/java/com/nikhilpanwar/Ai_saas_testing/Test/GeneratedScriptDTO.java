package com.nikhilpanwar.Ai_saas_testing.Test;

import java.util.Map;

public class GeneratedScriptDTO {
    private boolean success;
    private String test_script;
    private String error;
    private String url;
    private String generated_at;
    private String model;
    private Map<String, Object> page_info;
    private Map<String, Object> results; // for execute-tests response
    private String duration;
    private String status;
    private String browser;
    private String id;
    private String[] logs;
    private Object[] bugs;
    private Object[] recommendations;
    private Object[] screenshots;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getTest_script() { return test_script; }
    public void setTest_script(String test_script) { this.test_script = test_script; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getGenerated_at() { return generated_at; }
    public void setGenerated_at(String generated_at) { this.generated_at = generated_at; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Map<String, Object> getPage_info() { return page_info; }
    public void setPage_info(Map<String, Object> page_info) { this.page_info = page_info; }

    public Map<String, Object> getResults() { return results; }
    public void setResults(Map<String, Object> results) { this.results = results; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBrowser() { return browser; }
    public void setBrowser(String browser) { this.browser = browser; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String[] getLogs() { return logs; }
    public void setLogs(String[] logs) { this.logs = logs; }

    public Object[] getBugs() { return bugs; }
    public void setBugs(Object[] bugs) { this.bugs = bugs; }

    public Object[] getRecommendations() { return recommendations; }
    public void setRecommendations(Object[] recommendations) { this.recommendations = recommendations; }

    public Object[] getScreenshots() { return screenshots; }
    public void setScreenshots(Object[] screenshots) { this.screenshots = screenshots; }
}
