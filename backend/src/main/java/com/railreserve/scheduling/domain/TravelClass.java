package com.railreserve.scheduling.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Travel classes use short external codes (SL, 3A, 2A, ...) that are not valid Java
 * identifiers, so the enum carries a {@code code} and maps to/from it at every boundary:
 * <ul>
 *   <li>JPA persistence &rarr; {@link TravelClassConverter}</li>
 *   <li>JSON in/out &rarr; {@link JsonValue} / {@link JsonCreator}</li>
 *   <li>HTTP query binding &rarr; a {@code Converter<String, TravelClass>} registered in web config</li>
 * </ul>
 */
public enum TravelClass {
    SLEEPER("SL"),
    AC_3_TIER("3A"),
    AC_2_TIER("2A"),
    AC_1_TIER("1A"),
    CHAIR_CAR("CC"),
    SECOND_SITTING("2S"),
    EXECUTIVE_CHAIR("EC");

    private final String code;

    TravelClass(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static TravelClass fromCode(String code) {
        return Arrays.stream(values())
                .filter(tc -> tc.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown travel class: " + code));
    }
}
