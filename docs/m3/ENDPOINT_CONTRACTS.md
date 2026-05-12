# M3 Endpoint Contracts

This file documents the shared endpoint paths and DTO shapes used by the contracts module.

## User Service

### GET /api/users/{id}
Returns UserDTO.

Fields:
- id
- name
- email
- phone
- role
- status
- preferences

### GET /api/users/{userId}/addresses/{addressId}
Returns ShippingAddressDTO.

Fields:
- id
- userId
- addressLine
- city
- governorate
- postalCode
- country

## Product Service

### GET /api/products/{id}
Returns ProductDTO.

Fields:
- id
- name
- description
- price
- category
- brand
- stockQuantity
- status
- rating
- specifications

### GET /api/products/{id}/exists
Returns ProductExistsDTO.

Fields:
- exists

### GET /api/products/batch?ids=1,2,3
Returns List<ProductDTO>.

## Order Service

### GET /api/orders/{orderId}
Returns OrderDTO.

Fields:
- id
- userId
- shippingAddressId
- status
- totalAmount
- orderedAt
- deliveredAt
- metadata

### GET /api/orders/{orderId}/items
Returns List<OrderItemDTO>.

Fields:
- id
- orderId
- productId
- quantity
- priceAtPurchase
- itemOrder
- metadata

### GET /api/orders/user/{userId}/summary
Returns OrderSummaryDTO.

Fields:
- totalOrders
- completedOrders
- cancelledOrders
- totalSpent
- averageOrderValue

### GET /api/orders/user/{userId}/active-count
Returns int.

### GET /api/orders/user/{userId}/count
Returns long.

### GET /api/orders/product/{productId}/sales?startDate={date}&endDate={date}
Returns ProductSalesAggregateDTO.

Fields:
- totalUnitsSold
- totalRevenue
- averageSellingPrice

### GET /api/orders/product/{productId}/pending-count
Returns int.

### GET /api/orders/product/{productId}/units-sold
Returns long.

### GET /api/orders/product/{productId}/recent-sales-count?days={n}
Returns int.

### GET /api/orders/user/{userId}/has-purchased/{productId}
Returns boolean.

## Shipping Service

### GET /api/shipments/order/{orderId}/active
Returns ShipmentDTO.

Fields:
- id
- orderId
- carrier
- trackingNumber
- status
- estimatedDelivery
- actualDelivery
- lastUpdate
- latitude
- longitude
- metadata

### GET /api/shipments/order/{orderId}/ids
Returns List<Long>.

## Billing Service

### GET /api/transactions/user/{userId}/total?startDate={date}&endDate={date}
Returns BigDecimal.

### GET /api/transactions/user/{userId}/order-count?startDate={date}&endDate={date}
Returns long.
