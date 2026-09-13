package com.fairhome.support;

final class EnumNames {

    private EnumNames() {
    }

    static String toName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    static <E extends Enum<E>> E fromName(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(value) || Integer.toString(constant.ordinal()).equals(value)) {
                return constant;
            }
        }
        return Enum.valueOf(type, value);
    }
}
