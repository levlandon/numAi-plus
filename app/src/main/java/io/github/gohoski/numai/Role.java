package io.github.gohoski.numai;

/**
 * Created by Gleb on 21.08.2025.
 */

enum Role {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    static Role fromValue(String value) {
        if (value == null) {
            return USER;
        }
        for (int i = 0; i < values().length; i++) {
            Role role = values()[i];
            if (role.value.equals(value)) {
                return role;
            }
        }
        return USER;
    }
}
