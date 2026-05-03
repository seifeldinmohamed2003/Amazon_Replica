# Shipping Service Module

## Overview

This branch contains the integrated Shipping Service module for Milestone 2.

It combines the completed shipping feature work into one shared integration branch:

`feat/shipping/milestone-2`

This branch includes the previously integrated shipping features and the Milestone 2 shipping features S4-F10, S4-F11, and S4-F12.

## Implemented Features

### Completed

- S4-F1: Get latest shipment by order ID
- S4-F2: Create shipment for an order
- S4-F3: Find nearby shipments
- S4-F7: Purge old shipments
- S4-F8: Carrier performance summary
- S4-F9: Delayed shipments
- S4-F10: Shipping analytics dashboard
- S4-F11: Record shipment tracking event
- S4-F12: Get shipment tracking timeline

## Verified Milestone 2 Features

The following Milestone 2 features were manually tested and verified.

### S4-F10: Shipping Analytics Dashboard

Verified behavior:

- Requires JWT authentication
- Returns `401 Unauthorized` when token is missing
- Returns `400 Bad Request` for invalid date range
- Calculates total shipments
- Calculates average delivery time
- Calculates on-time delivery rate
- Groups shipments by status
- Calculates average delivery attempts
- Stores analytics result in Redis cache
- Logs `ANALYTICS_VIEWED` event in MongoDB

### S4-F11: Record Shipment Tracking Event

Verified behavior:

- Requires JWT authentication
- Returns `401 Unauthorized` when token is missing
- Creates tracking event successfully
- Returns `201 Created`
- Returns `404 Not Found` for invalid shipment ID
- Stores tracking event in Cassandra

### S4-F12: Shipment Tracking Timeline

Verified behavior:

- Requires JWT authentication
- Returns shipment tracking timeline
- Supports optional `startTime` and `endTime` filters
- Returns `401 Unauthorized` when token is missing
- Returns `404 Not Found` for invalid shipment ID
- Stores timeline result in Redis cache

## Technical Notes

- PostgreSQL is used for shipment records.
- Redis is used for caching.
- MongoDB is used for event logging.
- Cassandra is used for shipment tracking events.
- The shipping service runs internally on port `8080`.
- In Docker Compose, shipping is exposed locally on port `8084`.

## Notes

This is a shared Milestone 2 shipping integration branch.

The branch should be used for the final Pull Request into `main` after all shipping Milestone 2 work is confirmed by the team.
