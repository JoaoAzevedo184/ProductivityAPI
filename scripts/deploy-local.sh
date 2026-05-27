#!/usr/bin/env bash
# ============================================================
# deploy-local.sh — fecha o ciclo CD localmente
#
# O cd.yml publica a imagem no GHCR. Este script PUXA essa imagem
# e aplica no cluster Kind local via Helm — reproduzindo o que um
# runner com acesso ao cluster faria automaticamente.
#
# Uso (rodar da RAIZ do repositorio):
#   ./scripts/deploy-local.sh <env> <image-tag>
#
# Exemplos:
#   ./scripts/deploy-local.sh dev sha-b0d47c7
#   ./scripts/deploy-local.sh staging sha-b0d47c7
#   ./scripts/deploy-local.sh prod sha-b0d47c7
#
# Pre-requisitos: docker, kind, kubectl, helm e o cluster Kind "productivity" criado.
#
# NOTA: usa `docker save` + `kind load image-archive`. Imagens do pipeline
# sao buildadas com provenance/sbom desabilitados (ver cd.yml), o que evita
# o erro "content digest ... not found" do `kind load`.
# ============================================================
set -euo pipefail

ENV="${1:-dev}"
IMAGE_TAG="${2:-latest}"

# Ajuste pro seu usuario/repo (minusculas — exigencia do GHCR)
REGISTRY="ghcr.io"
IMAGE_REPO="joaoazevedo184/productivityapi"   # repo: ProductivityAPI -> productivityapi
CLUSTER_NAME="productivity"

# Caminho do chart relativo a RAIZ do repo.
# A pasta raiz e o modulo Maven tem o mesmo nome (productivity-api),
# por isso o chart fica em productivity-api/helm/productivity-api.
HELM_CHART="./productivity-api/helm/productivity-api"

# Mapeia env -> namespace + values file
case "$ENV" in
  dev)
    NAMESPACE="productivity-dev"
    VALUES_FILE="values-dev.yaml"
    ;;
  staging)
    NAMESPACE="productivity-staging"
    VALUES_FILE="values-staging.yaml"
    ;;
  prod)
    NAMESPACE="productivity"
    VALUES_FILE="values-prod.yaml"
    ;;
  *)
    echo "Ambiente invalido: '$ENV'. Use: dev | staging | prod"
    exit 1
    ;;
esac

FULL_IMAGE="${REGISTRY}/${IMAGE_REPO}:${IMAGE_TAG}"
TARFILE="$(mktemp -t prod-api-XXXXXX.tar)"

cleanup() { rm -f "$TARFILE"; }
trap cleanup EXIT

echo "============================================================"
echo "  Deploy local — productivity-api"
echo "  Ambiente:  $ENV"
echo "  Namespace: $NAMESPACE"
echo "  Values:    $VALUES_FILE"
echo "  Chart:     $HELM_CHART"
echo "  Imagem:    $FULL_IMAGE"
echo "============================================================"

echo "-> [1/4] Puxando imagem do GHCR..."
docker pull "$FULL_IMAGE"

echo "-> [2/4] Exportando imagem pra archive..."
docker save "$FULL_IMAGE" -o "$TARFILE"

echo "-> [3/4] Carregando archive no cluster Kind..."
kind load image-archive "$TARFILE" --name "$CLUSTER_NAME"

echo "-> [4/4] Aplicando via Helm..."
helm upgrade --install productivity "$HELM_CHART" \
  -f "${HELM_CHART}/values.yaml" \
  -f "${HELM_CHART}/${VALUES_FILE}" \
  --set "image.repository=${REGISTRY}/${IMAGE_REPO}" \
  --set "image.tag=${IMAGE_TAG}" \
  --namespace "$NAMESPACE" \
  --create-namespace \
  --wait --timeout 5m

echo ""
echo "OK Deploy concluido. Status dos pods:"
kubectl get pods -n "$NAMESPACE"