package com.team27.amazon.common.events;

import java.time.LocalDateTime;
import java.util.Map;

public interface MongoEvent {
    String getId();

    LocalDateTime getTimestamp();

    String getAction();

    Map<String, Object> getDetails();
}
