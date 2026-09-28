package com.jhz.movielens.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "movielens.pipeline")
public class PipelineProperties {
    private String script = "./scripts/run-agent-pipeline.sh";
    private String workingDirectory = ".";
    private String inputVersion = "raw-v1";
    private String rulesVersion = "quality-rules-v1";
    private Duration timeout = Duration.ofMinutes(20);

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public String getInputVersion() {
        return inputVersion;
    }

    public void setInputVersion(String inputVersion) {
        this.inputVersion = inputVersion;
    }

    public String getRulesVersion() {
        return rulesVersion;
    }

    public void setRulesVersion(String rulesVersion) {
        this.rulesVersion = rulesVersion;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
