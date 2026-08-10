package com.oj.runner;

import com.oj.runner.api.RunnerLanguage;
import com.oj.runner.language.LanguageProfileRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageProfileRegistryTest {

    @Test
    void providesOnlyTheClosedProtocolLanguageSet() {
        LanguageProfileRegistry registry = new LanguageProfileRegistry();

        assertThat(registry.profiles().keySet())
                .containsExactlyInAnyOrder(RunnerLanguage.values());
        assertThat(registry.require(RunnerLanguage.PYTHON).sourceFilename()).isEqualTo("Main.py");
        assertThat(registry.require(RunnerLanguage.JAVASCRIPT).runtimeProfile()).isEqualTo("node-22");
        assertThat(registry.require(RunnerLanguage.C).compileProfile()).isEqualTo("gnu-c17");
        assertThat(registry.require(RunnerLanguage.CPP17).compileProfile()).isEqualTo("gnu-cpp17");
        assertThat(registry.require(RunnerLanguage.JAVA).sourceFilename()).isEqualTo("Main.java");
    }
}
