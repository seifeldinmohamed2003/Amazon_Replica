package com.team27.amazon.common.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class AbstractEventSubject implements EventSubject {

    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

    @Override
    public void register(EntityObserver observer) {
        if (observer != null) {
            observers.add(observer);
        }
    }

    @Override
    public void unregister(EntityObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers(String eventType, Object payload) {
        for (EntityObserver observer : observers) {
            observer.onEvent(eventType, payload);
        }
    }
}