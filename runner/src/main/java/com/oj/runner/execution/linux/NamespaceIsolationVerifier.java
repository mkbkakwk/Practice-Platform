package com.oj.runner.execution.linux;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class NamespaceIsolationVerifier {

    public static final List<String> REQUIRED_NAMESPACES = List.of(
            "mnt", "pid", "net", "uts", "ipc", "user", "cgroup", "time");

    private static final Pattern NAMESPACE_LINE = Pattern.compile("^NS\\t([^\\t]+)\\t([^\\t]+)$");
    private static final Pattern PID_LINE = Pattern.compile("^PID\\t([0-9]+)$");
    private static final Pattern NAMESPACE_TARGET = Pattern.compile("^([a-z]+):\\[([0-9]+)]$");

    public Verification verify(String sandboxOutput) {
        return verify(sandboxOutput, namespace -> Files.readSymbolicLink(
                Path.of("/proc/self/ns", namespace)).toString());
    }

    Verification verify(String sandboxOutput, NamespaceLinkReader runnerReader) {
        Set<String> failures = new LinkedHashSet<>();
        Map<String, Long> sandboxNamespaces = new LinkedHashMap<>();
        Integer sandboxPid = null;

        if (sandboxOutput == null) {
            failures.add("namespace-probe-output-invalid");
            sandboxOutput = "";
        }
        for (String line : sandboxOutput.lines().toList()) {
            if (line.isBlank()) {
                continue;
            }
            Matcher namespaceLine = NAMESPACE_LINE.matcher(line);
            if (namespaceLine.matches()) {
                String namespace = namespaceLine.group(1);
                if (!REQUIRED_NAMESPACES.contains(namespace) || sandboxNamespaces.containsKey(namespace)) {
                    failures.add("namespace-probe-output-invalid");
                    continue;
                }
                Long inode = parseTarget(namespace, namespaceLine.group(2));
                if (inode == null) {
                    failures.add("namespace-" + namespace + "-sandbox-unavailable");
                } else {
                    sandboxNamespaces.put(namespace, inode);
                }
                continue;
            }
            Matcher pidLine = PID_LINE.matcher(line);
            if (pidLine.matches() && sandboxPid == null) {
                try {
                    sandboxPid = Integer.parseInt(pidLine.group(1));
                } catch (NumberFormatException exception) {
                    failures.add("namespace-pid-not-init");
                }
                continue;
            }
            failures.add("namespace-probe-output-invalid");
        }

        Map<String, Long> runnerNamespaces = new LinkedHashMap<>();
        for (String namespace : REQUIRED_NAMESPACES) {
            Long sandboxInode = sandboxNamespaces.get(namespace);
            if (sandboxInode == null) {
                failures.add("namespace-" + namespace + "-sandbox-unavailable");
            }

            Long runnerInode = null;
            try {
                runnerInode = parseTarget(namespace, runnerReader.read(namespace));
            } catch (IOException | RuntimeException exception) {
                // A missing, unreadable, or malformed Runner namespace is a failed security check.
            }
            if (runnerInode == null) {
                failures.add("namespace-" + namespace + "-runner-unavailable");
            } else {
                runnerNamespaces.put(namespace, runnerInode);
            }
            if (sandboxInode != null && runnerInode != null && sandboxInode.equals(runnerInode)) {
                failures.add("namespace-" + namespace + "-not-isolated");
            }
        }
        if (sandboxPid == null || sandboxPid != 1) {
            failures.add("namespace-pid-not-init");
        }

        return new Verification(
                List.copyOf(failures), Map.copyOf(runnerNamespaces),
                Map.copyOf(sandboxNamespaces), sandboxPid);
    }

    public static String pythonProbeSource() {
        String names = REQUIRED_NAMESPACES.stream()
                .map(name -> "'" + name + "'")
                .collect(Collectors.joining(", "));
        return "import os\n"
                + "for name in (" + names + ",):\n"
                + " print('NS\\t' + name + '\\t' + os.readlink('/proc/self/ns/' + name))\n"
                + "print('PID\\t' + str(os.getpid()))\n";
    }

    private Long parseTarget(String expectedNamespace, String target) {
        if (target == null) {
            return null;
        }
        Matcher matcher = NAMESPACE_TARGET.matcher(target);
        if (!matcher.matches() || !expectedNamespace.equals(matcher.group(1))) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(2));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @FunctionalInterface
    interface NamespaceLinkReader {
        String read(String namespace) throws IOException;
    }

    public record Verification(
            List<String> failures,
            Map<String, Long> runnerNamespaces,
            Map<String, Long> sandboxNamespaces,
            Integer sandboxPid) {
    }
}
