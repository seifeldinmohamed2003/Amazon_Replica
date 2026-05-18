## Kubernetes Runbook

Apply the application namespace first, then the application manifests, then the monitoring namespace and stack:

```bash
kubectl apply -f k8s/namespaces/namespace.yaml
kubectl apply -f k8s/secrets/
kubectl apply -f k8s/pvcs/
kubectl apply -f k8s/statefulsets/
kubectl apply -f k8s/configmaps/
kubectl apply -f k8s/deployments/
kubectl apply -f k8s/services/
kubectl apply -f k8s/api-gateway/

kubectl apply -f k8s/namespaces/monitoring-namespace.yaml
kubectl apply -f k8s/monitoring/loki/
kubectl apply -f k8s/monitoring/prometheus/
kubectl apply -f k8s/monitoring/grafana/
```

Smoke test the cluster with the exact demo commands:

```bash
kubectl get pods -n amazon
kubectl logs <your-service-pod> -n amazon
curl http://$(minikube ip):30080/api/<endpoint>
```

If the observability stack is part of the demo, open Grafana at `http://$(minikube ip):30030`.
# Shipping Service — S4-READ-DB Branch

## Branch

feat/M3/shipping/S4-READ-DB/55-8447

---

## Overview

This branch contains the Milestone 3 Shipping Service database isolation and integration preparation work.

The goal of this branch is to prepare shipping-service for:

- Independent database ownership
- Saga/event-driven integration
- Feign-based synchronous communication
- Shared infrastructure compatibility
- Monitoring and observability support

---

## Included Work

### Shipping Database Isolation

Configured dedicated Shipping Service database ownership.

Database:

amazondb-shipments

PostgreSQL service:

shipping-postgres

Purpose:

- Prevent cross-service DB coupling
- Ensure microservice isolation
- Support independent deployments

---

### Shipping Integration Endpoints

Added:

GET /api/shipments/order/{orderId}/active

Purpose:

- Active shipment lookup
- Saga pre-check endpoint
- Shipment validation

Added:

GET /api/shipments/order/{orderId}/ids

Purpose:

- Shipment aggregation support
- Timeline lookup support
- Future integration support

---

### Feign Integration

Added:

- OrderServiceClient
- ProductServiceClient

Purpose:

- Replace direct DB communication
- Support compile-time independence
- Standardize service reads

---

### RabbitMQ Saga Preparation

Prepared Shipping Service for event-driven communication.

Integrated support for:

- order.completed
- order.cancelled
- shipment.created
- shipment.status-changed
- shipment.cancelled

Purpose:

- Saga orchestration
- Decoupled communication
- Asynchronous side effects

---

### Logging Improvements

Added structured logging preparation.

Includes:

- RabbitMQ event logs
- Consumer logs
- Publisher logs
- Integration tracing support

---

### Monitoring Preparation

Prepared monitoring support for:

- Prometheus
- Grafana
- LogQL
- logback-spring.xml

---

### Kubernetes Preparation

Prepared stable Kubernetes naming compatibility.

Services:

- shipping-service
- shipping-postgres
- rabbitmq
- prometheus
- grafana
- loki

---

## Build Verification

Verified successfully:

mvn -pl shipping-service -am clean package -DskipTests

mvn clean compile -DskipTests

Result:

BUILD SUCCESS

---

## Communication Rules

### Reads

Use:

Feign Clients

### Side Effects

Use:

RabbitMQ Events

### Forbidden

Shipping Service must NOT:

- Directly query another service database
- Share PostgreSQL schemas
- Depend directly on another service implementation

---

## Final Status

✔ Shipping DB isolation prepared  
✔ Dedicated shipment database configured  
✔ Shared integration endpoints added  
✔ Feign clients added  
✔ RabbitMQ integration prepared  
✔ Saga communication support added  
✔ Logging improvements added  
✔ Monitoring preparation added  
✔ Kubernetes naming prepared  
✔ Build verification passed

---

## Author

Seif Elsherbiny — 55-8447
