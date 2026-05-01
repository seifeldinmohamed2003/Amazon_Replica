package com.team27.amazon.user.cache;

import java.time.Duration;
import java.util.List;

public final class CacheConstants {

    private CacheConstants() {}

    public static final String SERVICE = "user-service";

    public static final String ENTITY_USER = "user";
    public static final String ENTITY_SHIPPING_ADDRESS = "shipping-address";

    public static final String S1_F1 = "S1-F1";
    public static final String S1_F3 = "S1-F3";
    public static final String S1_F5 = "S1-F5";
    public static final String S1_F6 = "S1-F6";
    public static final String S1_F8 = "S1-F8";
    public static final String S1_F9 = "S1-F9";

    public static final Duration TTL_F1_SEARCH = Duration.ofMinutes(5);
    public static final Duration TTL_F3_DTO = Duration.ofMinutes(10);
    public static final Duration TTL_F5_JSONB = Duration.ofMinutes(5);
    public static final Duration TTL_F6_REPORT = Duration.ofMinutes(10);
    public static final Duration TTL_F8_RELATIONSHIP = Duration.ofMinutes(15);
    public static final Duration TTL_F9_COMBINED = Duration.ofMinutes(10);
    public static final Duration TTL_ENTITY_DETAIL = Duration.ofMinutes(15);

    public static final List<String> USER_FEATURE_CACHE_PATTERNS = List.of(
            SERVICE + "::" + S1_F1 + "::*",
            SERVICE + "::" + S1_F3 + "::*",
            SERVICE + "::" + S1_F5 + "::*",
            SERVICE + "::" + S1_F6 + "::*",
            SERVICE + "::" + S1_F8 + "::*",
            SERVICE + "::" + S1_F9 + "::*"
    );
}