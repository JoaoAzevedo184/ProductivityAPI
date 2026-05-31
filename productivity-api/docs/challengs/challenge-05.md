# 🌐 Desafio 05 (Final) — Observabilidade 360°

> **Arquitetura completa de microsserviços resilientes**, consolidando os desafios anteriores: CI/CD + Docker + Helm + logs centralizados + métricas + alertas. A productivity-api roda em Kubernetes (Kind), observável ponta a ponta.

| Campo | Valor |
|---|---|
| **Status** | 🟡 Implementado (resta capturar evidências + slides) |
| **Aplicação-base** | productivity-api |
| **Pilares** | Automação · Orquestração · Observabilidade · Resiliência |
| **Modo** | Solo |

---

## 🎯 Visão Geral

Este desafio **integra** os anteriores num único sistema funcionando:

| Pilar | Vem do desafio | Tecnologia |
|---|---|---|
| Automação (CI/CD) | [01](challenge-01.md) | GitHub Actions |
| Containerização | [02](challenge-02.md) | Docker multi-stage |
| Orquestração | [03](challenge-03.md) | Kubernetes + Helm |
| Logging centralizado | [04](challenge-04.md) | Syslog-ng |
| **Métricas** ⭐ | **Novo** | Prometheus + Grafana |
| **Alertas** ⭐ | **Novo** | Alertmanager |

---

## ✅ O que foi entregue

### 1. Instrumentação da aplicação (Micrometer)

Métricas automáticas (HTTP, JVM, HikariCP) via `micrometer-registry-prometheus` expostas em `/actuator/prometheus`, mais **duas métricas de negócio** adicionadas ao `TaskService`:

- `tasks_created_total{priority,status}` — contador de tarefas criadas, rotulado por prioridade e status. Permite responder, por exemplo, "quantas tarefas HIGH são criadas por hora?".
- `tasks_completion_duration_seconds` — `Timer` com histograma de percentis (`publishPercentileHistogram()`), medindo o tempo entre criação e conclusão, registrado apenas na transição real para `COMPLETED`. Os buckets `_bucket` gerados alimentam o `histogram_quantile()` no Prometheus.

Tags globais `application` e `env` aplicadas a **todas** as métricas via `management.metrics.tags`, o que diferencia ambientes (dev/staging/prod) no Grafana e faz as queries `{application="productivity-api"}` casarem.

> ⚠️ **Impacto nos testes:** o construtor do `TaskService` passou a receber `MeterRegistry`. O `TaskServiceTest` precisa instanciar o service com um `SimpleMeterRegistry` (registry real, em memória) em vez de depender apenas de `@InjectMocks` com o repositório. Sem esse ajuste, `./mvnw verify` quebra e o CI bloqueia o deploy.

### 2. ServiceMonitor no Helm Chart

Novo template `templates/servicemonitor.yaml` (condicional a `serviceMonitor.enabled`). Aponta o Prometheus para `/actuator/prometheus` na porta nomeada `http` do Service.

**Decisão crítica:** o ServiceMonitor carrega a label `release: kube-prom-stack`. O kube-prometheus-stack configura o Prometheus para coletar apenas ServiceMonitors com essa label. Sem ela, o scrape **nunca acontece e não há erro** — falha silenciosa.

### 3. Stack de observabilidade enxuta para Kind

`observability/prometheus-values.yaml` — `kube-prometheus-stack` ajustado para hardware modesto:

- Retention 3d, sem PVC (emptyDir) — suficiente para demo, não depende do storage do Kind.
- Componentes de control-plane que só geram ruído no Kind desligados (`kubeScheduler`, `kubeControllerManager`, `kubeProxy`, `kubeEtcd`).
- `node-exporter` e `kube-state-metrics` mantidos (baratos e necessários para métricas USE).
- `serviceMonitorSelectorNilUsesHelmValues: false` + selectors vazios → Prometheus enxerga ServiceMonitors/Rules de **qualquer** namespace (a app fica em `productivity-dev`, o stack em `observability`).

### 4. Alertas baseados em SLO

`observability/prometheus-rules.yaml` (`PrometheusRule`):

| Alerta | Condição | Severidade |
|---|---|---|
| `HighErrorRate` | 5xx > 1% por 5m | warning |
| `HighLatencyP95` | p95 > 500ms por 10m | warning |
| `PodCrashLooping` | restarts > 0 em 15m | critical |
| `HikariPoolNearExhaustion` | conexões ativas/máx > 90% | warning |
| `ProductivityApiDown` | `up == 0` por 2m | critical |

### 5. Dashboard Grafana (RED + USE + Negócio)

`observability/grafana-dashboards/productivity-api.json`, provisionado via ConfigMap com label `grafana_dashboard=1`:

- **RED:** rate por URI, % de erro 5xx, latência p50/p95/p99.
- **USE:** heap JVM (%), CPU do processo, pool HikariCP.
- **Negócio:** taxa de criação por prioridade, tempo de conclusão p50/p90/p99.

---

## 🎬 Como subir (ordem importa)

```bash
# Pré-requisito: cluster Kind "productivity" criado e app já instalada
# em productivity-dev (ver Desafios 02/03).

# Sobe tudo na ordem correta (stack -> rules -> dashboard -> religa o chart)
./scripts/observability-up.sh
```

O script existe porque a ordem é a armadilha principal: o CRD `ServiceMonitor`
só passa a existir **depois** que o kube-prometheus-stack instala o Operator.
Aplicar o chart da app com `serviceMonitor.enabled=true` antes disso falha com
`no matches for kind "ServiceMonitor"`.

### Acessos

```bash
# Grafana (admin/admin) — NodePort 30000, ou via port-forward:
kubectl port-forward -n observability svc/kube-prom-stack-grafana 3000:80

# Prometheus — conferir Status -> Targets -> productivity-api deve estar "up"
kubectl port-forward -n observability svc/kube-prom-stack-kube-prome-prometheus 9090:9090

# Alertmanager
kubectl port-forward -n observability svc/kube-prom-stack-kube-prome-alertmanager 9093:9093
```

### Gerar carga para a demo

```bash
# hey, ou um loop simples de curl
hey -z 60s -c 10 http://localhost:8080/tasks
```

---

## 💡 Decisões aplicadas

| Decisão | Por quê |
|---|---|
| `SimpleMeterRegistry` no teste em vez de mock | Evita stubar a cadeia fluente `builder().register()`; exercita a métrica de verdade |
| Label `release: kube-prom-stack` no ServiceMonitor/Rule | É o seletor padrão do stack; sem ela o scrape é ignorado silenciosamente |
| Selectors vazios no Prometheus | App e stack em namespaces distintos; sem isso a coleta cross-namespace não ocorre |
| Stack enxuta (sem control-plane scrapes, retention curta) | Kind single-node em hardware modesto; o desafio observa a APP, não o Kind |
| emptyDir no Prometheus (sem PVC) | Demo não precisa persistência; evita depender do provisionador de storage |
| `serviceMonitor.enabled: false` no `values.yaml` base | CRD não existe sem o Operator; default seguro, cada ambiente liga via override |
| `publishPercentileHistogram()` no Timer | Gera buckets `_bucket` para `histogram_quantile()` |

---

## ✅ Critérios de Avaliação

| Critério | Peso | Como atendo |
|---|---|---|
| Automação (CI/CD) | 2.5 | Pipeline dos Desafios 01 (ci.yml + cd.yml) |
| Orquestração (Helm) | 2.5 | Chart do Desafio 03 + ServiceMonitor |
| Observabilidade | 2.5 | Métricas + dashboard + 5 alertas + logs (Desafio 04) |
| Apresentação | 2.5 | Live demo (carga → painéis subindo → alerta disparando) |

---

## 📌 O que ainda falta (entrega)

- [ ] Capturar screenshots: Prometheus Targets (`up`), dashboard sob carga, alerta `HighErrorRate`/`PodCrashLooping` disparado.
- [ ] Gravar/ensaiar a live demo (≤ 10 min).
- [ ] Slides (8-10, foco visual).
- [ ] (opcional) Configurar receiver real no Alertmanager (Slack/email).
- [ ] (opcional) Correlacionar logs (Loki) + métricas no Grafana.

---

## 📂 Arquivos desta entrega

```
productivity-api/
├── src/main/java/.../service/TaskService.java   # instrumentado (commit feat(metrics))
├── src/test/java/.../service/TaskServiceTest.java # ajustar (SimpleMeterRegistry)
├── pom.xml                                       # + micrometer-registry-prometheus
├── src/main/resources/application*.yml           # + prometheus + tags globais
├── helm/productivity-api/
│   ├── templates/servicemonitor.yaml             # ⭐ novo
│   ├── values.yaml                               # + bloco serviceMonitor
│   └── values-dev.yaml                           # serviceMonitor.enabled: true
├── observability/                                # ⭐ novo
│   ├── prometheus-values.yaml
│   ├── prometheus-rules.yaml
│   └── grafana-dashboards/productivity-api.json
└── scripts/observability-up.sh                   # ⭐ novo

(scripts/observability-up.sh fica na RAIZ do repo, junto de deploy-local.sh)
```

> 💡 **Lição transversal:** observabilidade não é "adicionar Grafana". É instrumentar a aplicação para responder, a qualquer momento: *"está funcionando?"*, *"como está se comportando?"* e *"o que aconteceu lá atrás?"*. A maior parte do trabalho real foi o encanamento silencioso — labels que precisam casar, ordem de criação de CRDs, selectors cross-namespace — coisas que não dão erro, só não funcionam.