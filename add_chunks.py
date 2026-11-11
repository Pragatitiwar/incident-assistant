import json, sys

path = sys.argv[1]
chunks = {
    1: ["payment-service-500-errors#Fixes"],
    2: ["db-connection-pool-exhausted#Symptoms"],
    3: ["db-connection-pool-exhausted#Likely Causes"],
    4: ["kafka-consumer-lag#Fixes"],
    5: ["high-latency-checkout-api#Fixes"],
    6: ["pod-crashloopbackoff#Likely Causes"],
    7: ["db-connection-pool-exhausted#Diagnostic Steps"],
    8: ["kafka-consumer-lag#Diagnostic Steps"],
    9: ["certificate-expiry#Diagnostic Steps"],
    10: ["disk-space-full#Fixes"],
    11: ["pod-crashloopbackoff#Diagnostic Steps"],
    12: ["kafka-consumer-lag#Fixes"],
    13: ["redis-cache-failures#Fixes"],
    14: ["db-connection-pool-exhausted#Diagnostic Steps", "db-connection-pool-exhausted#Fixes"],
    15: ["high-latency-checkout-api#Fixes", "redis-cache-failures#Fixes"],
    16: ["kafka-consumer-lag#Fixes"],
    17: ["disk-space-full#Diagnostic Steps", "disk-space-full#Fixes"],
}
data = json.load(open(path))
for q in data:
    q["expectedChunks"] = chunks.get(q["id"], [])
json.dump(data, open(path, "w"), indent=2)
print("updated", len(data), "questions;", sum(1 for q in data if q["expectedChunks"]), "with expected chunks")
