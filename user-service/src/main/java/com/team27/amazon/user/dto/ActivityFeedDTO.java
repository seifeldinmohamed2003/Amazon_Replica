package com.team27.amazon.user.dto;


import java.util.List;
import java.util.Map;

public class ActivityFeedDTO {

    private final List<ActivityEventDTO> content;
    private final int page;
    private final int size;
    private final long totalElements;

    private ActivityFeedDTO(Builder builder) {
        this.content = builder.content;
        this.page = builder.page;
        this.size = builder.size;
        this.totalElements = builder.totalElements;
    }

    public List<ActivityEventDTO> getContent() { return content; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }

    public static class Builder {
        private List<ActivityEventDTO> content;
        private int page;
        private int size;
        private long totalElements;

        public Builder content(List<ActivityEventDTO> content) {
            this.content = content;
            return this;
        }
        public Builder page(int page) {
            this.page = page;
            return this;
        }
        public Builder size(int size) {
            this.size = size;
            return this;
        }
        public Builder totalElements(long totalElements) {
            this.totalElements = totalElements;
            return this;
        }
        public ActivityFeedDTO build() {
            return new ActivityFeedDTO(this);
        }
    }

    public static class ActivityEventDTO {
        private final String action;
        private final String timestamp;
        private final Map<String, Object> details;

        private ActivityEventDTO(Builder builder) {
            this.action = builder.action;
            this.timestamp = builder.timestamp;
            this.details = builder.details;
        }

        public String getAction() { return action; }
        public String getTimestamp() { return timestamp; }
        public Map<String, Object> getDetails() { return details; }

        public static class Builder {
            private String action;
            private String timestamp;
            private Map<String, Object> details;

            public Builder action(String action) {
                this.action = action;
                return this;
            }
            public Builder timestamp(String timestamp) {
                this.timestamp = timestamp;
                return this;
            }
            public Builder details(Map<String, Object> details) {
                this.details = details;
                return this;
            }
            public ActivityEventDTO build() {
                return new ActivityEventDTO(this);
            }
        }
    }
}