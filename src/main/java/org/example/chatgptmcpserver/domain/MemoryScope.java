package org.example.chatgptmcpserver.domain;

import java.util.Arrays;

public enum MemoryScope {
    PERSONAL("personal"),
    FAMILY("family"),
    CUSTODY("custody"),
    CHILD_SUPPORT("child_support"),
    ASHLEY("ashley"),
    RELATIONSHIPS("relationships"),
    LEGAL("legal"),
    WORK("work"),
    EDUCATION("education"),
    HOME("home"),
    GENERAL("general"),
    FIDELITY("fidelity"),
    SOFTWARE_ENGINEERING("software_engineering"),
    TRAVEL("travel"),
    VEHICLES("vehicles"),
    FINANCE("finance"),
    HEALTH("health"),
    SPIRITUALITY("spirituality"),
    MEDIUM_SESSIONS("medium_sessions"),
    PSYCHIC_SESSIONS("psychic_sessions"),
    APPLE("apple"),
    CREATIVE("creative"),
    ALL("all"),
    OFF("off");

    private final String value;

    MemoryScope(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static MemoryScope fromValue(String rawScope) {
        if (rawScope == null || rawScope.isBlank()) {
            throw new IllegalArgumentException("Scope cannot be blank");
        }

        return Arrays.stream(values())
                .filter(scope -> scope.value.equalsIgnoreCase(rawScope.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported scope: " + rawScope));
    }
}

