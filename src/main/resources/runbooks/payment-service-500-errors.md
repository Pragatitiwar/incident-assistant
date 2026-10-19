# Runbook: Payment Service Returning HTTP 500 Errors

**Service:** payment-service
**Owner:** Payments Team (#payments-oncall)
**Severity guide:** SEV1 if error rate > 5% for 5 minutes, SEV2 if 1-5%

## Symptoms
- Error rate on `payment-service` above 1% in the "Payments Overview" dashboard
- Customers see "Payment failed, please try again" at checkout
- Alert `PaymentService5xxHigh` fires in PagerDuty

## Likely Causes
1. Database connection pool exhausted (HikariCP pending threads > 0)
2. Payment gateway (Stripe) timeouts or elevated error rates
3. A recent deployment introduced a bug
4. Expired or rotated API key for the payment gateway

## Diagnostic Steps
1. Check recent deployments: run `kubectl rollout history deployment/payment-service -n payments`. If a deploy happened in the last 30 minutes, suspect it first.
2. Check pod health: `kubectl get pods -n payments -l app=payment-service`. Look for CrashLoopBackOff or frequent restarts.
3. Check logs for the first exception: `kubectl logs deployment/payment-service -n payments --since=15m | grep ERROR`. Find the earliest error, not the latest.
4. Check the HikariCP metric `hikaricp_connections_pending` in Grafana. A value above 0 for more than a minute means pool exhaustion (see runbook: db-connection-pool-exhausted).
5. Check the Stripe status page and the `gateway_latency_p99` metric. If p99 is above 5 seconds, the gateway is slow.

## Fixes
- **Bad deployment:** roll back with `kubectl rollout undo deployment/payment-service -n payments`, then confirm the error rate drops within 5 minutes.
- **Pool exhaustion:** follow the db-connection-pool-exhausted runbook.
- **Gateway slowness:** enable the circuit breaker flag `payments.gateway.circuit-breaker` in the feature flag console so requests fail fast and queue for retry.
- **Expired gateway key:** rotate the key in Vault at `secret/payments/stripe`, then restart the deployment.

## Verification
- Error rate below 0.5% for 10 minutes
- Test payment succeeds in the staging checkout flow
- No new `PaymentService5xxHigh` alerts

## Escalation
- If not resolved within 20 minutes, page the Payments Team lead
- If the gateway is confirmed down, notify the Incident Commander and post in #incidents
- Do not reprocess failed payments manually without checking for duplicate charges