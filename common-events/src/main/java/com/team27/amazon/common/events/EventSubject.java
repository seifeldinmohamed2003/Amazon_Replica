package com.team27.amazon.common.events;

public interface EventSubject {
    void register(EntityObserver observer);

    void unregister(EntityObserver observer);

    void notifyObservers(String eventType, Object payload);
}