package com.team27.amazon.user.adapter;

import java.util.Optional;

public interface ActivityCacheAdapter {
    Optional<String> get(String key);
    void set(String key, String value, long ttlMinutes);
}