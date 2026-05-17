# Shipping Service LogQL Panels

## Panel 1: Shipping service error rate

sum(rate({service="shipping-service"} |= "level=ERROR" [5m]))

## Panel 2: Active shipment endpoint traffic

sum(rate({service="shipping-service"} |= "/api/shipments/order/" |= "/active" [5m]))

## Panel 3: Shipment IDs endpoint traffic

sum(rate({service="shipping-service"} |= "/api/shipments/order/" |= "/ids" [5m]))
