package com.jhz.movielens.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "movielens.ml")
public class MlProperties {
    private String cleanVersion = "clean-agent-20260929-124232-bfc440f2";
    private String python = ".venv-ml/bin/python";
    private Duration timeout = Duration.ofMinutes(45);

    public String getCleanVersion() { return cleanVersion; }
    public void setCleanVersion(String cleanVersion) { this.cleanVersion = cleanVersion; }
    public String getPython() { return python; }
    public void setPython(String python) { this.python = python; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
}
