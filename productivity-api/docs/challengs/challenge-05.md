# 🌐 Desafio 05 (Final) — Observabilidade 360°

> **Arquitetura completa de microsserviços resilientes**, consolidando os desafios anteriores: CI/CD + Docker + Helm + logs centralizados + métricas + alertas. A productivity-api roda em Kubernetes, observável ponta a ponta.

| Campo | Valor |
|---|---|
| **Status** | 🔲 Planejado (depende dos 4 anteriores) |
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
| Logging centralizado | [04](challenge-04.md) | Syslog-ng (ou Loki) |
| **Métricas** ⭐ | **Novo** | Prometheus + Grafana |
| **Alertas** ⭐ | **Novo** | Alertmanager |

⭐ = adicionado neste desafio.

---

## 🏛️ Arquitetura Consolidada

```
                              ┌─────────────────┐
                              │  Desenvolvedor  │
                              └────────┬────────┘
                                       │ git push
                                       ▼
                           ┌──────────────────────┐
                           │   GitHub Actions     │ ── Desafio 01
                           │   build · test · push│
                           └──────────┬───────────┘
                                      │ helm upgrade
                                      ▼
   ┌────────────────────────────────────────────────────────────────┐
   │                       Kubernetes Cluster                       │
   │                                                                │
   │  ┌─────────────────────────────────────────────────────────┐   │
   │  │              Namespace: productivity                    │   │
   │  │                                                         │   │
   │  │   ┌──────────┐  ┌──────────┐  ┌──────────┐              │   │
   │  │   │  Pod 1   │  │  Pod 2   │  │  Pod N   │ ◀── HPA      │   │
   │  │   │  /metrics│  │  /metrics│  │  /metrics│              │   │
   │  │   └────┬─────┘  └────┬─────┘  └────┬─────┘              │   │
   │  │        │             │             │                    │   │
   │  └────────┼─────────────┼─────────────┼────────────────────┘   │
   │           │             │             │                        │
   │           ▼             ▼             ▼                        │
   │  ┌──────────────────┐         ┌────────────────┐               │
   │  │   Syslog-ng /    │         │   Prometheus   │               │
   │  │   Loki           │         │   (scrape)     │               │
   │  │   (logs)         │         └────────┬───────┘               │
   │  └────────┬─────────┘                  │                       │
   │           │                            ▼                       │
   │           │                   ┌────────────────┐               │
   │           │                   │     Grafana    │ ── Dashboards │
   │           │                   └────────┬───────┘               │
   │           │                            │                       │
   │           ▼                            ▼                       │
   │  ┌──────────────────────────────────────────┐                  │
   │  │      Stack de Observabilidade            │                  │
   │  │  logs + métricas correlacionáveis        │                  │
   │  └──────────────────────────────────────────┘                  │
   │                                                                │
   │                            ┌────────────────┐                  │
   │                            │  Alertmanager  │ ── 🔔 Slack/Email│
   │                            └────────────────┘                  │
   └────────────────────────────────────────────────────────────────┘
```

---

## 🛠️ Implementação

### Etapa 1 — Métricas na aplicação

Adicionar Spring Actuator + Micrometer com exporter Prometheus.

**`pom.xml`**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
    <scope>runtime</scope>
</dependency>
```

**`application.yml`**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  metrics:
    tags:
      application: ${spring.application.name}
      env: ${SPRING_PROFILES_ACTIVE:dev}
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when-authorized
```

**Endpoint:** `GET /actuator/prometheus` retorna métricas em formato OpenMetrics.

### Métricas customizadas

Além das métricas automáticas (HTTP, JVM, hikari), adicionar contadores específicos do domínio:

```java
@Service
public class TaskService {

    private final Counter tasksCreatedCounter;
    private final Timer taskCompletionTimer;

    public TaskService(TaskRepository taskRepository, MeterRegistry meterRegistry) {
        this.taskRepository = taskRepository;
        this.tasksCreatedCounter = Counter.builder("tasks_created_total")
                .description("Total de tarefas criadas")
                .tag("application", "productivity-api")
                .register(meterRegistry);
        this.taskCompletionTimer = Timer.builder("task_completion_duration_seconds")
                .description("Tempo desde criação até conclusão")
                .register(meterRegistry);
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        Task task = TaskMapper.toEntity(request);
        Task saved = taskRepository.save(task);
        tasksCreatedCounter.increment();
        return TaskMapper.toResponse(saved);
    }

    @Transactional
    public TaskResponse update(Long id, UpdateTaskRequest request) {
        // ... lógica existente
        if (request.status() == TaskStatus.COMPLETED && statusAnterior != TaskStatus.COMPLETED) {
            Duration duration = Duration.between(task.getCreatedAt(), LocalDateTime.now());
            taskCompletionTimer.record(duration);
        }
        // ...
    }
}
```

---

### Etapa 2 — Prometheus no cluster

Usar o chart oficial **kube-prometheus-stack** (instala Prometheus + Grafana + Alertmanager + node-exporter + kube-state-metrics de uma vez):

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install kube-prom-stack prometheus-community/kube-prometheus-stack \
  --namespace observability \
  --create-namespace \
  --values observability/prometheus-values.yaml
```

**`observability/prometheus-values.yaml`** (snippet):

```yaml
prometheus:
  prometheusSpec:
    serviceMonitorSelectorNilUsesHelmValues: false
    serviceMonitorSelector:
      matchLabels:
        release: kube-prom-stack
    retention: 15d
    resources:
      requests:
        cpu: 200m
        memory: 512Mi

grafana:
  adminPassword: changeme   # alterar via Secret em prod
  defaultDashboardsEnabled: true
  service:
    type: NodePort
    nodePort: 30000

alertmanager:
  config:
    receivers:
      - name: 'default'
      # Em prod: configurar Slack/email/PagerDuty
```

### Etapa 3 — `ServiceMonitor` para a productivity-api

Adicionar ao Helm Chart do app um `ServiceMonitor` para o Prometheus saber onde fazer scrape:

**`helm/productivity-api/templates/servicemonitor.yaml`**

```yaml
{{- if .Values.serviceMonitor.enabled }}
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: {{ include "productivity-api.fullname" . }}
  labels:
    {{- include "productivity-api.labels" . | nindent 4 }}
    release: kube-prom-stack
spec:
  selector:
    matchLabels:
      {{- include "productivity-api.selectorLabels" . | nindent 6 }}
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
{{- end }}
```

E adicionar ao `values.yaml`:

```yaml
serviceMonitor:
  enabled: true
```

---

### Etapa 4 — Dashboard Grafana

#### Métricas-chave a expor (RED + USE)

Modelo **RED** (Rate, Errors, Duration) para o serviço HTTP:

| Métrica | PromQL |
|---|---|
| **Rate** — req/s | `sum(rate(http_server_requests_seconds_count{application="productivity-api"}[5m])) by (uri, method)` |
| **Errors** — 5xx % | `sum(rate(http_server_requests_seconds_count{status=~"5..",application="productivity-api"}[5m])) / sum(rate(http_server_requests_seconds_count{application="productivity-api"}[5m])) * 100` |
| **Duration** — p95 | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="productivity-api"}[5m])) by (le, uri))` |

Modelo **USE** (Utilization, Saturation, Errors) para recursos:

| Recurso | Métrica |
|---|---|
| CPU | `rate(process_cpu_seconds_total[5m])` |
| Memória JVM | `jvm_memory_used_bytes / jvm_memory_max_bytes * 100` |
| HikariCP pool | `hikaricp_connections_active / hikaricp_connections_max * 100` |
| GC | `rate(jvm_gc_pause_seconds_count[5m])` |

#### Métricas de negócio

- `tasks_created_total` — gráfico de área (cumulativo).
- `task_completion_duration_seconds` (p50, p90, p99) — entender produtividade real do usuário.
- `rate(tasks_created_total[1h])` — tarefas criadas por hora.

#### Dashboard organizado por persona

| Persona | Painéis |
|---|---|
| **Dev** | Erros 5xx, latência p95, top endpoints lentos |
| **Ops** | CPU/memória, restarts de pod, status do HPA |
| **Negócio** | Tasks criadas/concluídas, tempo médio de conclusão |

---

### Etapa 5 — Alertas

**`observability/prometheus-rules.yaml`**

```yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: productivity-api-alerts
  namespace: observability
spec:
  groups:
    - name: productivity-api
      interval: 30s
      rules:
        - alert: HighErrorRate
          expr: |
            sum(rate(http_server_requests_seconds_count{application="productivity-api",status=~"5.."}[5m]))
            /
            sum(rate(http_server_requests_seconds_count{application="productivity-api"}[5m]))
            > 0.01
          for: 5m
          labels:
            severity: warning
          annotations:
            summary: "Alta taxa de erro 5xx na productivity-api"
            description: "Mais de 1% das requisições estão retornando 5xx nos últimos 5 minutos"

        - alert: HighLatencyP95
          expr: |
            histogram_quantile(0.95,
              sum(rate(http_server_requests_seconds_bucket{application="productivity-api"}[5m])) by (le)
            ) > 0.5
          for: 10m
          labels:
            severity: warning
          annotations:
            summary: "Latência p95 acima de 500ms"

        - alert: PodCrashLooping
          expr: |
            rate(kube_pod_container_status_restarts_total{namespace="productivity"}[15m]) > 0
          for: 5m
          labels:
            severity: critical
          annotations:
            summary: "Pod {{ $labels.pod }} reiniciando em loop"

        - alert: HikariPoolExhausted
          expr: |
            hikaricp_connections_active / hikaricp_connections_max > 0.9
          for: 5m
          labels:
            severity: warning
          annotations:
            summary: "Pool de conexões com banco quase esgotado (>90%)"
```

**Princípio:** alertas baseados em **SLOs**, não em valores arbitrários. "1% de erro" vem do SLO de 99% de disponibilidade.

---

## 🎬 Roteiro de Demo (Live, 7-10 min)

### Cenário 1: Pipeline funcionando (1 min)

```bash
# Commit + push
git commit -m "feat: aumenta limite de busca" && git push

# Mostrar pipeline no GitHub Actions rodando
# Esperar build + push de imagem
```

### Cenário 2: Deploy via Helm (1 min)

```bash
# Helm upgrade puxando a nova imagem
helm upgrade productivity ./helm/productivity-api \
  --namespace productivity \
  -f helm/productivity-api/values-prod.yaml \
  --set image.tag=sha-${GIT_SHA}

# Acompanhar rolling update — pods novos só recebem tráfego quando readiness passa
kubectl get pods -n productivity -w
```

### Cenário 3: Resiliência (2 min)

```bash
# Matar um pod
kubectl delete pod -n productivity <pod-name>

# Mostrar Kubernetes recriando + novos pods passando readiness
# Mostrar que a API continua respondendo (curl em loop)
while true; do curl -s http://api.productivity.local/actuator/health; sleep 1; done
```

### Cenário 4: Rollback (2 min)

```bash
# Deploy "ruim" (versão quebrada)
helm upgrade productivity ./helm/productivity-api \
  --set image.tag=v0.0.0-broken

# Pods entrando em CrashLoopBackOff
kubectl get pods -n productivity

# Rollback rápido
helm rollback productivity

# Ver alerta "PodCrashLooping" disparou no Alertmanager
```

### Cenário 5: Observabilidade (3 min) ⭐

```bash
# Abrir Grafana
kubectl port-forward -n observability svc/kube-prom-stack-grafana 3000:80
# http://localhost:3000

# Gerar carga
hey -z 60s -c 10 http://api.productivity.local/tasks

# Mostrar:
# - Painel "Tasks criadas" subindo
# - Latência p95
# - CPU dos pods subindo
# - HPA escalando de 3 para 5 pods
```

### Cenário 6: Logs centralizados (1 min)

```bash
# Pegar logs de um pod específico via Syslog-ng
tail -f /var/log/docker/productivity-api-*/2026/05/*.log

# Ou consulta no Loki/Grafana
# {namespace="productivity"} |= "ERROR"
```

---

## ✅ Critérios de Avaliação

| Critério | Peso | Como atendo |
|---|---|---|
| Automação (CI/CD) | 2.5 | Pipeline completo do Desafio 01 |
| Orquestração (Helm) | 2.5 | Chart do Desafio 03 com ServiceMonitor |
| Observabilidade | 2.5 | Métricas + dashboards + alertas + logs |
| Apresentação | 2.5 | Live demo com os 6 cenários acima |

---

## 🏆 Diferenciais para Nota Máxima

Itens do enunciado que vou tentar atingir:

- [ ] **GitOps** com ArgoCD — declarativo, Git como fonte da verdade
- [ ] **Tracing distribuído** com OpenTelemetry + Tempo/Jaeger
- [ ] **Testes de carga** com k6 no pipeline
- [ ] **Multi-ambiente** (dev/staging/prod) com promoção controlada
- [ ] **Network Policies** restringindo comunicação inter-pods
- [ ] **Sealed Secrets** ou **External Secrets** para credenciais

---

## 📂 Estrutura Final

```
productivity-api/
├── .github/workflows/
│   ├── ci.yml                       # Desafio 01
│   └── cd.yml                       # Desafio 01
├── Dockerfile                        # Desafio 02
├── docker-compose.yml                # Desafio 02
├── helm/
│   └── productivity-api/             # Desafio 03
│       ├── Chart.yaml
│       ├── values.yaml
│       ├── values-dev.yaml
│       ├── values-prod.yaml
│       └── templates/
│           ├── deployment.yaml
│           ├── service.yaml
│           ├── ingress.yaml
│           ├── hpa.yaml
│           ├── configmap.yaml
│           ├── secret.yaml
│           └── servicemonitor.yaml   # ⭐ Desafio 05
├── syslog-ng/                        # Desafio 04
│   ├── docker-compose.yml
│   └── config/syslog-ng.conf
├── observability/                    # ⭐ Desafio 05
│   ├── prometheus-values.yaml
│   ├── prometheus-rules.yaml
│   └── grafana-dashboards/
│       ├── productivity-red.json
│       ├── productivity-use.json
│       └── productivity-business.json
└── docs/
    └── challengs/
        ├── challenge-01.md
        ├── challenge-02.md
        ├── challenge-03.md
        ├── challenge-04.md
        └── challenge-05.md           # este arquivo
```

---

## 📌 Status e Próximos Passos

**Concluído:**

- [ ] Nada — é o último; depende dos quatro anteriores.

**A fazer (ordem):**

1. Concluir desafios 01 → 02 → 03 → 04.
2. Adicionar Actuator + Micrometer Prometheus na app.
3. Instalar `kube-prometheus-stack` via Helm.
4. Adicionar `ServiceMonitor` ao chart da productivity-api.
5. Criar métricas customizadas no `TaskService`.
6. Importar dashboards no Grafana (criar próprios).
7. Definir regras de alerta com base em SLOs realistas.
8. Ensaio da live demo (cronometrar — máximo 10 min).
9. Slides de apresentação (8-10 slides, foco visual).

---

## 📚 Referências

- [Spring Boot — Production-ready Features](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer + Prometheus](https://micrometer.io/docs/registry/prometheus)
- [kube-prometheus-stack](https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack)
- [Grafana Dashboards](https://grafana.com/grafana/dashboards/)
- [Google SRE Book — Monitoring Distributed Systems](https://sre.google/sre-book/monitoring-distributed-systems/)
- [The Twelve-Factor App](https://12factor.net/)
- [RED method](https://www.weave.works/blog/the-red-method-key-metrics-for-microservices-architecture/)
- [USE method](http://www.brendangregg.com/usemethod.html)

> 💡 **Lição transversal:** observabilidade não é "adicionar Grafana". É instrumentar a aplicação para que, em qualquer momento, você consiga responder três perguntas: *"está funcionando?"*, *"como está se comportando?"* e *"o que aconteceu lá atrás?"*. Sem isso, operar em produção é dirigir de olhos fechados.