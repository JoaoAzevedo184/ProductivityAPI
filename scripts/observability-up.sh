#!/usr/bin/env bash
# =============================================================================
# observability-up.sh — sobe a stack de observabilidade no Kind (Desafio 05)
#
# A ORDEM importa e é o ponto que mais quebra:
#   1. Instalar kube-prometheus-stack  -> cria os CRDs (ServiceMonitor,
#                                          PrometheusRule) e o Operator.
#   2. Aplicar as PrometheusRules.
#   3. Provisionar o dashboard via ConfigMap.
#   4. Atualizar o chart da app com serviceMonitor.enabled=true.
#      (NÃO ANTES do passo 1, senão o CRD não existe e o helm explode.)
#
# Uso (da RAIZ do repo):
#   ./scripts/observability-up.sh
#
# Pré-requisitos: helm, kubectl, cluster Kind "productivity" já criado,
# productivity-api já instalada no namespace productivity-dev.
# =============================================================================
set -euo pipefail

STACK_RELEASE="kube-prom-stack"
OBS_NS="observability"
APP_NS="productivity-dev"
APP_RELEASE="productivity"
CHART="./productivity-api/helm/productivity-api"
# observability/ fica na RAIZ do repo (junto de scripts/ e syslog-ng/),
# diferente de helm/ e k8s/ que ficam dentro do módulo productivity-api/.
OBS_DIR="./observability"

echo "==> [1/4] Instalando kube-prometheus-stack (cria CRDs + Operator)..."
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null 2>&1 || true
helm repo update >/dev/null

helm upgrade --install "$STACK_RELEASE" prometheus-community/kube-prometheus-stack \
  --namespace "$OBS_NS" --create-namespace \
  -f "${OBS_DIR}/prometheus-values.yaml" \
  --wait --timeout 10m

echo "==> Aguardando o CRD ServiceMonitor ficar disponível..."
kubectl wait --for=condition=Established \
  crd/servicemonitors.monitoring.coreos.com --timeout=120s

echo "==> [2/4] Aplicando regras de alerta (PrometheusRule)..."
kubectl apply -f "${OBS_DIR}/prometheus-rules.yaml"

echo "==> [3/4] Provisionando dashboard Grafana via ConfigMap..."
# A label grafana_dashboard=1 faz o sidecar do Grafana importar o JSON.
kubectl create configmap productivity-api-dashboard \
  --namespace "$OBS_NS" \
  --from-file="${OBS_DIR}/grafana-dashboards/productivity-api.json" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl label configmap productivity-api-dashboard \
  --namespace "$OBS_NS" grafana_dashboard=1 --overwrite

echo "==> [4/4] Religando o chart da app com serviceMonitor.enabled=true..."
helm upgrade "$APP_RELEASE" "$CHART" \
  -f "${CHART}/values.yaml" \
  -f "${CHART}/values-dev.yaml" \
  --namespace "$APP_NS" \
  --set serviceMonitor.enabled=true \
  --wait --timeout 5m

cat <<EOF

============================================================
  Stack de observabilidade no ar.

  Grafana (NodePort 30000 — se o Kind mapear, abra http://localhost:30000):
    kubectl port-forward -n ${OBS_NS} svc/${STACK_RELEASE}-grafana 3000:80
    -> http://localhost:3000   (user: admin / pass: admin)

  Prometheus:
    kubectl port-forward -n ${OBS_NS} svc/${STACK_RELEASE}-kube-prome-prometheus 9090:9090
    -> http://localhost:9090

  Alertmanager:
    kubectl port-forward -n ${OBS_NS} svc/${STACK_RELEASE}-kube-prome-alertmanager 9093:9093

  Conferir se o scrape da app pegou (deve aparecer "up"):
    No Prometheus, Status -> Targets -> procurar productivity-api
============================================================
EOF