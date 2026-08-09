package com.oj.sandbox;

import java.util.Arrays;
import java.util.Optional;

public enum SandboxLanguage {
    PYTHON("python"),
    JAVASCRIPT("javascript"),
    C("c"),
    CPP17("cpp"),
    JAVA("java");

    private final String platformId;

    SandboxLanguage(String platformId) {
        this.platformId = platformId;
    }

    public String platformId() {
        return platformId;
    }

    public static Optional<SandboxLanguage> fromPlatformId(String platformId) {
        return Arrays.stream(values())
                .filter(language -> language.platformId.equals(platformId))
                .findFirst();
    }
}
