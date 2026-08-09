package com.oj.runner.language;

import com.oj.runner.api.RunnerLanguage;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class LanguageProfileRegistry {

    private final Map<RunnerLanguage, LanguageProfile> profiles;

    public LanguageProfileRegistry() {
        EnumMap<RunnerLanguage, LanguageProfile> configured = new EnumMap<>(RunnerLanguage.class);
        configured.put(RunnerLanguage.PYTHON,
                new LanguageProfile(RunnerLanguage.PYTHON, "Main.py", "none", "python-3", false));
        configured.put(RunnerLanguage.JAVASCRIPT,
                new LanguageProfile(RunnerLanguage.JAVASCRIPT, "Main.js", "none", "node-22", false));
        configured.put(RunnerLanguage.C,
                new LanguageProfile(RunnerLanguage.C, "Main.c", "gnu-c17", "native", true));
        configured.put(RunnerLanguage.CPP17,
                new LanguageProfile(RunnerLanguage.CPP17, "Main.cpp", "gnu-cpp17", "native", true));
        configured.put(RunnerLanguage.JAVA,
                new LanguageProfile(RunnerLanguage.JAVA, "Main.java", "java-21", "java-21", true));
        profiles = Map.copyOf(configured);
    }

    public LanguageProfile require(RunnerLanguage language) {
        LanguageProfile profile = language == null ? null : profiles.get(language);
        if (profile == null) {
            throw new RunnerRequestValidationException("Runner language is unsupported");
        }
        return profile;
    }

    public Map<RunnerLanguage, LanguageProfile> profiles() {
        return profiles;
    }
}
