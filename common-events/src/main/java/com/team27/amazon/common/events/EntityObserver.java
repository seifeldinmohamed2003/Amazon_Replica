package com.team27.amazon.common.events;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}