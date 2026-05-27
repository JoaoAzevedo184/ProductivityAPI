# 📋 Desafios 02 e 03 — Comandos, Tecnologias e Resultados

> Referência rápida com tudo que foi usado e alcançado nos Desafios 02 (Docker) e 03 (Helm/Kubernetes).
> Útil para apresentações, slides, README do repositório e revisão geral.

---

## 🧰 Stack tecnológica completa

### Aplicação
| Tecnologia | Versão | Função |
|---|---|---|
| Java | 21 (LTS) | Linguagem |
| Spring Boot | 3.5.14 | Framework principal |
| Spring Data JPA | 3.5.x | Persistência |
| Spring Actuator | 3.5.x | Health checks + métricas |
| Spring Validation | 3.5.x | Bean Validation |
| Springdoc OpenAPI | 2.8.16 | Swagger UI + OpenAPI spec |
| Lombok | latest | Boilerplate reduction |
| Flyway Core + Postgres | 10.x | Migrations versionadas |
| Maven | 3.9.15 (via wrapper) | Build |

### Bancos de dados
| Tecnologia | Função |
|---|---|
| H2 (in-memory, modo PostgreSQL) | Banco para perfil `dev` |
| PostgreSQL 16 Alpine | Banco para perfil `prod` |

### Qualidade de código (CI)
| Tecnologia | Função |
|---|---|
| JaCoCo 0.8.12 | Cobertura de testes (gate 60% → meta 80%) |
| Spotless 2.43 + Google Java Format 1.22 | Formatação automática |
| SpotBugs 4.8.6 | Análise estática de bugs |
| OWASP Dependency Check 10.0.4 | Scan de vulnerabilidades |
| JUnit 5 + Mockito + AssertJ | Testes unitários |
| Spring MockMvc | Testes de integração HTTP |

### Containers e orquestração
| Tecnologia | Função |
|---|---|
| Docker | Engine de containers |
| Docker Compose v2 | Orquestração local |
| Eclipse Temurin JDK 21 Alpine | Imagem builder |
| Eclipse Temurin JRE 21 Alpine | Imagem runtime |
| Kind (Kubernetes in Docker) | Cluster Kubernetes local |
| kubectl | CLI do Kubernetes |
| Helm 3 | Gerenciador de pacotes Kubernetes |

### CI/CD
| Tecnologia | Função |
|---|---|
| GitHub Actions | Pipeline CI |
| GitHub Container Registry (GHCR) | Registry de imagens (planejado) |

---

## 🐳 Desafio 02 — Comandos Docker

### Build, run e inspeção

```bash
# Build com cache otimizado (multi-stage)
docker build -t productivity-api:local productivity-api/

# Inspecionar tamanho da imagem
docker images productivity-api
# REPOSITORY         TAG     SIZE
# productivity-api   local   ~250MB

# Rodar standalone (modo dev, H2 in-memory)
docker run -d --name productivity-test -p 8080:8080 productivity-api:local

# Acompanhar logs
docker logs -f productivity-test

# Inspecionar container em execução
docker exec productivity-test whoami            # appuser (não-root!)
docker exec productivity-test ls -la /app       # só app.jar
docker exec productivity-test ps aux            # processo java

# Validar healthcheck
docker ps
# STATUS: Up X minutes (healthy)

# Parar e remover
docker stop productivity-test && docker rm productivity-test
```

### Docker Compose (API + Postgres)

```bash
# Setup inicial das variáveis
cp .env.example .env

# Subir tudo
docker compose up -d

# Acompanhar logs
docker compose logs -f api

# Status
docker compose ps
docker compose port postgres 5432    # vazio (isolamento)

# Parar mantendo dados
docker compose down

# Parar apagando dados
docker compose down -v

# Rebuild forçado quando muda Dockerfile
docker compose build --no-cache
docker compose up -d
```

### Validação funcional

```bash
# Health check
curl http://localhost:8080/actuator/health
# {"status":"UP"}

# CRUD
curl -X POST http://localhost:8080/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"Teste","description":"Docker","status":"PENDING","priority":"HIGH"}'

curl http://localhost:8080/tasks
# {"content":[...],"totalElements":16, ...}   ← 15 do seed + 1 criado
```

---

## ⛵ Desafio 03 — Comandos Helm + Kubernetes

### Setup do cluster

```bash
# Criar cluster Kind com config customizada (portas 80/443 mapeadas)
kind create cluster --name productivity --config k8s/kind-config.yaml

# Verificar
kubectl cluster-info --context kind-productivity
kubectl get nodes

# Aplicar manifests "manuais" do Postgres (fora do chart)
kubectl apply -f k8s/manifests/

# Confirmar
kubectl get ns | grep productivity
# productivity-dev   Active

kubectl get pods -n productivity-dev
# postgres-0   1/1   Running
```

### Carregar imagem local no Kind

```bash
# Build da imagem no Docker do host
docker build -t productivity-api:local productivity-api/

# Carregar no nó do Kind (sem isso, ImagePullBackOff)
kind load docker-image productivity-api:local --name productivity
```

### Validar o chart antes de aplicar

```bash
# Linter de sintaxe Helm
helm lint ./helm/productivity-api

# Renderizar templates sem aplicar (debug)
helm template productivity ./helm/productivity-api \
  -f helm/productivity-api/values.yaml \
  -f helm/productivity-api/values-dev.yaml

# Comparar o que cada profile gera
helm template productivity ./helm/productivity-api \
  -f helm/productivity-api/values.yaml \
  -f helm/productivity-api/values-staging.yaml | grep "^kind:"
# kind: ConfigMap
# kind: Service
# kind: Deployment
# kind: HorizontalPodAutoscaler   ← só em staging/prod
# kind: Ingress                   ← só em staging/prod
```

### Install / Upgrade / Rollback

```bash
# Primeira instalação
helm install productivity ./helm/productivity-api \
  -f helm/productivity-api/values.yaml \
  -f helm/productivity-api/values-dev.yaml \
  --namespace productivity-dev \
  --create-namespace

# Upgrade (mudança de config ou versão)
helm upgrade productivity ./helm/productivity-api \
  -f helm/productivity-api/values.yaml \
  -f helm/productivity-api/values-dev.yaml \
  --namespace productivity-dev

# Histórico
helm history productivity -n productivity-dev

# Rollback pra revisão específica
helm rollback productivity 2 -n productivity-dev

# Listar releases em todos os namespaces
helm list -A

# Status detalhado
helm status productivity -n productivity-dev

# Desinstalar (cuidado: remove tudo do release)
helm uninstall productivity -n productivity-dev
```

### Inspeção dos recursos K8s

```bash
# Todos os recursos do namespace
kubectl get all -n productivity-dev

# Pods com labels (debug de selectors)
kubectl get pods -n productivity-dev --show-labels

# Watch (acompanhar rolling update / falhas)
kubectl get pods -n productivity-dev -w

# Logs de um pod
kubectl logs -f -n productivity-dev <pod-name>

# Logs do Deployment (todos os pods)
kubectl logs -f -n productivity-dev deployment/productivity-productivity-api

# Inspecionar probes
kubectl describe pod -n productivity-dev -l app.kubernetes.io/name=productivity-api \
  | grep -A 3 -E "Liveness|Readiness|Startup"

# Verificar HPA e Ingress (vazios em dev — parametrização funcionando)
kubectl get hpa,ingress -n productivity-dev

# Port-forward pra testar API
kubectl port-forward -n productivity-dev svc/productivity-productivity-api 8080:80
curl http://localhost:8080/actuator/health
```

### Demo de upgrade quebrado + rollback

```bash
# 1. Provocar falha (image tag inexistente)
helm upgrade productivity ./helm/productivity-api \
  -f helm/productivity-api/values.yaml \
  -f helm/productivity-api/values-dev.yaml \
  --set image.tag=versao-que-nao-existe \
  --namespace productivity-dev

# 2. Observar desastre (pod novo falha, pod velho continua servindo)
kubectl get pods -n productivity-dev -w
# productivity-...-novo   0/1   ImagePullBackOff
# productivity-...-velho  1/1   Running           ← continua vivo!

# 3. Conferir histórico
helm history productivity -n productivity-dev

# 4. Voltar pra revisão boa
helm rollback productivity 2 -n productivity-dev

# 5. Confirmar recuperação
kubectl get pods -n productivity-dev
helm history productivity -n productivity-dev
```

---

## 🎯 Resultados alcançados

### Desafio 02

| Resultado | Métrica / Evidência |
|---|---|
| Imagem Docker otimizada | **~250MB** (vs ~700MB sem multi-stage) |
| Build incremental rápido | **~30s** com cache de dependências (~3min sem) |
| Tempo de startup do compose | **~60s** (Postgres healthcheck → API up) |
| Setup completo do ambiente | **~2 minutos** (clone → up running) |
| Container roda como não-root | Verificado: `whoami` retorna `appuser` |
| Postgres isolado (sem porta no host) | Verificado: `docker compose port postgres 5432` vazio |
| Healthcheck do Docker funcionando | Verificado: `STATUS: Up X (healthy)` |
| Schema versionado | 2 migrations Flyway aplicadas (`V1__init`, `V2__seed`) |
| Dados persistem entre `down/up` | Verificado com volume nomeado `pgdata` |
| Build LF-safe no Windows | `.gitattributes` força LF; Spotless passa |

### Desafio 03

| Resultado | Métrica / Evidência |
|---|---|
| Chart Helm funcional | `helm install`/`upgrade`/`rollback` funcionando |
| Parametrização por ambiente | 3 values files; renderização condicional comprovada |
| Probes corretas (3 níveis) | Verificadas via `kubectl describe pod` |
| Rolling update sem downtime | Capturado em log: pod novo passa readiness ANTES do velho morrer |
| HPA condicional | `kubectl get hpa -n productivity-dev` → vazio; staging/prod → 1 HPA |
| Ingress condicional | Mesmo padrão do HPA |
| `checksum/config` annotation | Force-rolling-update em mudança de ConfigMap |
| **MTTR (Mean Time to Recovery)** | **1 min 29s** (deploy quebrado → rollback bem-sucedido) |
| **Zero requisições perdidas no rollback** | Pod velho preservado durante todo o `ImagePullBackOff` |
| Histórico versionado | 4 revisions registradas via `helm history` |
| Recursos por ambiente diferenciados | dev: 100m CPU; prod: 500m-2000m CPU |

---

## 📊 Diferenciais técnicos aplicados

Coisas que vão **além** do mínimo pedido nos enunciados:

| Diferencial | Onde aparece | Por que importa |
|---|---|---|
| **Multi-stage build** | Dockerfile | Imagem 3x menor; sem JDK em produção |
| **Usuário não-root** | Dockerfile | Mitigação de escalada de privilégios |
| **`pg_isready` healthcheck** | docker-compose | Elimina race condition no startup |
| **Flyway com modo H2/Postgres compartilhado** | application.yml + migrations | Mesma migration roda em dev e prod |
| **`startupProbe` separada** | deployment.yaml | Previne CrashLoopBackOff durante boot lento |
| **`checksum/config` annotation** | deployment.yaml | Auto-redeploy em mudança de config |
| **`replicas` condicional ao HPA** | deployment.yaml | Evita conflito entre Deployment e HPA |
| **Templates Helm condicionais** | hpa.yaml, ingress.yaml | Renderização limpa por ambiente |
| **Secret como `existingSecret` (não criado pelo chart)** | values.yaml | Desacopla credenciais do release |
| **Three-stage CI** (lint → build/test → security) | ci.yml | Fail-fast + paralelização |
| **OWASP scan com cache NVD** | ci.yml | Evita download de ~500MB a cada run |

---

## 🗣️ Pontos-chave para a apresentação

### Desafio 02 — 1 slide cada

1. **Por que containerizar?** Eliminar "funciona na minha máquina"
2. **Multi-stage build:** mostrar Dockerfile e tamanho final
3. **Live demo:** `docker compose up` → API respondendo em ~60s
4. **Isolamento:** Postgres não exposto; usuário não-root
5. **Persistência:** `down/up` mantém dados; `down -v` apaga

### Desafio 03 — 1 slide cada

1. **Por que Helm?** Versionar e parametrizar manifests K8s
2. **Estrutura do chart + values multi-ambiente** (tabela dev/staging/prod)
3. **Três probes do K8s** (startup + liveness + readiness) — diagrama temporal
4. **Live demo principal:** upgrade quebrado → `ImagePullBackOff` → rollback
5. **Resultado:** MTTR de 1 min 29s, zero downtime, histórico versionado

---

## 📂 Estrutura final do repositório

```
productivity-api/
├── .github/workflows/
│   ├── ci.yml                          # ✅ Lint + test + security scan
│   └── cd.yml                          # 🔲 Pendente (Desafio 01)
├── productivity-api/                    # Projeto Maven
│   ├── Dockerfile                      # ✅ Multi-stage, non-root, healthcheck
│   ├── .dockerignore
│   ├── .gitattributes                  # LF-safe no Windows
│   ├── docker-compose.yml              # ✅ API + Postgres healthcheck dep
│   ├── .env.example
│   ├── pom.xml                         # Java 21 + Spring Boot 3.5
│   └── src/main/resources/
│       ├── application.yml             # Profile dev (default)
│       ├── application-dev.yml         # H2 + PostgreSQL mode
│       ├── application-prod.yml        # Postgres real
│       └── db/migration/
│           ├── V1__init.sql            # Schema versionado
│           └── V2__seed.sql            # 15 tarefas de exemplo
├── helm/productivity-api/               # ✅ Chart Helm completo
│   ├── Chart.yaml
│   ├── values.yaml                     # Defaults
│   ├── values-dev.yaml                 # 1 replica, sem HPA/Ingress
│   ├── values-staging.yaml             # 2-5 replicas, HPA, Ingress sem TLS
│   ├── values-prod.yaml                # 3-10 replicas, HPA, Ingress TLS
│   └── templates/
│       ├── _helpers.tpl
│       ├── configmap.yaml
│       ├── deployment.yaml             # ✅ 3 probes + HPA-aware
│       ├── service.yaml
│       ├── hpa.yaml                    # ✅ Condicional
│       └── ingress.yaml                # ✅ Condicional
├── k8s/
│   ├── kind-config.yaml                # Cluster Kind config
│   └── manifests/                      # Recursos fora do chart
│       ├── 01-namespace.yaml
│       ├── 02-postgres-secret.yaml
│       └── 03-postgres-statefulset.yaml
└── docs/
    ├── api.md
    ├── architecture.md
    ├── decisions.md
    ├── devops.mdd
    ├── roadmap.md
    ├── setup.md
    └── challengs/
        ├── challenge-01.md              # 🟡 70% (CI pronto, CD pendente)
        ├── challenge-02.md              # ✅ 100%
        ├── challenge-03.md              # ✅ 100% (resta slides)
        ├── challenge-04.md              # 🔲 Planejado
        └── challenge-05.md              # 🔲 Planejado
```