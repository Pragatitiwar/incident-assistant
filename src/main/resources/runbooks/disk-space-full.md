# Runbook: Disk Space Full on Node or Volume

**Service:** Kubernetes worker nodes and persistent volumes (log volumes, Postgres data volumes)
**Owner:** Platform Team (#platform-oncall)
**Severity guide:** SEV1 if a database volume is above 95%, SEV2 if a node is above 90%, SEV3 for warning-level alerts

## Symptoms
- Alert `NodeDiskUsageHigh` fires when disk usage exceeds 85% (warning) or 92% (critical)
- Pods are evicted with the message `The node was low on resource: ephemeral-storage`
- Services log `No space left on device`
- Databases stop accepting writes if their data volume fills up

## Likely Causes
1. Application logs growing without rotation
2. Old container images and stopped containers accumulating on the node
3. Core dumps or heap dumps written to disk after crashes
4. Temporary files from a batch job that never cleaned up
5. Database growth, including write-ahead log (WAL) files piling up because of a stuck replica or a slot that is not consumed

## Diagnostic Steps
1. Identify the node or volume from the alert, then check usage: `df -h`.
2. Find the biggest directories: `du -sh /var/log/* /var/lib/* 2>/dev/null | sort -h | tail -15`.
3. Check container storage: `crictl images` and `crictl ps -a` to see unused images and exited containers.
4. Look for large recent files: `find / -xdev -type f -size +500M -mtime -2 2>/dev/null`.
5. For a Postgres volume, check WAL size and replication slots: `SELECT slot_name, active, pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)) AS retained FROM pg_replication_slots;`

## Fixes
- **Logs:** run `logrotate -f /etc/logrotate.conf`, then delete rotated files older than 7 days in `/var/log`. Do not delete logs from the last 24 hours, which are needed for incident analysis.
- **Unused images and containers:** run `crictl rmi --prune`.
- **Core or heap dumps:** copy to the archive bucket `s3://ops-dumps/` if they are needed, then remove them from the node.
- **Evicted pods:** after freeing space, remove them with `kubectl delete pod --field-selector=status.phase=Failed -n <namespace>`.
- **Persistent volume nearly full:** expand the PVC with `kubectl patch pvc <name> -n <namespace> -p '{"spec":{"resources":{"requests":{"storage":"200Gi"}}}}'`. Volume expansion is supported on the `gp3` storage class.
- **Inactive replication slot retaining WAL:** do not drop the slot yourself, and ask the DBA on call.

## Verification
- Disk usage below 75% on the node or volume
- No new `NodeDiskUsageHigh` alerts for 30 minutes
- Evicted or pending pods are running again

## Escalation
- If a database volume is above 95%, page the DBA on call (#dba-oncall) immediately
- If space cannot be freed, cordon the node with `kubectl cordon <node>` and ask the Platform Team to replace it
