# M3 Contract-First Day-0 Setup

This branch provides the shared contracts and stubs required so all members can work independently.

## Shared Contracts Module

Module:

contracts

Package:

com.team27.amazon.contracts

Includes:

- Shared DTOs
- Shared Feign client interfaces
- Shared RabbitMQ event payload records
- Shared exchange and routing key constants

## Rule

Members should depend on contracts instead of copying DTOs or inventing new Feign signatures.

## Shared Service Names

user-service
product-service
order-service
shipping-service
billing-service
api-gateway
rabbitmq
loki
prometheus
grafana

## PostgreSQL Service Names

user-postgres
product-postgres
order-postgres
shipping-postgres
billing-postgres

## Databases

amazondb-users
amazondb-products
amazondb-orders
amazondb-shipments
amazondb-billing

## Shared RabbitMQ Exchanges

user.events
product.events
order.events
shipment.events
payment.events

## Rule of Communication

Feign is used for reads.

RabbitMQ is used for side effects.

No service should directly query or update another service's PostgreSQL database.

## Shared YAML Stubs

This branch includes placeholders for:

- api-gateway route configuration
- Prometheus scrape jobs
- Grafana dashboard registration

Each slice owner should only edit their assigned service block.
