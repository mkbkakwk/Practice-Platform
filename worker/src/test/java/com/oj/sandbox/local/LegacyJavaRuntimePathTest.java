package com.oj.sandbox.local;

import com.oj.sandbox.SandboxLanguage;
import com.oj.sandbox.SandboxLimits;
import com.oj.sandbox.SandboxRequest;
import com.oj.sandbox.SandboxStatus;
import com.oj.sandbox.SandboxTestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyJavaRuntimePathTest {

    private static final List<String> TRUSTED_SYSTEM_PATHS = List.of(
            "/usr/local/sbin",
            "/usr/local/bin",
            "/usr/sbin",
            "/usr/bin",
            "/sbin",
            "/bin");

    @TempDir
    Path tempDir;

    @Test
    void compilesAndRunsJavaWithTheClearedProcessEnvironment() {
        LegacyLocalSandboxClient client = new LegacyLocalSandboxClient(
                tempDir, 1_048_576, 1_048_576);
        SandboxRequest request = new SandboxRequest(
                UUID.randomUUID().toString(),
                SandboxLanguage.JAVA,
                "public class Main { public static void main(String[] args) { System.out.println(42); } }",
                new SandboxLimits(10_000, 2_000, 256, 1_048_576),
                List.of(new SandboxTestCase("java-runtime", "")));

        var result = client.execute(request);

        assertThat(result.compile().status())
                .withFailMessage("Java compilation failed: %s", result.compile().stderr())
                .isEqualTo(SandboxStatus.OK);
        assertThat(result.cases()).hasSize(1);
        assertThat(result.cases().getFirst().status()).isEqualTo(SandboxStatus.OK);
        assertThat(result.cases().getFirst().stdout()).isEqualTo("42\n");
    }

    @Test
    void trustedEnvironmentUsesJavaHomeWithoutInheritingTheWorkerPath() {
        Map<String, String> environment = LegacyProcessRunner.trustedEnvironment(tempDir);
        String javaHome = Path.of(System.getProperty("java.home"))
                .toAbsolutePath().normalize().toString();
        LinkedHashSet<String> expectedPathEntries = new LinkedHashSet<>();
        expectedPathEntries.add(Path.of(javaHome).resolve("bin").normalize().toString());
        expectedPathEntries.addAll(TRUSTED_SYSTEM_PATHS);

        assertThat(environment).containsEntry("JAVA_HOME", javaHome);
        assertThat(environment.get("PATH"))
                .isEqualTo(String.join(File.pathSeparator, expectedPathEntries))
                .doesNotContain("/tmp/malicious-bin");
        assertThat(environment.keySet())
                .containsExactlyInAnyOrder("JAVA_HOME", "PATH", "HOME", "LANG", "LC_ALL");
    }
}
