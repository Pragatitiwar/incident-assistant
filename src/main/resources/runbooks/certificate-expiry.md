# Runbook: TLS Certificate Expiring or Expired

**Service:** Public API gateway (`api.example.com`) and internal service mesh certificates
**Owner:** Platform Security Team (#security-oncall)
**Severity guide:** SEV1 if a public certificate has expired, SEV2 if expiry is less than 7 days away, SEV3 if less than 30 days

## Symptoms
- Browsers show `NET::ERR_CERT_DATE_INVALID`, and mobile apps fail with SSL handshake errors
- Alert `CertificateExpiringSoon` fires when `certmanager_certificate_expiration_timestamp_seconds` is less than 14 days away
- Service-to-service calls fail with `PKIX path validation failed` or `certificate has expired`
- Webhooks from partners stop arriving

## Likely Causes
1. cert-manager failed to renew automatically, usually because the ACME challenge failed
2. DNS or ingress changes broke the HTTP-01 or DNS-01 validation
3. The certificate was issued manually and nobody set a renewal reminder
4. The renewed certificate was issued but the pods still use the old one (not reloaded)
5. The issuer rate limit was reached (Let's Encrypt allows 5 duplicate certificates per week)

## Diagnostic Steps
1. Check the certificate the public endpoint is serving:
   `echo | openssl s_client -connect api.example.com:443 -servername api.example.com 2>/dev/null | openssl x509 -noout -dates -subject`
2. List cert-manager certificates and their status: `kubectl get certificate -A`. Look for `READY False`.
3. Inspect the failing certificate: `kubectl describe certificate <name> -n <namespace>` and then `kubectl describe certificaterequest -n <namespace>`.
4. Check ACME challenges: `kubectl get challenges -A` and `kubectl describe challenge <name> -n <namespace>`. A `pending` challenge usually means DNS or ingress problems.
5. Check cert-manager logs: `kubectl logs deployment/cert-manager -n cert-manager --since=1h | grep -i error`.

## Fixes
- **Renewal stuck:** trigger a new request with `cmctl renew <certificate-name> -n <namespace>`.
- **Failed challenge:** fix the underlying DNS record or ingress rule, then delete the failed challenge so cert-manager retries: `kubectl delete challenge <name> -n <namespace>`.
- **Certificate renewed but old one still served:** restart the ingress controller with `kubectl rollout restart deployment/ingress-nginx-controller -n ingress-nginx`, or restart the affected service.
- **Rate limit reached:** switch temporarily to the backup issuer by changing `issuerRef` to `letsencrypt-backup` (ZeroSSL).
- **Emergency (certificate already expired and renewal is failing):** ask the Security Team for a manually issued certificate from the internal CA, then install it with `kubectl create secret tls api-example-tls --cert=tls.crt --key=tls.key -n ingress-nginx --dry-run=client -o yaml | kubectl apply -f -`.

## Verification
- The `openssl s_client` command shows a `notAfter` date at least 60 days in the future
- `kubectl get certificate -A` shows all certificates `READY True`
- Mobile and web clients connect without TLS errors
- Partner webhooks resume

## Escalation
- For an expired public certificate, page the Security Team lead and notify the Incident Commander immediately
- Post a customer-facing update in #incidents if clients are failing to connect
