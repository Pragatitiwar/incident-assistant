# Runbook: Pod in CrashLoopBackOff

**Service:** Any service running on Kubernetes (namespaces: payments, orders, checkout, platform)
**Owner:** Platform Team (#platform-oncall), with the owning service team
**Severity guide:** SEV1 if all pods of a customer-facing service are crashing, SEV3 if a single replica is affected

## Symptoms
- `kubectl get pods` shows `CrashLoopBackOff` or a rising RESTARTS count
- Alert `PodCrashLooping` fires when a pod restarts more than 3 times in 15 minutes
- Service capacity drops, and error rates may rise as healthy pods take extra load

## Likely Causes
1. Out of memory: the container is killed with exit code 137 and reason `OOMKilled`
2. Application fails at startup (bad configuration, missing environment variable, or missing secret)
3. A failing liveness probe kills a pod that is slow to start
4. The application cannot reach a dependency at startup (database or Vault)
5. A bad image tag or a broken deployment

## Diagnostic Steps
1. List the pods and restart counts: `kubectl get pods -n <namespace> -l app=<service>`.
2. Read the reason and last state: `kubectl describe pod <pod> -n <namespace>`. Look at `Last State`, `Reason`, `Exit Code`, and the Events section at the bottom.
3. Read the logs of the crashed container, not the new one: `kubectl logs <pod> -n <namespace> --previous`.
4. If the exit code is 137 or the reason is `OOMKilled`, compare the memory limit with usage in the "Pod Resources" dashboard (`container_memory_working_set_bytes`).
5. Check probe failures in the Events section (`Liveness probe failed` or `Readiness probe failed`).
6. Check whether the ConfigMap or Secret changed recently: `kubectl get configmap <name> -n <namespace> -o yaml`.

## Fixes
- **OOMKilled:** raise the memory limit in the deployment (for example 512Mi to 1Gi), then apply it. Also check for a memory leak if usage climbs steadily between restarts.
- **Bad configuration or missing secret:** restore the previous ConfigMap or Secret value, then `kubectl rollout restart deployment/<service> -n <namespace>`.
- **Probe too aggressive:** increase `initialDelaySeconds` on the liveness probe (for example from 10 to 60 for JVM services).
- **Bad deployment:** roll back with `kubectl rollout undo deployment/<service> -n <namespace>`.
- **Dependency unreachable at startup:** fix the dependency first, since pods will recover on the next restart.

## Verification
- All pods show `Running` with `READY 1/1` and the RESTARTS count stops increasing
- `PodCrashLooping` alert resolves
- Service error rate and latency back to normal

## Escalation
- If the cause is not found in 20 minutes, page the owning service team
- If multiple services crash at once, suspect a cluster problem and page the Platform Team lead
