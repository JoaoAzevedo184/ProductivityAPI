# ⛵ Desafio 03 — Deploy Orquestrado com Helm no Kubernetes

> **Empacotamento da productivity-api em um Helm Chart parametrizado**, com deploys em múltiplos ambientes (dev/staging/prod), probes do Kubernetes e demonstração de upgrade/rollback sem downtime.

| Campo | Valor |
|---|---|
| **Status** | ✅ **Concluído** (resta apenas documentação visual / slides) |
| **Aplicação-base** | productivity-api (imagem do [Desafio 02](challenge-02.md)) |
| **Ferramentas** | Kubernetes (Kind) + Helm 3 |
| **Modo** | Solo |

---

## 🎯 Objetivos

1. ✅ Criar Helm Chart customizado para a productivity-api.
2. ✅ Suportar múltiplos ambientes via `values-dev.yaml`, `values-staging.yaml`, `values-prod.yaml`.
3. ✅ Demonstrar `install`, `upgrade`, `history`, `rollback`.
4. ✅ Configurar HPA (autoscaling), probes e recursos por ambiente.
5. 🔲 Preparar apresentação (slides + roteiro de demo).

---

## 🏛️ Arquitetura no Kubernetes

```
┌──────────────────────────────────────────────────────────────┐
│                  Cluster Kubernetes (Kind)                   │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  Namespace: productivity-dev                           │  │
│  │                                                        │  │
│  │   ┌─────────────┐                                      │  │
│  │   │   Service   │ (ClusterIP, porta 80)                │  │
│  │   └──────┬──────┘                                      │  │
│  │          ▼                                             │  │
│  │   ┌──────────────────────────────────┐                 │  │
│  │   │  Deployment (productivity-api)   │                 │  │
│  │   │  └─ Pod (1 replica em dev)       │                 │  │
│  │   │     ├─ startupProbe              │                 │  │
│  │   │     ├─ livenessProbe             │                 │  │
│  │   │     └─ readinessProbe            │                 │  │
│  │   └────────┬─────────────────────────┘                 │  │
│  │            │                                           │  │
│  │            ▼ usa                                       │  │
│  │   ┌─────────────┐  ┌──────────┐                        │  │
│  │   │ ConfigMap   │  │  Secret  │ (postgres-credentials) │  │
│  │   │ (DB_URL,    │  │  DB_USER,│ — criado fora do chart │  │
│  │   │  profile)   │  │  DB_PWD  │                        │  │
│  │   └─────────────┘  └──────────┘                        │  │
│  │                                                        │  │
│  │   ┌──────────────────────────────────┐                 │  │
│  │   │  StatefulSet postgres            │                 │  │
│  │   │  └─ Pod + PVC (2Gi)              │                 │  │
│  │   └──────────────────────────────────┘                 │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

Em staging/prod, mais componentes são adicionados **automaticamente** pelos values files: `HorizontalPodAutoscaler`, `Ingress`, mais réplicas, recursos maiores.

---

## ✅ O que foi entregue

### 1. Estrutura completa do chart

```
helm/productivity-api/
├── Chart.yaml                    # versão do chart + appVersion separados
├── values.yaml                   # defaults
├── values-dev.yaml               # 1 réplica, sem HPA, sem Ingress
├── values-staging.yaml           # 2 réplicas + HPA + Ingress sem TLS
├── values-prod.yaml              # 3 réplicas + HPA agressivo + Ingress + TLS
└── templates/
    ├── _helpers.tpl              # macros (fullname, labels, selectorLabels)
    ├── configmap.yaml            # configs não-secretas
    ├── deployment.yaml           # com 3 probes + HPA-aware
    ├── service.yaml              # ClusterIP
    ├── hpa.yaml                  # condicional (renderiza só se autoscaling.enabled)
    └── ingress.yaml              # condicional (renderiza só se ingress.enabled)
```

### 2. Probes do Kubernetes (a parte mais importante)

**Três probes apontando pro Spring Boot Actuator**:

| Probe | Endpoint | Função | Configuração em dev |
|---|---|---|---|
| `startupProbe` | `/actuator/health` | "Ainda tá subindo?" — protege liveness/readiness durante o boot | `failureThreshold: 40` × `period: 5s` = 200s máx de boot |
| `livenessProbe` | `/actuator/health/liveness` | "Ainda tá vivo?" — se falhar, K8s **mata e reinicia** o pod | `period: 10s`, `failureThreshold: 3` |
| `readinessProbe` | `/actuator/health/readiness` | "Tá pronto pra tráfego?" — se falhar, K8s tira do Service | `period: 5s`, `failureThreshold: 3` |

**Por que `startupProbe` é crítica:** Spring Boot demora 30-60s pra subir. Sem startup probe, a `livenessProbe` mataria o pod antes da JVM terminar de inicializar — clássico `CrashLoopBackOff`. Com startup, K8s só começa a checar liveness depois que startup passou pelo menos uma vez.

**Configuração explícita no `application.yml`**:
```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true   # habilita /actuator/health/liveness e /readiness
```

### 3. Values por ambiente

| Característica | dev | staging | prod |
|---|---|---|---|
| Réplicas | 1 | 2 (HPA: 2-5) | 3 (HPA: 3-10) |
| HPA | ❌ desligado | ✅ CPU 70% | ✅ CPU 60% |
| Ingress | ❌ desligado | ✅ sem TLS | ✅ com TLS |
| `pullPolicy` | `IfNotPresent` | `Always` | `IfNotPresent` (tag imutável) |
| CPU request/limit | 100m / 500m | 250m / 1000m | 500m / 2000m |
| Memory request/limit | 256Mi / 512Mi | 384Mi / 768Mi | 512Mi / 1Gi |
| `JAVA_OPTS` heap | -Xmx256m | -Xmx512m | -Xmx1024m |
| Profile Spring | `dev` (H2) | `prod` (Postgres) | `prod` (Postgres) |

### 4. Templates condicionais

**`deployment.yaml`** — não fixa `replicas` quando HPA está ativo:
```yaml
{{- if not .Values.autoscaling.enabled }}
replicas: {{ .Values.replicaCount }}
{{- end }}
```
Sem isso, HPA e Deployment brigam pelo número de pods.

**`hpa.yaml`** e **`ingress.yaml`** — só renderizam se a feature estiver habilitada:
```yaml
{{- if .Values.autoscaling.enabled }}
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
...
```

### 5. `checksum/config` annotation

```yaml
template:
  metadata:
    annotations:
      checksum/config: {{ include (...) . | sha256sum }}
```

Sem isso, mudar o ConfigMap **não** dispara rolling update — K8s só observa mudanças no spec do pod. A annotation força o spec a mudar quando o ConfigMap muda.

---

## 🎬 Comandos executados (logs reais da demo)

### Setup do cluster

```bash
# Criar cluster Kind
kind create cluster --name productivity --config k8s/kind-config.yaml

# Aplicar manifests fora do chart (namespace, secret, postgres)
kubectl apply -f k8s/manifests/

# Verificar
kubectl get ns | grep productivity
# productivity-dev     Active   4d13h
```

### Validar e renderizar o chart

```bash
helm lint ./helm/productivity-api

# Renderizar sem aplicar — confere YAML final por ambiente
helm template productivity ./helm/productivity-api \
  -f helm/productivity-api/values.yaml \
  -f helm/productivity-api/values-dev.yaml

# Confirmar diferenças por ambiente — em staging aparecem HPA e Ingress
helm template productivity ./helm/productivity-api \
  -f helm/productivity-api/values.yaml \
  -f helm/productivity-api/values-staging.yaml | grep "^kind:"
# kind: ConfigMap
# kind: Service
# kind: Deployment
# kind: HorizontalPodAutoscaler   ← só em staging/prod
# kind: Ingress                   ← só em staging/prod
```

### Carregar imagem no Kind e instalar

```bash
# Imagem buildada no Docker do host precisa ser carregada no Kind
docker build -t productivity-api:local productivity-api/
kind load docker-image productivity-api:local --name productivity

# Instalar
helm install productivity ./helm/productivity-api \
  -f helm/productivity-api/values.yaml \
  -f helm/productivity-api/values-dev.yaml \
  --namespace productivity-dev \
  --create-namespace
```

### Verificar status

```bash
helm list -A
# NAME           NAMESPACE          REVISION   STATUS      CHART                    APP VERSION
# productivity   productivity-dev   1          deployed    productivity-api-0.1.0   0.0.1

kubectl get pods -n productivity-dev
# NAME                                             READY   STATUS    RESTARTS   AGE
# postgres-0                                       1/1     Running   0          5m
# productivity-productivity-api-85f7875789-kqc8r   1/1     Running   0          2m

# Confirmar que as 3 probes estão configuradas
kubectl describe pod -n productivity-dev -l app.kubernetes.io/name=productivity-api \
  | grep -A 3 -E "Liveness|Readiness|Startup"
# Liveness:   http-get http://:http/actuator/health/liveness delay=0s timeout=3s period=10s
# Readiness:  http-get http://:http/actuator/health/readiness delay=0s timeout=3s period=5s
# Startup:    http-get http://:http/actuator/health delay=0s timeout=1s period=5s #failure=40

# Confirmar que HPA e Ingress NÃO existem em dev (parametrização funcionando)
kubectl get hpa,ingress -n productivity-dev
# No resources found in productivity-dev namespace.
```

### Rolling update sem downtime (demonstrado)

Quando o `application.yml` mudou para habilitar probes explicitamente, foi feito um `helm upgrade`. Log capturado durante o processo:

```
NAME                                             READY   STATUS              AGE
productivity-productivity-api-6777859c4b-nb66p   1/1     Running             3d23h   ← versão antiga, viva
productivity-productivity-api-85f7875789-kqc8r   0/1     ContainerCreating   2s
productivity-productivity-api-85f7875789-kqc8r   0/1     Running             32s     ← subindo
productivity-productivity-api-85f7875789-kqc8r   1/1     Running             41s     ← passou na readiness probe!
productivity-productivity-api-6777859c4b-nb66p   1/1     Terminating         3d23h   ← SÓ AGORA o velho morre
```

**Resultado:** zero downtime. O pod velho continuou respondendo até o novo passar na readiness probe, exatamente como o K8s deve se comportar com probes corretas.

### Demo de rollback (a demonstração mais valiosa)

**Passo 1 — provocar deploy quebrado (image tag inexistente):**

```bash
helm upgrade productivity ./helm/productivity-api \
  -f helm/productivity-api/values.yaml \
  -f helm/productivity-api/values-dev.yaml \
  --set image.tag=versao-que-nao-existe \
  --namespace productivity-dev

# Release "productivity" has been upgraded. Happy Helming!
# REVISION: 3
```

**Passo 2 — desastre acontece, mas usuário não percebe:**

```
NAME                                             READY   STATUS             AGE
productivity-productivity-api-65dc47b6fb-gb6pt   0/1     ImagePullBackOff   21s   ← novo, quebrado
productivity-productivity-api-85f7875789-kqc8r   1/1     Running            6m37s ← antigo, viva!
```

Pod novo entrou em `ImagePullBackOff`. Pod antigo continuou `1/1 Running`. **A API permaneceu respondendo o tempo todo** — readiness probe do novo nunca passou, então K8s não removeu o velho.

**Passo 3 — conferir histórico:**

```bash
helm history productivity -n productivity-dev
# REVISION   UPDATED                     STATUS      CHART                    DESCRIPTION
# 1          Thu May 14 22:20:29 2026    superseded  productivity-api-0.1.0   Install complete
# 2          Mon May 18 21:39:24 2026    superseded  productivity-api-0.1.0   Upgrade complete   ← versão boa
# 3          Mon May 18 21:45:39 2026    deployed    productivity-api-0.1.0   Upgrade complete   ← quebrada
```

**Passo 4 — rollback:**

```bash
helm rollback productivity 2 -n productivity-dev
# Rollback was a success! Happy Helming!

helm history productivity -n productivity-dev
# 4          Mon May 18 21:47:08 2026    deployed    productivity-api-0.1.0   Rollback to 2

kubectl get pods -n productivity-dev
# NAME                                             READY   STATUS    AGE
# postgres-0                                       1/1     Running   4d13h
# productivity-productivity-api-85f7875789-kqc8r   1/1     Running   8m13s   ← MESMO pod, MESMO hash, mesma vida
```

**MTTR (Mean Time to Recovery) medido:**
- Deploy quebrado às 21:45:39
- Rollback bem-sucedido às 21:47:08
- **1 minuto e 29 segundos** entre o desastre e a recuperação
- **Zero requisições perdidas**

---

## 💡 Decisões aplicadas

| Decisão | Por quê |
|---|---|
| `Chart.yaml` com `version` e `appVersion` separados | Versionar chart e app independentemente |
| Secret `postgres-credentials` existente (não criado pelo chart) | Desacopla credenciais do release; em prod viria de Vault |
| Três probes (startup + liveness + readiness) | Spring Boot demora a subir; sem startup, CrashLoopBackOff |
| `replicas` condicional ao `autoscaling.enabled` | Evita HPA e Deployment brigando pelo número de pods |
| `checksum/config` annotation | Força rolling update quando ConfigMap muda |
| Templates `hpa.yaml`/`ingress.yaml` condicionais | Renderização limpa: dev sem HPA é dev sem HPA |
| `pullPolicy: Always` em staging vs. `IfNotPresent` em prod | Staging = tag mutável `staging`; prod = tag imutável `sha-xxx` |
| `failureThreshold` por ambiente | Dev tolera 200s pra subir; prod só 100s (sintoma de problema) |
| Macros em `_helpers.tpl` | DRY; labels e fullname consistentes em todos os templates |

---

## 📊 Critérios de avaliação atendidos

| Eixo do enunciado | Status | Evidência |
|---|---|---|
| Trabalho em equipe | N/A | Modo solo; processo documentado |
| **Charts personalizados** | ✅ | Chart próprio, 3 values files, templates condicionais |
| **Apresentação** | 🔲 | Live demo pronta (rollback < 2 min); slides pendentes |
| **Relatório técnico** | ✅ | Este arquivo + logs reais capturados |

---

## 📌 O que ainda falta

- 🔲 Capturar screenshots da demo (helm history, kubectl get pods durante rollback)
- 🔲 Montar slides (5-8 páginas, foco em demo)
- 🔲 (opcional) Adicionar `templates/NOTES.txt` com instruções pós-install
- 🔲 (opcional) Empacotar o Postgres como subchart ou Bitnami chart, em vez de manifests `kubectl apply` separados

---

## 📚 Referências

- [Helm Docs](https://helm.sh/docs/)
- [Helm Best Practices](https://helm.sh/docs/chart_best_practices/)
- [Kind — Kubernetes in Docker](https://kind.sigs.k8s.io/)
- [Kubernetes — Probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)
- [Spring Boot — Kubernetes Probes](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.endpoints.kubernetes-probes)
- [HPA — Horizontal Pod Autoscaler](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/)

> 💡 **Lição transversal:** Helm é "Maven do Kubernetes" — não inventa nada novo, só empacota o que você já fazia com `kubectl apply` em algo versionado, parametrizado e re-deployável. O Helm Chart é **infraestrutura como código de verdade**.
>
> 💡 **Lição da demo:** o que diferencia "rodar no Kubernetes" de "operar no Kubernetes" é **rollback em < 2 minutos sem downtime**. Probes corretas tornam isso automático — não foi um milagre, foi o comportamento esperado do sistema bem configurado.