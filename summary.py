import json, sys
r = json.load(open(sys.argv[1]))
keys = ['total','passed','retrievalHitRate','chunkRecall','answerAccuracy','correctRefusalRate']
print({k: r[k] for k in keys})
ans = [c for c in r['results'] if c['type'] != 'unanswerable']
print('refusals with full evidence:', [c['id'] for c in ans if c['refused'] and c['chunkRecall'] == 1.0])
for c in r['results']:
    if not c['passed']:
        print(c['id'], c['type'], '| recall:', c['chunkRecall'], '| missing:', c['missingFacts'])
