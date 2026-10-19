# Runbook: Database Connection Pool Exhausted

**Service:** Any Spring Boot service using HikariCP (most common: payment-service, order-service)
**Owner:** Platform Team (#platform-oncall)
**Severity guide:** SEV1 if payments or checkout are affected, otherwise SEV2

## Symptoms
- Requests fail or hang, with `HikariPool-1 - Connection is not available, request timed out after 30000ms` in logs
- Grafana metric `hikaricp_connections_pending` above 0 for more than 1 minute
- Alert `DBPoolSaturationHigh` fires when `hikaricp_connections_active` equals `hikaricp_connections_max` for 3 minutes
- Latency rises first, then HTTP 500 errors follow

## Likely Causes
1. A slow or long-running query holding connections (missing index, large table scan)
2. Connection leak: code that opens a connection and never closes it
3. Traffic spike beyond the configured pool size (default `maximum-pool-size` is 20)
4. Database under load or in failover, so queries take much longer than normal
5. Transactions left open in the state "idle in transaction"

## Diagnostic Steps
1. Open the "JVM and DB Pools" dashboard in Grafana and compare `hikaricp_connections_active`, `idle`, and `pending` for the affected service.
2. Connect to the database and look for long-running queries:
   `SELECT pid, now() - query_start AS duration, state, left(query, 80) FROM pg_stat_activity WHERE state <> 'idle' ORDER BY duration DESC LIMIT 10;`
3. Look for stuck transactions:
   `SELECT pid, now() - xact_start AS xact_age, query FROM pg_stat_activity WHERE state = 'idle in transaction' ORDER BY xact_age DESC;`
4. Check whether a deployment happened in the last hour: `kubectl rollout history deployment/<service> -n <namespace>`.
5. Check logs for leak warnings: `kubectl logs deployment/<service> -n <namespace> --since=15m | grep "Apparent connection leak"`.

## Fixes
- **Stuck or runaway queries:** cancel with `SELECT pg_cancel_backend(<pid>);`. Use `pg_terminate_backend(<pid>)` only if the cancel does not work, and never on replication or backup sessions.
- **Idle-in-transaction sessions older than 10 minutes:** terminate them with `pg_terminate_backend`.
- **Traffic spike:** raise `spring.datasource.hikari.maximum-pool-size` in the service ConfigMap (for example 20 to 30), then run `kubectl rollout restart deployment/<service>`. Do not exceed 80 total connections per database, as the primary allows 200 and many services share it.
- **Connection leak after a deploy:** roll back with `kubectl rollout undo deployment/<service> -n <namespace>`.

## Verification
- `hikaricp_connections_pending` returns to 0
- `hikaricp_connections_active` stays below 70% of the maximum for 15 minutes
- Error rate on the affected service is below 0.5%

## Escalation
- If not resolved in 20 minutes, page the Database Team (#dba-oncall)
- If the database itself is unhealthy (failover, high CPU above 90%), page the DBA on call immediately and notify the Incident Commander
