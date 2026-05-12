package com.team27.amazon.contracts.constants;

public final class EventRoutingKeys {
    private EventRoutingKeys() {}

    public static final String USER_REGISTERED = "user.registered";
    public static final String USER_DEACTIVATED = "user.deactivated";

    public static final String PRODUCT_DISCONTINUED = "product.discontinued";
    public static final String PRODUCT_REVIEW_ADDED = "product.review-added";
    public static final String PRODUCT_RATED = "product.rated";

    public static final String ORDER_PLACED = "order.placed";
    public static final String ORDER_COMPLETED = "order.completed";
    public static final String ORDER_CANCELLED = "order.cancelled";

    public static final String SHIPMENT_CREATED = "shipment.created";
    public static final String SHIPMENT_STATUS_CHANGED = "shipment.status-changed";
    public static final String SHIPMENT_CANCELLED = "shipment.cancelled";

    public static final String PAYMENT_INITIATED = "payment.initiated";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_REFUNDED = "payment.refunded";
}
