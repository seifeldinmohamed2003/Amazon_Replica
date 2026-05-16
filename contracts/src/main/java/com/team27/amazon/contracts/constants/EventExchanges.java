package com.team27.amazon.contracts.constants;

public final class EventExchanges {
    private EventExchanges() {}

    public static final String USER_EVENTS = "user.events";
    public static final String PRODUCT_EVENTS = "product.events";
    public static final String ORDER_EVENTS = "order.events";
    public static final String SHIPMENT_EVENTS = "shipment.events";
    public static final String PAYMENT_EVENTS = "payment.events";

    public static String deadLetterExchange(String sourceExchange) {
        return sourceExchange + ".dlx";
    }
}
