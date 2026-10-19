# Runbook: High Latency on Checkout API

**Service:** checkout-api
**Owner:** Checkout Team (#checkout-oncall)
**Severity guide:** SEV1 if p99 latency exceeds 5 seconds, SEV2 if p99 is between 2 and 5 seconds

## Symptoms
- Alert `CheckoutLatencyP99High` fires when p99 of `http_server_requests_seconds` for `/api/checkout` exceeds 2 seconds for 5 minutes
- Customers report a slow or spinning checkout page
- Cart abandonment rate on the "Checkout Funnel" dashboard rises above 35%

## Likely Causes
1. Slow call to `inventory-service` (stock check on every cart item)
2. The pricing cache is cold or unavailable, so prices are recalculated from the database
3. The recommendations widget calling a slow ML service
4. CPU throttling on checkout-api pods after a traffic spike
5. A recent deployment with an inefficient query or an N+1 problem

## Diagnostic Steps
1. Open a slow trace in Jaeger: filter service `checkout-api`, min duration 2s. Find which span takes the most time.
2. Check dependency latency in the "Checkout Dependencies" dashboard: `inventory_client_latency_p99`, `pricing_client_latency_p99`, `recommendations_client_latency_p99`.
3. Check CPU throttling: look at `container_cpu_cfs_throttled_seconds_total` for checkout-api. A rising value means pods need more CPU.
4. Check pod count against traffic: `kubectl get hpa checkout-api -n checkout`. If current replicas equal max replicas (10), autoscaling is capped.
5. Check recent deployments: `kubectl rollout history deployment/checkout-api -n checkout`.

## Fixes
- **Slow recommendations service:** turn off the feature flag `checkout.recommendations.enabled` in the flag console. This removes the widget and usually cuts latency by 300 to 800 ms.
- **Cold or down pricing cache:** see the redis-cache-failures runbook. As a stopgap, enable `pricing.cache.fallback.db.enabled`.
- **Capacity:** raise the HPA maximum with `kubectl patch hpa checkout-api -n checkout -p '{"spec":{"maxReplicas":16}}'`.
- **Bad deployment:** roll back with `kubectl rollout undo deployment/checkout-api -n checkout`.
- **Slow inventory-service:** set `inventory.check.mode` to `lenient` so checkout accepts the cart and verifies stock asynchronously. Tell the Incident Commander before doing this, because overselling is possible.

## Verification
- p99 latency under 1.5 seconds for 15 minutes
- Cart abandonment back below 25%
- No `CheckoutLatencyP99High` alert for 30 minutes

## Escalation
- If not resolved in 30 minutes, page the Checkout Team lead
- If the root cause is inventory-service, page the Inventory Team (#inventory-oncall)
