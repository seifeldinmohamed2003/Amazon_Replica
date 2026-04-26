package com.team27.amazon.billing.observer;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}
