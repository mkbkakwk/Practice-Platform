package com.oj.runner.execution.linux;

import com.oj.runner.api.RunnerCaseRequest;
import com.oj.runner.api.RunnerJobRequest;
import com.oj.runner.api.RunnerJobResponse;
import com.oj.runner.api.RunnerLanguage;
import com.oj.runner.api.RunnerLimitsRequest;
import com.oj.runner.api.RunnerStatus;
import com.oj.runner.config.LinuxSandboxProperties;
import com.oj.runner.service.RunnerJobService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Linux-only adversarial acceptance suite. It is intentionally selected only by
 * the linux-security Maven profile after runner-linux-preflight.sh succeeds.
 */
@SpringBootTest(properties = {
        "runner.token=linux-acceptance-token",
        "runner.sandbox.mode=linux"
})
class LinuxSandboxSecurityIT {

    @Autowired
    RunnerJobService jobs;

    @Autowired
    LinuxSandboxProperties properties;

    @Autowired
    NamespaceIsolationVerifier namespaceVerifier;

    @BeforeEach
    void requireRealSandbox() {
        assertThat(jobs.sandboxAvailable())
                .as("Linux acceptance must never run against the unavailable executor")
                .isTrue();
        assertCleanHostState();
    }

    @AfterEach
    void verifyNoExecutionResidue() {
        assertCleanHostState();
        assertThat(normal(RunnerLanguage.PYTHON, "print('healthy')").cases().getFirst().stdout())
                .isEqualTo("healthy\n");
        assertCleanHostState();
    }

    @Test
    void fiveLanguageProfilesCompileOnceAndRunNormally() {
        assertOk(RunnerLanguage.PYTHON, "print(input().strip())", "python\n", "python\n");
        assertOk(RunnerLanguage.JAVASCRIPT,
                "process.stdin.once('data', d => process.stdout.write(d));", "javascript\n", "javascript\n");
        assertOk(RunnerLanguage.C,
                "#include <stdio.h>\nint main(){char s[32];fgets(s,sizeof s,stdin);printf(\"%s\",s);}",
                "c\n", "c\n");
        assertOk(RunnerLanguage.CPP17,
                "#include <iostream>\n#include <string>\nint main(){std::string s;std::getline(std::cin,s);std::cout<<s<<'\\n';}",
                "cpp\n", "cpp\n");
        assertOk(RunnerLanguage.JAVA,
                "public class Main{public static void main(String[]a)throws Exception{System.out.print(new String(System.in.readAllBytes()));}}",
                "java\n", "java\n");
    }

    @Test
    void compiledLanguagesReturnCompileErrors() {
        for (RunnerLanguage language : List.of(RunnerLanguage.C, RunnerLanguage.CPP17, RunnerLanguage.JAVA)) {
            RunnerJobResponse response = execute(language, "this is not valid source", "", 1000, 256, 4096);
            assertThat(response.compile().status()).isEqualTo(RunnerStatus.COMPILE_ERROR);
            assertThat(response.cases()).isEmpty();
        }
    }

    @Test
    void fiveLanguagesReturnRuntimeErrorsWithoutHarmingRunner() {
        assertRuntimeError(RunnerLanguage.PYTHON, "raise RuntimeError('expected')");
        assertRuntimeError(RunnerLanguage.JAVASCRIPT, "throw new Error('expected');");
        assertRuntimeError(RunnerLanguage.C, "int main(){return 7;}");
        assertRuntimeError(RunnerLanguage.CPP17, "int main(){return 7;}");
        assertRuntimeError(RunnerLanguage.JAVA,
                "public class Main{public static void main(String[]a){throw new RuntimeException();}}");
    }

    @Test
    void fiveLanguagesHitWallTimeLimitAndWholeTreeIsRemoved() {
        assertTimedOut(RunnerLanguage.PYTHON, "while True: pass");
        assertTimedOut(RunnerLanguage.JAVASCRIPT, "while (true) {};");
        assertTimedOut(RunnerLanguage.C, "int main(){for(;;){}} ");
        assertTimedOut(RunnerLanguage.CPP17, "int main(){for(;;){}} ");
        assertTimedOut(RunnerLanguage.JAVA,
                "public class Main{public static void main(String[]a){for(;;){}}}");
    }

    @Test
    void memoryGrowthIsKilledByPerExecutionCgroup() {
        RunnerJobResponse response = execute(
                RunnerLanguage.PYTHON,
                "x=[]\nwhile True: x.append(bytearray(1024*1024))",
                "", 5000, 64, 4096);
        assertThat(response.cases().getFirst().status()).isEqualTo(RunnerStatus.MEMORY_LIMIT_EXCEEDED);
    }

    @Test
    void unlimitedStdoutAndStderrAreKilledWhileCaptureStaysBounded() {
        RunnerJobResponse stdout = execute(
                RunnerLanguage.PYTHON,
                "import os\nwhile True: os.write(1,b'x'*8192)", "", 3000, 128, 32768);
        assertThat(stdout.cases().getFirst().status()).isEqualTo(RunnerStatus.OUTPUT_LIMIT_EXCEEDED);
        assertThat(stdout.cases().getFirst().stdout().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .hasSizeLessThanOrEqualTo(32768);

        RunnerJobResponse stderr = execute(
                RunnerLanguage.PYTHON,
                "import os\nwhile True: os.write(2,b'x'*8192)", "", 3000, 128, 32768);
        assertThat(stderr.cases().getFirst().status()).isEqualTo(RunnerStatus.OUTPUT_LIMIT_EXCEEDED);
        assertThat(stderr.cases().getFirst().stderr().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .hasSizeLessThanOrEqualTo(32768);
    }

    @Test
    void forkBombCannotEscapePidsLimit() {
        RunnerJobResponse response = execute(
                RunnerLanguage.C,
                "#include <unistd.h>\nint main(){for(;;){if(fork()<0) pause();}}",
                "", 1000, 128, 4096);
        assertThat(response.cases().getFirst().status()).isEqualTo(RunnerStatus.TIME_LIMIT_EXCEEDED);
    }

    @Test
    void backgroundChildIsRemovedWhenMainProgramExits() {
        assertOk(RunnerLanguage.PYTHON,
                "import os,time\nif os.fork()==0:\n while True: time.sleep(1)\nelse: print('parent-exit')",
                "", "parent-exit\n");
    }

    @Test
    void sandboxIdentityNamespacesAndCapabilitiesAreRestricted() {
        RunnerJobResponse response = normal(RunnerLanguage.PYTHON, """
                import os,socket
                caps='?'
                for line in open('/proc/self/status'):
                    if line.startswith('CapEff:'): caps=line.split()[1]
                print(os.geteuid(),os.getegid(),os.getpid(),socket.gethostname(),caps)
                """);
        String[] values = response.cases().getFirst().stdout().trim().split(" ");
        assertThat(values[0]).isEqualTo(Integer.toString(properties.getStudentUid()));
        assertThat(values[1]).isEqualTo(Integer.toString(properties.getStudentGid()));
        assertThat(values[2]).isEqualTo("1");
        assertThat(values[3]).isEqualTo("student-sandbox");
        assertThat(values[4]).matches("0+");
    }

    @Test
    void allRequiredNamespacesAreActuallyIsolatedAndStudentIsPidOne() {
        RunnerJobResponse response = normal(
                RunnerLanguage.PYTHON, NamespaceIsolationVerifier.pythonProbeSource());
        NamespaceIsolationVerifier.Verification verification = namespaceVerifier.verify(
                response.cases().getFirst().stdout());

        assertThat(verification.failures()).isEmpty();
        assertThat(verification.sandboxPid()).isEqualTo(1);
        for (String namespace : NamespaceIsolationVerifier.REQUIRED_NAMESPACES) {
            assertThat(verification.sandboxNamespaces())
                    .as("sandbox namespace inode for %s", namespace)
                    .containsKey(namespace);
            assertThat(verification.runnerNamespaces())
                    .as("Runner namespace inode for %s", namespace)
                    .containsKey(namespace);
            assertThat(verification.sandboxNamespaces().get(namespace))
                    .as("sandbox %s namespace must differ from Runner JVM", namespace)
                    .isNotEqualTo(verification.runnerNamespaces().get(namespace));
        }
    }

    @Test
    void hostSecretsAndRunnerEnvironmentAreNotVisible() {
        RunnerJobResponse response = normal(RunnerLanguage.PYTHON, """
                import os
                assert 'RUNNER_TOKEN' not in os.environ
                assert 'DATABASE_URL' not in os.environ
                assert 'JWT_SECRET' not in os.environ
                assert set(os.environ).issubset({'PATH','LANG','LC_ALL','HOME','JAVA_HOME'})
                try:
                    open('/etc/shadow').read()
                    print('LEAK')
                except Exception:
                    print('BLOCKED')
                """);
        assertThat(response.cases().getFirst().stdout()).isEqualTo("BLOCKED\n");
    }

    @Test
    void procDoesNotExposeRunnerJvmOrHostProcesses() {
        RunnerJobResponse response = normal(RunnerLanguage.PYTHON, """
                data=open('/proc/1/cmdline','rb').read().decode('utf-8','replace')
                assert 'oj-sandbox-runner' not in data
                assert 'java -jar' not in data
                print('ISOLATED')
                """);
        assertThat(response.cases().getFirst().stdout()).isEqualTo("ISOLATED\n");
    }

    @Test
    void networkDnsAndLocalhostAreUnavailable() {
        RunnerJobResponse response = normal(RunnerLanguage.PYTHON, """
                import socket
                blocked=0
                for action in [lambda: socket.socket(), lambda: socket.getaddrinfo('example.com',80)]:
                    try: action()
                    except OSError: blocked+=1
                print('BLOCKED' if blocked==2 else 'LEAK')
                """);
        assertThat(response.cases().getFirst().stdout()).isEqualTo("BLOCKED\n");
    }

    @Test
    void mountPtraceAndRawSocketSyscallsAreDenied() {
        String source = """
                #include <errno.h>
                #include <stdio.h>
                #include <sys/mount.h>
                #include <sys/ptrace.h>
                #include <sys/socket.h>
                #include <netinet/in.h>
                int main(){
                  int denied=0;
                  if(mount("none","/tmp","tmpfs",0,0)==-1 && errno==EPERM) denied++;
                  errno=0; if(ptrace(PTRACE_TRACEME,0,0,0)==-1 && errno==EPERM) denied++;
                  errno=0; if(socket(AF_INET,SOCK_RAW,IPPROTO_RAW)==-1 && errno==EPERM) denied++;
                  printf(denied==3?"BLOCKED\\n":"LEAK\\n");
                  return denied==3?0:2;
                }
                """;
        assertOk(RunnerLanguage.C, source, "", "BLOCKED\n");
    }

    @Test
    void runtimeRootIsReadOnlyAndTmpfsIsBounded() {
        RunnerJobResponse root = normal(RunnerLanguage.PYTHON, """
                try:
                    open('/host-write','w').write('x')
                    print('LEAK')
                except Exception:
                    print('BLOCKED')
                """);
        assertThat(root.cases().getFirst().stdout()).isEqualTo("BLOCKED\n");

        RunnerJobResponse temporary = execute(RunnerLanguage.PYTHON, """
                try:
                    with open('/tmp/fill','wb') as f:
                        while True: f.write(b'x'*1048576)
                except OSError:
                    print('CAPPED')
                """, "", 5000, 128, 4096);
        assertThat(temporary.cases().getFirst().stdout()).isEqualTo("CAPPED\n");
    }

    private RunnerJobResponse normal(RunnerLanguage language, String source) {
        return execute(language, source, "", 2000, 256, 65536);
    }

    private void assertOk(RunnerLanguage language, String source, String stdin, String stdout) {
        RunnerJobResponse response = execute(language, source, stdin, 2000, 256, 65536);
        assertThat(response.compile().status()).isEqualTo(RunnerStatus.OK);
        assertThat(response.cases().getFirst().status()).isEqualTo(RunnerStatus.OK);
        assertThat(response.cases().getFirst().stdout()).isEqualTo(stdout);
    }

    private void assertRuntimeError(RunnerLanguage language, String source) {
        RunnerJobResponse response = execute(language, source, "", 2000, 256, 65536);
        assertThat(response.compile().status()).isEqualTo(RunnerStatus.OK);
        assertThat(response.cases().getFirst().status()).isEqualTo(RunnerStatus.RUNTIME_ERROR);
    }

    private void assertTimedOut(RunnerLanguage language, String source) {
        RunnerJobResponse response = execute(language, source, "", 500, 256, 4096);
        assertThat(response.compile().status()).isEqualTo(RunnerStatus.OK);
        assertThat(response.cases().getFirst().status()).isEqualTo(RunnerStatus.TIME_LIMIT_EXCEEDED);
    }

    private RunnerJobResponse execute(
            RunnerLanguage language,
            String source,
            String stdin,
            long runTimeMs,
            long memoryMb,
            int outputLimit) {
        RunnerJobRequest request = new RunnerJobRequest(
                UUID.randomUUID().toString(),
                language,
                source,
                new RunnerLimitsRequest(10_000, runTimeMs, memoryMb, outputLimit),
                List.of(new RunnerCaseRequest("case-1", stdin)));
        return jobs.execute(request);
    }

    private void assertCleanHostState() {
        Path workspace = Path.of(properties.getWorkspaceRoot());
        Path cgroup = Path.of(properties.getCgroupV2Mount());
        try (var jobs = Files.list(workspace); var cgroups = Files.list(cgroup)) {
            assertThat(jobs.filter(path -> path.getFileName().toString().startsWith("job-"))).isEmpty();
            assertThat(cgroups.filter(path -> path.getFileName().toString().startsWith("NSJAIL."))).isEmpty();
        } catch (IOException exception) {
            throw new AssertionError("Unable to verify sandbox cleanup", exception);
        }
        assertThat(studentProcesses()).isZero();
    }

    private long studentProcesses() {
        try (var entries = Files.list(Path.of("/proc"))) {
            return entries.filter(path -> path.getFileName().toString().chars().allMatch(Character::isDigit))
                    .filter(path -> {
                        try {
                            String command = Files.readString(path.resolve("cmdline")).toLowerCase(Locale.ROOT);
                            return command.contains("/workspace/main") || command.contains("student-sandbox");
                        } catch (IOException exception) {
                            return false;
                        }
                    }).count();
        } catch (IOException exception) {
            throw new AssertionError("Unable to inspect process cleanup", exception);
        }
    }
}
