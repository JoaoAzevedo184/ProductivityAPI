# ⛵ Desafio 03 — Deploy Orquestrado com Helm no Kubernetes

> **Empacotamento da productivity-api em um Helm Chart parametrizado**, com deploys em múltiplos ambientes e demonstração de upgrade/rollback.

| Campo | Valor |
|---|---|
| **Status** | 🔲 Planejado |
| **Aplicação-base** | productivity-api (imagem do [Desafio 02](challenge-02.md)) |
| **Ferramentas** | Kubernetes (Kind/k3d) + Helm 3 |
| **Modo** | Solo |

---

## 🎯 Objetivos

1. Criar um Helm Chart customizado para a productivity-api.
2. Suportar múltiplos ambientes via `values-dev.yaml`, `values-staging.yaml`, `values-prod.yaml`.
3. Demonstrar `install`, `upgrade`, `history`, `rollback`.
4. Configurar HPA (autoscaling), probes e recursos por ambiente.
5. Documentar processo e preparar apresentação.

---

## 🗺️ Metodologia

Sigo as três fases do enunciado:

### Fase 1 — Planejamento

- [ ] Definir recursos K8s necessários: `Deployment`, `Service`, `Ingress`, `HPA`, `ConfigMap`, `Secret`.
- [ ] Decidir o que vai em `ConfigMap` (não-secreto) vs. `Secret` (credenciais).
- [ ] Mapear variáveis configuráveis por ambiente.
- [ ] Escolher cluster local: **Kind** (mais simples) ou **k3d** (mais leve). → **Decisão: Kind** por melhor documentação.

### Fase 2 — Implementação

- [ ] Criar estrutura do chart com `helm create app`.
- [ ] Customizar templates para a productivity-api.
- [ ] Criar `values-*.yaml` para cada ambiente.
- [ ] Validar com `helm lint` e `helm template`.

### Fase 3 — Validação

- [ ] Instalar no Kind, validar pods rodando.
- [ ] Testar upgrade com mudança de versão.
- [ ] Testar rollback após "deploy ruim" proposital.
- [ ] Capturar logs e screenshots.

---

## 🏛️ Arquitetura no Kubernetes

```
┌──────────────────────────────────────────────────────────────┐
│                  Cluster Kubernetes (Kind)                   │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  Namespace: productivity-{env}                         │  │
│  │                                                        │  │
│  │   ┌─────────────┐                                      │  │
│  │   │   Ingress   │ ← productivity.local                 │  │
│  │   └──────┬──────┘                                      │  │
│  │          │                                             │  │
│  │          ▼                                             │  │
│  │   ┌─────────────┐                                      │  │
│  │   │   Service   │ (ClusterIP, porta 80)                │  │
│  │   └──────┬──────┘                                      │  │
│  │          │                                             │  │
│  │          ▼                                             │  │
│  │   ┌──────────────────────────────────┐                 │  │
│  │   │  Deployment (productivity-api)   │                 │  │
│  │   │  ├─ Pod 1                        │                 │  │
│  │   │  ├─ Pod 2                        │ ◀──── HPA       │  │
│  │   │  └─ Pod 3                        │      (min=2,    │  │
│  │   │      (porta 8080)                │       max=10)   │  │
│  │   └────────┬─────────────────────────┘                 │  │
│  │            │                                           │  │
│  │            ▼ usa                                       │  │
│  │   ┌─────────────┐  ┌──────────┐                        │  │
│  │   │ ConfigMap   │  │  Secret  │                        │  │
│  │   │ (não-secret)│  │ (DB pwd) │                        │  │
│  │   └─────────────┘  └──────────┘                        │  │
│  │                                                        │  │
│  │   ┌──────────────────────────────────┐                 │  │
│  │   │  StatefulSet (postgres)          │                 │  │
│  │   │  └─ Pod + PVC (10Gi)             │                 │  │
│  │   └──────────────────────────────────┘                 │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

---

## 📂 Estrutura do Chart

```
helm/
└── productivity-api/
    ├── Chart.yaml
    ├── values.yaml                 # defaults
    ├── values-dev.yaml             # 1 réplica, sem HPA, recursos baixos
    ├── values-staging.yaml         # 2 réplicas, HPA habilitado
    ├── values-prod.yaml            # 3 réplicas mínimo, HPA agressivo
    ├── templates/
    │   ├── _helpers.tpl            # macros reutilizáveis
    │   ├── deployment.yaml
    │   ├── service.yaml
    │   ├── ingress.yaml
    │   ├── hpa.yaml
    │   ├── configmap.yaml
    │   ├── secret.yaml
    │   ├── serviceaccount.yaml
    │   └── NOTES.txt               # mensagem pós-install
    └── charts/                     # dependências (ex.: postgres subchart)
```

---

## 🛠️ Snippets Principais

### `Chart.yaml`

```yaml
apiVersion: v2
name: productivity-api
description: Helm Chart para a Productivity API
type: application
version: 0.1.0       # versão do chart
appVersion: "0.0.1"  # versão da aplicação
maintainers:
  - name: João Victor Azevedo
    url: https://github.com/JoaoAzevedo184
```

### `values.yaml` (defaults)

```yaml
replicaCount: 2

image:
  repository: ghcr.io/joaoazevedo184/productivity-api
  tag: latest
  pullPolicy: IfNotPresent

imagePullSecrets: []

serviceAccount:
  create: true
  name: ""

service:
  type: ClusterIP
  port: 80
  targetPort: 8080

ingress:
  enabled: true
  className: nginx
  annotations: {}
  hosts:
    - host: productivity.local
      paths:
        - path: /
          pathType: Prefix

resources:
  requests:
    cpu: 250m
    memory: 384Mi
  limits:
    cpu: 1000m
    memory: 768Mi

autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70

env:
  SPRING_PROFILES_ACTIVE: prod
  JAVA_OPTS: "-Xms256m -Xmx512m"

# Configs não-secretas (vão pro ConfigMap)
config:
  serverPort: "8080"

# Secrets (DB credentials, etc.)
# IMPORTANTE: em prod, esses values vêm de Sealed Secrets ou Vault — nunca commitados!
secrets:
  DB_USER: productivity
  DB_PASSWORD: changeme
  DB_URL: jdbc:postgresql://productivity-postgres:5432/productivity

probes:
  liveness:
    path: /actuator/health/liveness
    initialDelaySeconds: 30
    periodSeconds: 30
  readiness:
    path: /actuator/health/readiness
    initialDelaySeconds: 10
    periodSeconds: 10

# Subchart do Postgres
postgresql:
  enabled: true
  auth:
    database: productivity
    username: productivity
    existingSecret: productivity-api-secret
```

### `values-dev.yaml`

```yaml
replicaCount: 1

resources:
  requests:
    cpu: 100m
    memory: 256Mi
  limits:
    cpu: 500m
    memory: 512Mi

autoscaling:
  enabled: false

env:
  SPRING_PROFILES_ACTIVE: dev
```

### `values-prod.yaml`

```yaml
replicaCount: 3

resources:
  requests:
    cpu: 500m
    memory: 512Mi
  limits:
    cpu: 2000m
    memory: 1Gi

autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 20
  targetCPUUtilizationPercentage: 60

ingress:
  hosts:
    - host: api.productivity.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: productivity-api-tls
      hosts:
        - api.productivity.example.com
```

### `templates/deployment.yaml` (trecho)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "productivity-api.fullname" . }}
  labels:
    {{- include "productivity-api.labels" . | nindent 4 }}
spec:
  {{- if not .Values.autoscaling.enabled }}
  replicas: {{ .Values.replicaCount }}
  {{- end }}
  selector:
    matchLabels:
      {{- include "productivity-api.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "productivity-api.selectorLabels" . | nindent 8 }}
    spec:
      serviceAccountName: {{ include "productivity-api.serviceAccountName" . }}
      containers:
        - name: {{ .Chart.Name }}
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - name: http
              containerPort: {{ .Values.service.targetPort }}
              protocol: TCP
          livenessProbe:
            httpGet:
              path: {{ .Values.probes.liveness.path }}
              port: http
            initialDelaySeconds: {{ .Values.probes.liveness.initialDelaySeconds }}
            periodSeconds: {{ .Values.probes.liveness.periodSeconds }}
          readinessProbe:
            httpGet:
              path: {{ .Values.probes.readiness.path }}
              port: http
            initialDelaySeconds: {{ .Values.probes.readiness.initialDelaySeconds }}
            periodSeconds: {{ .Values.probes.readiness.periodSeconds }}
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
          envFrom:
            - configMapRef:
                name: {{ include "productivity-api.fullname" . }}-config
            - secretRef:
                name: {{ include "productivity-api.fullname" . }}-secret
```

---

## 🎬 Roteiro de Comandos (Live Demo)

### Pré-requisitos: cluster local com Kind

```bash
# Instalar Kind (uma vez)
go install sigs.k8s.io/kind@latest
# Ou: brew install kind / curl -Lo ./kind ...

# Criar cluster
kind create cluster --name productivity --config kind-config.yaml

# kind-config.yaml expõe portas 80/443 para o Ingress funcionar:
cat <<EOF > kind-config.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 80
        hostPort: 80
      - containerPort: 443
        hostPort: 443
EOF

# Instalar Ingress NGINX
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
```

### 1. Validar e renderizar (sem aplicar)

```bash
# Validar sintaxe e estrutura
helm lint ./helm/productivity-api

# Renderizar templates pra ver o YAML final
helm template productivity ./helm/productivity-api \
  -f helm/productivity-api/values-dev.yaml \
  --debug | less
```

### 2. Instalar em dev

```bash
helm install productivity ./helm/productivity-api \
  --namespace productivity-dev \
  --create-namespace \
  -f helm/productivity-api/values-dev.yaml

# Acompanhar pods subindo
kubectl get pods -n productivity-dev -w
```

### 3. Verificar status

```bash
helm status productivity -n productivity-dev

helm list -n productivity-dev
# NAME          NAMESPACE          REVISION    STATUS      CHART
# productivity  productivity-dev   1           deployed    productivity-api-0.1.0

kubectl get all -n productivity-dev
```

### 4. Testar a API

```bash
# Adicionar entrada no /etc/hosts
echo "127.0.0.1 productivity.local" | sudo tee -a /etc/hosts

# Testar
curl http://productivity.local/tasks
curl http://productivity.local/actuator/health
```

### 5. Upgrade com mudança de configuração

```bash
# Aumentar réplicas
helm upgrade productivity ./helm/productivity-api \
  --namespace productivity-dev \
  -f helm/productivity-api/values-dev.yaml \
  --set replicaCount=3

# Ver os pods escalando
kubectl get pods -n productivity-dev -w
```

### 6. Ver histórico

```bash
helm history productivity -n productivity-dev
# REVISION   STATUS      CHART                    DESCRIPTION
# 1          superseded  productivity-api-0.1.0   Install complete
# 2          deployed    productivity-api-0.1.0   Upgrade complete
```

### 7. Demonstrar rollback

```bash
# Provocar um deploy ruim (imagem inexistente)
helm upgrade productivity ./helm/productivity-api \
  --namespace productivity-dev \
  -f helm/productivity-api/values-dev.yaml \
  --set image.tag=versao-inexistente

# Ver os pods falhando
kubectl get pods -n productivity-dev
# productivity-api-xxx   0/1   ImagePullBackOff   ...

# Rollback rápido pra revisão anterior
helm rollback productivity 2 -n productivity-dev

# Ver os pods voltando
kubectl get pods -n productivity-dev -w
```

> Esse é **o momento mais impressionante da demo**. Rollback em < 30 segundos, sem precisar abrir o código, sem reverter commit, sem rebuildar imagem.

### 8. Cleanup

```bash
helm uninstall productivity -n productivity-dev
kubectl delete namespace productivity-dev

# Ou destruir o cluster inteiro:
kind delete cluster --name productivity
```

---

## 📊 O Que Mostrar no Dashboard

Acessar o **Kubernetes Dashboard** ou usar `kubectl` direto:

```bash
# Instalar o dashboard (uma vez)
kubectl apply -f https://raw.githubusercontent.com/kubernetes/dashboard/v2.7.0/aio/deploy/recommended.yaml

# Acessar via proxy
kubectl proxy
# Abre em: http://localhost:8001/api/v1/namespaces/kubernetes-dashboard/...
```

**O que destacar:**

- Pods em estado **Running** (3 réplicas após upgrade).
- Service do tipo **ClusterIP** roteando para os pods.
- Ingress fazendo o roteamento HTTP externo.
- HPA mostrando métricas de CPU em tempo real.
- Logs de cada pod (Spring Boot startando, JPA conectando ao Postgres).

---

## ✅ Critérios de Avaliação

Conforme o enunciado:

| Eixo | Como vou atender |
|---|---|
| **Trabalho em equipe** | N/A solo; documento o processo |
| **Charts personalizados** | Chart próprio com 3 values files (dev/staging/prod) |
| **Apresentação** | Slides + live demo (foco no rollback) |
| **Relatório técnico** | Este arquivo + diagrama + logs/screenshots |

---

## 📌 Status e Próximos Passos

**Concluído:**

- [ ] Nada ainda — depende do [Desafio 02](challenge-02.md) (imagem Docker).

**A fazer (ordem):**

1. Finalizar Dockerfile no Desafio 02 e publicar imagem no GHCR.
2. Instalar Kind localmente.
3. `helm create` e customizar templates.
4. Validar `helm lint` + `helm template`.
5. Instalar no Kind, validar todos os recursos.
6. Capturar logs/screenshots da demo de rollback.
7. Preparar slides.

---

## 📚 Referências

- [Helm Docs](https://helm.sh/docs/)
- [Helm Best Practices](https://helm.sh/docs/chart_best_practices/)
- [Kind — Kubernetes in Docker](https://kind.sigs.k8s.io/)
- [Kubernetes — Probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)
- [HPA — Horizontal Pod Autoscaler](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/)

> 💡 **Lição transversal:** Helm é "Maven do Kubernetes" — não inventa nada novo, só empacota o que você já fazia com `kubectl apply` em algo versionado, parametrizado e re-deployável. O Helm Chart é **infraestrutura como código de verdade**.