package com.oj.runner.execution.linux;

import com.oj.runner.language.LanguageProfile;
import com.oj.runner.language.RuntimeMemoryPolicy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LanguageCommandResolver {

    public List<String> compile(LanguageProfile profile, long memoryMb) {
        List<String> command = new ArrayList<>(profile.compileArgv());
        if (profile.memoryPolicy() == RuntimeMemoryPolicy.JAVA) {
            command.add(1, "-J-Xmx" + javaHeapMb(memoryMb) + "m");
            command.add(2, "-J-XX:MaxMetaspaceSize=" + javaMetaspaceMb(memoryMb) + "m");
        } else if (profile.memoryPolicy() == RuntimeMemoryPolicy.NODE) {
            command.add(1, "--max-old-space-size=" + nodeHeapMb(memoryMb));
        }
        return List.copyOf(command);
    }

    public List<String> run(LanguageProfile profile, long memoryMb) {
        List<String> command = new ArrayList<>(profile.runArgv());
        if (profile.memoryPolicy() == RuntimeMemoryPolicy.JAVA) {
            command.add(1, "-Xms16m");
            command.add(2, "-Xmx" + javaHeapMb(memoryMb) + "m");
            command.add(3, "-XX:MaxMetaspaceSize=" + javaMetaspaceMb(memoryMb) + "m");
            command.add(4, "-XX:MaxDirectMemorySize=16m");
            command.add(5, "-Xss256k");
        } else if (profile.memoryPolicy() == RuntimeMemoryPolicy.NODE) {
            command.add(1, "--max-old-space-size=" + nodeHeapMb(memoryMb));
        }
        return List.copyOf(command);
    }

    private long javaHeapMb(long memoryMb) {
        return Math.max(16, memoryMb * 55 / 100);
    }

    private long javaMetaspaceMb(long memoryMb) {
        return Math.max(16, Math.min(96, memoryMb * 15 / 100));
    }

    private long nodeHeapMb(long memoryMb) {
        return Math.max(16, memoryMb * 70 / 100);
    }
}
