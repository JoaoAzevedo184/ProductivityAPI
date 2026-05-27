#!/usr/bin/env bash
# ============================================================
# deploy-local.sh — fecha o ciclo CD localmente
#
# O cd.yml publica a imagem no GHCR. Este script PUXA essa imagem
# e aplica no cluster Kind local via Helm — reproduzindo o que um
# runner com acesso ao cluster faria automaticamente.
#
# Uso:
#   ./deploy-local.sh <env> <image-tag>
#
# Exemplos:
#   ./deploy-local.sh dev sha-a1b2c3d
#   ./deploy-local.sh staging sha-a1b2c3d
#   ./deploy-local.sh prod sha-a1b2c3d
#
# Pré-requisitos: docker, kind, kubectl, helm e o cluster Kind "productivity" criado.
# ============================================================
set -euo pipefail

ENV="${1:-dev}"
IMAGE_TAG="${2:-latest}"

# Ajuste pro seu usuário/repo (minúsculas — exigência do GHCR)
REGISTRY="ghcr.io"
IMAGE_REPO="joaoazevedo184/productivity-api"
CLUSTER_NAME="productivity"

# Mapeia env → namespace + values file
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
    echo "❌ Ambiente inválido: '$ENV'. Use: dev | staging | prod"
    exit 1
    ;;
esac

FULL_IMAGE="${REGISTRY}/${IMAGE_REPO}:${IMAGE_TAG}"

echo "============================================================"
echo "  Deploy local — productivity-api"
echo "  Ambiente:  $ENV"
echo "  Namespace: $NAMESPACE"
echo "  Values:    $VALUES_FILE"
echo "  Imagem:    $FULL_IMAGE"
echo "============================================================"

# 1. Puxa a imagem publicada pelo pipeline
echo "→ [1/3] Puxando imagem do GHCR..."
docker pull "$FULL_IMAGE"

# 2. Carrega no Kind (sem isso → ImagePullBackOff dentro do cluster)
echo "→ [2/3] Carregando imagem no cluster Kind..."
kind load docker-image "$FULL_IMAGE" --name "$CLUSTER_NAME"

# 3. Deploy via Helm
echo "→ [3/3] Aplicando via Helm..."
helm upgrade --install productivity ./helm/productivity-api \
  -f helm/productivity-api/values.yaml \
  -f "helm/productivity-api/${VALUES_FILE}" \
  --set "image.repository=${REGISTRY}/${IMAGE_REPO}" \
  --set "image.tag=${IMAGE_TAG}" \
  --namespace "$NAMESPACE" \
  --create-namespace \
  --wait --timeout 5m

echo ""
echo "✅ Deploy concluído. Status dos pods:"
kubectl get pods -n "$NAMESPACE"