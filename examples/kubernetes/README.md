# Kubernetes

Production-пример содержит Kustomize base и production overlay для приложения,
а observability разворачивается закреплённым официальным SigNoz Helm chart.

## Файлы

| Файл | Назначение |
|---|---|
| `deployment.yaml` | namespace, config, workloads, services, PVC и ingress |
| `secret.example.yaml` | шаблон application secrets и JWT-ключей |
| `network-policy.yaml` | изоляция внутренних сервисов |
| `signoz-values.yaml` | ресурсы и persistence для SigNoz + ClickHouse |

## Требования

- NGINX Ingress Controller;
- default `StorageClass`;
- CoreDNS/kube-dns;
- CNI с поддержкой `NetworkPolicy`;
- TLS-сертификат;
- SMTP с STARTTLS.
- Helm 3.

## Observability

Установите SigNoz до приложения. Chart управляет OTel Collector, ClickHouse,
ZooKeeper, SigNoz UI и схемой хранения:

```sh
helm repo add signoz https://charts.signoz.io
helm repo update
helm upgrade --install signoz signoz/signoz \
  --version 0.127.0 \
  --namespace signoz \
  --create-namespace \
  -f signoz-values.yaml
```

Для cluster/node/container logs и metrics установите официальный
`signoz/k8s-infra` chart, направив его в
`signoz-otel-collector.signoz.svc.cluster.local:4317`. Backend уже отправляет
JVM, HTTP, JDBC и Redis telemetry в SigNoz через OTLP.

После первого входа настройте retention, notification channels и правила из
`observability/signoz/alerts/README.md`.

## Настройка

В `deployment.yaml` замените:

- `account.example.com` и allowed origins;
- SMTP и публичный S3 URL;
- `ACCOUNT_TRUSTED_PROXY_CIDRS`;
- `ACCOUNT_DNS_RESOLVER` на ClusterIP DNS-сервиса;
- image tags, resources и размеры PVC.

Создайте рабочий файл секретов:

```sh
cp secret.example.yaml secret.yaml
```

Замените все `CHANGE_ME` и JWT placeholders. `secret.yaml` игнорируется Git.

## Развёртывание

```sh
kubectl create namespace account
kubectl apply -f secret.yaml
kubectl -n account create secret tls account-ingress-tls \
  --cert=tls.crt \
  --key=tls.key
kubectl apply -k overlays/production
kubectl -n account rollout status deployment/profile
kubectl -n account rollout status deployment/account-gateway
```

Проверка:

```sh
kubectl -n account get pods,svc,ingress,pvc
kubectl -n account logs deployment/profile
kubectl -n account logs deployment/account-gateway
```

## Production

NetworkPolicy ожидает ingress controller в namespace `ingress-nginx`; измените
selector при другой установке. Для отказоустойчивого production замените
одиночные PostgreSQL, Redis и MinIO StatefulSet на managed или HA-сервисы.
