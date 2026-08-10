package com.oj.runner.execution.linux;

import com.oj.runner.api.RunnerLanguage;
import com.oj.runner.language.LanguageProfileRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageCommandResolverTest {

    private final LanguageProfileRegistry profiles = new LanguageProfileRegistry();
    private final LanguageCommandResolver resolver = new LanguageCommandResolver();

    @Test
    void allCommandsComeFromClosedProfilesWithoutAShell() {
        for (RunnerLanguage language : RunnerLanguage.values()) {
            var profile = profiles.require(language);
            for (List<String> argv : List.of(
                    resolver.compile(profile, 256), resolver.run(profile, 256))) {
                assertThat(argv).isNotEmpty();
                assertThat(argv.getFirst()).startsWith("/");
                assertThat(argv).doesNotContain("bash", "sh", "-c");
                assertThat(String.join(" ", argv)).doesNotContain("${", "`", "requestId");
            }
        }
    }

    @Test
    void javaMemoryFlagsStayInsideRequestedCgroupMemory() {
        var java = profiles.require(RunnerLanguage.JAVA);
        assertThat(resolver.run(java, 256)).contains(
                "-Xms16m", "-Xmx140m", "-XX:MaxMetaspaceSize=38m",
                "-XX:MaxDirectMemorySize=16m", "-Xss256k");
        assertThat(resolver.compile(java, 256)).contains(
                "-J-Xmx140m", "-J-XX:MaxMetaspaceSize=38m");
    }
}
