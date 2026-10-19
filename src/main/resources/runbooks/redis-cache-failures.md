# Runbook: Redis Cache Failures

**Service:** redis-sessions and redis-pricing (Redis clusters used by checkout-api, auth-service, and pricing-service)
**Owner:** Platform Team (#platform-oncall)
**Severity guide:** SEV1 if user sessions or checkout are failing, SEV2 if only caching performance is degraded

## Symptoms
- Users are logged out unexpectedly or cannot log in
- Errors such as `RedisConnectionException` or `Unable to connect to redis-prod:6379` in service logs
- Alert `RedisMemoryHigh` fires when `redis_memory_used_bytes` exceeds 85% of `redis_memory_max_bytes`
- Database load rises sharply because requests are no longer served from cache

## Likely Causes
1. Memory is full and keys are being evicted (`redis_evicted_keys_total` rising)
2. The primary node failed and failover is in progress
3. Connection limit reached (`redis_connected_clients` close to the `maxclients` value of 10,000)
4. A large key or an expensive command (for example `KEYS *`) blocking the single-threaded server
5. Network or DNS problems between the pods and the Redis cluster

## Diagnostic Steps
1. Check memory: `redis-cli -h redis-prod INFO memory` and look at `used_memory_human` and `maxmemory_human`.
2. Check clients and evictions: `redis-cli -h redis-prod INFO stats | grep -E "evicted_keys|rejected_connections"` and `INFO clients`.
3. Check for slow commands: `redis-cli -h redis-prod SLOWLOG GET 10`.
4. Check cluster state: `redis-cli -h redis-prod CLUSTER INFO`. A `cluster_state` of `fail` means a node is down.
5. Check which services have the most connections in the "Redis Overview" dashboard.

## Fixes
- **Memory full:** find large keys with `redis-cli -h redis-prod --bigkeys`, then delete specific unneeded keys or lower their TTLs. Request a memory increase through the Platform Team if usage is genuinely growing.
- **Failover in progress:** wait up to 60 seconds for automatic promotion. If the state does not recover, ask the Platform Team to trigger a manual failover.
- **Connection exhaustion:** restart the service with the highest connection count using `kubectl rollout restart deployment/<service>`, and check its pool settings.
- **Blocking command:** identify the caller from the slowlog and the client list (`CLIENT LIST`), then stop that job.
- **While the cache is down:** enable `cache.fallback.db.enabled` so services read from the database, and watch database pool usage closely (see db-connection-pool-exhausted).
- **Never run FLUSHALL or FLUSHDB in production.** It logs out every user and sends a flood of load to the database.

## Verification
- `redis_memory_used_bytes` under 75% of the maximum
- `redis_evicted_keys_total` is flat
- Login and checkout success rates back to normal
- Database connection pool usage returned to its usual level

## Escalation
- If the cluster state is `fail` for more than 5 minutes, page the Platform Team lead
- Notify the Incident Commander if user sessions were lost
