package com.oj.runner.config;

import com.oj.runner.api.RunnerLanguage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DockerSandboxPropertiesTest {

    @Test
    void mapsOnlyWhitelistedLanguagesToProjectMaintainedImages() {
        DockerSandboxProperties properties = new DockerSandboxProperties();
        assertThat(properties.getPidsLimit()).isEqualTo(64);
        assertThat(properties.imageFor(RunnerLanguage.PYTHON)).isEqualTo("practice-sandbox-python:local");
        assertThat(properties.imageFor(RunnerLanguage.JAVASCRIPT)).isEqualTo("practice-sandbox-javascript:local");
        assertThat(properties.imageFor(RunnerLanguage.C)).isEqualTo("practice-sandbox-c:local");
        assertThat(properties.imageFor(RunnerLanguage.CPP17)).isEqualTo("practice-sandbox-cpp17:local");
        assertThat(properties.imageFor(RunnerLanguage.JAVA)).isEqualTo("practice-sandbox-java:local");
    }
}
