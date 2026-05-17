# Shipping Service PromQL Panels

## Panel 1 — Shipping HTTP Request Rate

```promql
sum(rate(http_server_requests_seconds_count{application="shipping-service"}[5m]))
```

This panel shows how many requests per second shipping-service receives.

## Panel 2 — Shipping HTTP Errors by Status

```promql
sum by (status) (http_server_requests_seconds_count{application="shipping-service", status=~"4..|5.."})
```

This panel shows shipping-service errors grouped by HTTP status code.

## Panel 3 — Shipping JVM Heap Memory Usage

```promql
sum(jvm_memory_used_bytes{application="shipping-service", area="heap"})
```

This panel shows how much Java heap memory shipping-service is using.

## Optional Panel 4 — Shipping Request Latency p95

```promql
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="shipping-service"}[5m])) by (le))
```

This panel shows the 95th percentile request latency for shipping-service.
