# Kubernetes example

Создайте `account-config`, `account-secrets`, `account-jwt-keys` и
`account-grpc-tls`, затем примените manifests:

```sh
kubectl apply -k examples/kubernetes
```

Migration Job должен успешно завершиться до rollout backend. В production
gRPC требует server certificate, private key, client CA и SAN allowlist.
