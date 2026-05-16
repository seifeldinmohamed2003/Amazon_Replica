package com.team27.amazon.contracts.constants;

/**
 * Durable queue names per §2.9 consumer. DLQ names are {@code queueName + ".dlq"}.
 */
public final class EventQueueNames {
    private EventQueueNames() {}

    // product-service ← order.events
    public static final String PRODUCT_ORDER_PLACED = "product.order-placed";
    public static final String PRODUCT_ORDER_COMPLETED = "product.order-completed";
    public static final String PRODUCT_ORDER_CANCELLED = "product.order-cancelled";

    // user-service ← order.events
    public static final String USER_ORDER_COMPLETED = "user.order-completed";
    public static final String USER_ORDER_CANCELLED = "user.order-cancelled";

    // shipping-service ← order.events
    public static final String SHIPPING_ORDER_PLACED = "shipping.order-placed";
    public static final String SHIPPING_ORDER_COMPLETED = "shipping.order-completed";
    public static final String SHIPPING_ORDER_CANCELLED = "shipping.order-cancelled";

    // billing-service ← order.events
    public static final String BILLING_ORDER_COMPLETED = "billing.order-completed";
    public static final String BILLING_ORDER_CANCELLED = "billing.order-cancelled";

    // order-service ← payment.events
    public static final String ORDER_PAYMENT_INITIATED = "order.payment-initiated";
    public static final String ORDER_PAYMENT_COMPLETED = "order.payment-completed";
    public static final String ORDER_PAYMENT_FAILED = "order.payment-failed";
    public static final String ORDER_PAYMENT_REFUNDED = "order.payment-refunded";

    // order-service ← shipment.events
    public static final String ORDER_SHIPMENT_CREATED = "order.shipment-created";
    public static final String ORDER_SHIPMENT_STATUS_CHANGED = "order.shipment-status-changed";
}
