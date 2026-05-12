# M3 Endpoint Contracts

This file documents shared endpoint paths, params, return DTOs, and response JSON shapes used by the contracts module.

## User Service

### GET /api/users/{id}

Path params:
- id: user id

Returns: UserDTO

Response JSON:

{
  "id": 1,
  "name": "Omar Ashraf",
  "email": "omar@example.com",
  "phone": "+201000000000",
  "role": "CUSTOMER",
  "status": "ACTIVE",
  "preferences": {}
}

### GET /api/users/{userId}/addresses/{addressId}

Path params:
- userId: user id
- addressId: shipping address id

Returns: ShippingAddressDTO

Response JSON:

{
  "id": 10,
  "userId": 1,
  "addressLine": "Street 1",
  "city": "Cairo",
  "governorate": "Cairo",
  "postalCode": "11835",
  "country": "Egypt"
}

## Product Service

### GET /api/products/{id}

Path params:
- id: product id

Returns: ProductDTO

Response JSON:

{
  "id": 100,
  "name": "Wireless Mouse",
  "description": "Ergonomic wireless mouse",
  "price": 499.99,
  "category": "Electronics",
  "brand": "Amazon Basics",
  "stockQuantity": 25,
  "status": "ACTIVE",
  "rating": 4.5,
  "specifications": {}
}

### GET /api/products/{id}/exists

Path params:
- id: product id

Returns: ProductExistsDTO

Response JSON:

{
  "exists": true
}

### GET /api/products/batch?ids=1,2,3

Query params:
- ids: comma-separated product ids

Returns: List<ProductDTO>

Response JSON:

[
  {
    "id": 100,
    "name": "Wireless Mouse",
    "description": "Ergonomic wireless mouse",
    "price": 499.99,
    "category": "Electronics",
    "brand": "Amazon Basics",
    "stockQuantity": 25,
    "status": "ACTIVE",
    "rating": 4.5,
    "specifications": {}
  }
]

## Order Service

### GET /api/orders/{orderId}

Path params:
- orderId: order id

Returns: OrderDTO

Response JSON:

{
  "id": 500,
  "userId": 1,
  "shippingAddressId": 10,
  "status": "DELIVERED",
  "totalAmount": 999.98,
  "orderedAt": "2026-05-12T20:30:00",
  "deliveredAt": "2026-05-15T12:00:00",
  "metadata": {}
}

### GET /api/orders/{orderId}/items

Path params:
- orderId: order id

Returns: List<OrderItemDTO>

Response JSON:

[
  {
    "id": 1,
    "orderId": 500,
    "productId": 100,
    "quantity": 2,
    "priceAtPurchase": 499.99,
    "itemOrder": 1,
    "metadata": {}
  }
]

### GET /api/orders/user/{userId}/summary

Path params:
- userId: user id

Returns: OrderSummaryDTO

Response JSON:

{
  "totalOrders": 12,
  "completedOrders": 10,
  "cancelledOrders": 2,
  "totalSpent": 15000.0,
  "averageOrderValue": 1250.0
}

### GET /api/orders/user/{userId}/active-count

Returns: int

Response JSON:

3

### GET /api/orders/user/{userId}/count

Returns: long

Response JSON:

12

### GET /api/orders/product/{productId}/sales?startDate={date}&endDate={date}

Returns: ProductSalesAggregateDTO

Response JSON:

{
  "totalUnitsSold": 40,
  "totalRevenue": 19999.6,
  "averageSellingPrice": 499.99
}

### GET /api/orders/product/{productId}/pending-count

Returns: int

Response JSON:

5

### GET /api/orders/product/{productId}/units-sold

Returns: long

Response JSON:

40

### GET /api/orders/product/{productId}/recent-sales-count?days={n}

Returns: int

Response JSON:

7

### GET /api/orders/user/{userId}/has-purchased/{productId}

Returns: boolean

Response JSON:

true

## Shipping Service

### GET /api/shipments/order/{orderId}/active

Path params:
- orderId: order id

Returns: ShipmentDTO

Response JSON:

{
  "id": 700,
  "orderId": 500,
  "carrier": "DHL",
  "trackingNumber": "DHL-123456",
  "status": "IN_TRANSIT",
  "estimatedDelivery": "2026-05-15",
  "actualDelivery": null,
  "lastUpdate": "2026-05-13T10:00:00",
  "latitude": 30.0444,
  "longitude": 31.2357,
  "metadata": {}
}

### GET /api/shipments/order/{orderId}/ids

Path params:
- orderId: order id

Returns: List<Long>

Response JSON:

[700, 701]

## Billing Service

### GET /api/transactions/user/{userId}/total?startDate={date}&endDate={date}

Path params:
- userId: user id

Query params:
- startDate: start date
- endDate: end date

Returns: BigDecimal

Response JSON:

15000.0

### GET /api/transactions/user/{userId}/order-count?startDate={date}&endDate={date}

Path params:
- userId: user id

Query params:
- startDate: start date
- endDate: end date

Returns: long

Response JSON:

12
