package com.team27.amazon.order.dto;

import java.util.List;

public class CoPurchaseRecordResponse {

    private Long orderId;
    private List<Long> productIds;
    private Integer pairsRecorded;
    private Boolean alreadyRecorded;
    private String message;

    public CoPurchaseRecordResponse() {
    }

    public CoPurchaseRecordResponse(Long orderId, List<Long> productIds,
                                    Integer pairsRecorded, Boolean alreadyRecorded,
                                    String message) {
        this.orderId = orderId;
        this.productIds = productIds;
        this.pairsRecorded = pairsRecorded;
        this.alreadyRecorded = alreadyRecorded;
        this.message = message;
    }

    public Long getOrderId() {
        return orderId;
    }

    public List<Long> getProductIds() {
        return productIds;
    }

    public Integer getPairsRecorded() {
        return pairsRecorded;
    }

    public Boolean getAlreadyRecorded() {
        return alreadyRecorded;
    }

    public String getMessage() {
        return message;
    }
}