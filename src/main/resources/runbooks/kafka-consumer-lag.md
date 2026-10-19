# Runbook: Kafka Consumer Lag Growing

**Service:** order-processor (consumer group `order-processor`, topic `order-events`)
**Owner:** Orders Team (#orders-oncall)
**Severity guide:** SEV2 if lag exceeds 50,000 messages for 10 minutes, SEV1 if orders are visibly delayed to customers

## Symptoms
- Alert `KafkaConsumerLagHigh` fires when `kafka_consumergroup_lag` for `order-processor` exceeds 10,000
- Customers report that order confirmation emails are delayed
- The "Kafka Overview" dashboard shows lag climbing steadily instead of oscillating near zero

## Likely Causes
1. Consumers are slower than producers (a traffic spike, or a slow downstream call inside the consumer)
2. A poison message that repeatedly fails and blocks one partition
3. A consumer rebalance loop caused by pods restarting or a short `max.poll.interval.ms`
4. Fewer consumer pods running than partitions (the topic has 12 partitions)
5. The downstream database or API that the consumer calls is slow

## Diagnostic Steps
1. Describe the group to see lag per partition:
   `kafka-consumer-groups.sh --bootstrap-server kafka-prod:9092 --describe --group order-processor`
   If only one partition has high lag, suspect a poison message. If all partitions are high, suspect throughput.
2. Check consumer pod count and health: `kubectl get pods -n orders -l app=order-processor`.
3. Check for rebalances: `kubectl logs deployment/order-processor -n orders --since=30m | grep -i rebalance`. More than 3 rebalances in 30 minutes means a loop.
4. Check logs for repeated exceptions on the same offset: `kubectl logs deployment/order-processor -n orders --since=15m | grep ERROR | sort | uniq -c | sort -rn | head`.
5. Check processing time per message in the `order_processing_duration_seconds` metric. A p99 above 2 seconds is a slow consumer.

## Fixes
- **Throughput problem:** scale up with `kubectl scale deployment/order-processor -n orders --replicas=12`. More than 12 replicas gives no benefit because there are only 12 partitions.
- **Poison message:** set the feature flag `orders.consumer.skip-to-dlq` to true so failing messages go to the topic `order-events.dlq`, then raise a ticket for the Orders Team to inspect them. Never delete messages from the topic.
- **Rebalance loop:** increase `max.poll.interval.ms` from 300000 to 600000 in the ConfigMap and restart the deployment.
- **Slow downstream dependency:** follow the runbook for that dependency (for example db-connection-pool-exhausted).

## Verification
- `kafka_consumergroup_lag` trending to below 1,000
- Order confirmation emails are being sent within 1 minute of order creation
- No new rebalance log lines for 15 minutes

## Escalation
- If lag keeps growing after scaling to 12 replicas, page the Orders Team lead
- If Kafka brokers are unhealthy (under-replicated partitions above 0), page the Streaming Platform Team (#streaming-oncall)
