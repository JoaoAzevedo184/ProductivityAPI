# 🎬 Roteiro de Demo ao Vivo — Seminário DevOps & Observabilidade 360°

> Sequência exata de comandos para a apresentação do Desafio Final (`productivity-api`).
> Estruturado nos 4 momentos do seminário, com o "pulo do gato" (rollback) no centro.

---

## ⚠️ Convenção de portas (IMPORTANTE)

Para evitar conflito na porta 8080 do host durante a demo, as duas stacks usam portas diferentes:

| Stack | Acesso à API | Como |
|---|---|---|
| **Kind + Observabilidade** (Desafios 03/05) | `http://localhost:8080` | `kubectl port-forward` |
| **Syslog-ng** (Desafio 04) | `http://localhost:8081` | `docker-compose` (`8081:8080`) |

> O `syslog-ng/docker-compose.yml` foi ajustado: serviço `api` expõe `8081:8080`.
> Só o lado do **host** mudou (8081); o container continua escutando 8080 internamente.

---

## 🛫 Fase 0 — Pré-voo (30–40 min ANTES, fora da apresentação)

> **Regra de ouro:** suba toda a infraestrutura pesada antes. A demo ao vivo começa com tudo no ar.

```bash
# 1. Cluster Kind
kind create cluster --name productivity --config productivity-api/k8s/kind-config.yaml

# 2. Manifests base (namespace, secret, postgres)
kubectl apply -f productivity-api/k8s/manifests/

# 3. Build + load da imagem no Kind
docker build -t productivity-api:local productivity-api/
kind load docker-image productivity-api:local --name productivity

# 4. Instalar a app via Helm (ambiente dev)
helm install productivity ./productivity-api/helm/productivity-api \
  -f productivity-api/helm/productivity-api/values.yaml \
  -f productivity-api/helm/productivity-api/values-dev.yaml \
  --set serviceMonitor.enabled=false \
  --namespace productivity-dev --create-namespace

# 5. Subir a stack de observabilidade (na ordem certa — o script cuida disso)
./scripts/observability-up.sh

# 6. Subir a stack de logging Syslog-ng (porta 8081)
cd syslog-ng && docker compose up -d && cd ..

# 7. Confirmar que está tudo Running
kubectl get pods -n productivity-dev
kubectl get pods -n observability
docker compose -f syslog-ng/docker-compose.yml ps
```

### Port-forwards (deixe rodando em terminais separados)

```bash
# Terminal A — API do Kind
kubectl port-forward -n productivity-dev svc/productivity-productivity-api 8080:80

# Terminal B — Grafana
kubectl port-forward -n observability svc/kube-prom-stack-grafana 3000:80

# Terminal C — Prometheus
kubectl port-forward -n observability svc/kube-prom-stack-kube-prome-prometheus 9090:9090
```

### Abas do navegador já abertas

- **Grafana** → `http://localhost:3000` (admin/admin) → dashboard *"Productivity API — RED + Negócio"*
- **Prometheus** → `http://localhost:9090` → Status → Targets

### Checklist pré-voo

- [ ] `kubectl get pods -n productivity-dev` → tudo `Running`
- [ ] `kubectl get pods -n observability` → tudo `Running`
- [ ] Syslog-ng stack no ar (4 serviços)
- [ ] 3 port-forwards ativos
- [ ] Grafana e Prometheus abertos no navegador
- [ ] `hey` instalado (`hey -h` responde)
- [ ] `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`
- [ ] `curl http://localhost:8081/actuator/health` → `{"status":"UP"}`

---

## 🗣️ Momento 1 — Defesa Teórica (~2 min, sem terminal)

Containers **vs** VMs para o cenário proposto:

- Container = isolamento a nível de **processo**, compartilha o kernel do host.
- VM = virtualiza **hardware completo**, cada uma com seu próprio SO.
- Boot em **segundos** (container) vs. **minutos** (VM); footprint muito menor.
- Conexão com o projeto: imagem **Alpine multi-stage de ~250MB**, rodando como **non-root**.

Comando opcional de impacto:

```bash
docker images productivity-api
# ~250MB (vs ~700MB sem multi-stage)
```

---

## 🏗️ Momento 2 — Arquitetura da Solução (~3 min)

Mostrar que a teoria virou recursos reais no cluster:

```bash
# Todos os recursos do namespace
kubectl get all -n productivity-dev

# As 3 probes configuradas (startup + liveness + readiness)
kubectl describe pod -n productivity-dev -l app.kubernetes.io/name=productivity-api \
  | grep -A 3 -E "Liveness|Readiness|Startup"

# Prova de parametrização: HPA e Ingress NÃO existem em dev
kubectl get hpa,ingress -n productivity-dev
# -> "No resources found" — exatamente o esperado em dev
```

**Narrar:** Deployment → Pod (1 réplica em dev) → Service (ClusterIP) → ConfigMap + Secret.
Postgres como StatefulSet com PVC. O **mesmo chart** renderiza HPA e Ingress em staging/prod —
a diferença está só nos `values-*.yaml`.

---

## 🐱 Momento 3 — Live Demo: o "Pulo do Gato" (~5 min)

> Três atos: deploy bom → quebra proposital → rollback ao vivo.

### Ato 1 — Estado atual (deploy bem-sucedido)

```bash
helm list -n productivity-dev
helm history productivity -n productivity-dev

curl -s http://localhost:8080/actuator/health
# {"status":"UP"}
```

### Ato 2 — Quebrar de propósito (image tag inexistente)

```bash
helm upgrade productivity ./productivity-api/helm/productivity-api \
  -f productivity-api/helm/productivity-api/values.yaml \
  -f productivity-api/helm/productivity-api/values-dev.yaml \
  --set image.tag=versao-que-nao-existe \
  --namespace productivity-dev
```

Imediatamente, em outro terminal, mostrar o desastre **controlado**:

```bash
kubectl get pods -n productivity-dev -w
# Pod novo:   0/1   ImagePullBackOff   <- quebrado
# Pod velho:  1/1   Running            <- continua servindo!
```

**Provar que o usuário não percebeu nada** (a readiness probe do novo nunca passou,
então o K8s NÃO removeu o pod velho):

```bash
curl -s http://localhost:8080/actuator/health
# ainda {"status":"UP"}
```

> Ctrl+C para parar o `kubectl ... -w`.

### Ato 3 — Rollback ao vivo

```bash
helm history productivity -n productivity-dev
# ⚠️ identifique na hora a última revisão BOA (a anterior à quebrada)

helm rollback productivity <REVISION_BOA> -n productivity-dev
# Rollback was a success! Happy Helming!

kubectl get pods -n productivity-dev
helm history productivity -n productivity-dev
```

**Fechamento:** MTTR de **~1min29s, zero requisições perdidas** — argumento direto do
critério de "Qualidade Sustentável".

> 🚨 **NÃO decore o número da revisão.** Rode `helm history` na hora e use a revisão boa
> real — o histórico muda conforme você testa.

---

## 📊 Momento 4 — Painel de Observabilidade (~4 min)

### Parte A — Métricas (Kind + Grafana, porta 8080)

Gerar carga para os painéis se mexerem:

```bash
# Tráfego normal
hey -z 60s -c 10 http://localhost:8080/tasks

# Criar tarefa (alimenta a métrica de negócio tasks_created_total)
curl -s -X POST http://localhost:8080/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"Demo","description":"seminario","status":"PENDING","priority":"HIGH"}'

# Gerar erro 4xx
curl -s http://localhost:8080/tasks/99999 > /dev/null
```

No navegador, enquanto o `hey` roda:

- **Grafana** → RED (rate por URI, % erro 5xx, latência p50/p90/p99) + USE (heap, CPU, HikariCP) + Negócio (tarefas por prioridade)
- **Prometheus** → Status → Targets → `productivity-api` em **up**
- Query rápida no Prometheus:
  ```
  sum(rate(http_server_requests_seconds_count{application="productivity-api"}[1m]))
  ```

### Parte B — Persistência de logs (Syslog-ng, porta 8081)

> Critério explícito: **logs sobrevivem à destruição do container**.

```bash
cd syslog-ng

# Gerar tráfego + um erro (NA PORTA 8081)
curl -s http://localhost:8081/tasks > /dev/null
curl -s http://localhost:8081/tasks/99999 > /dev/null
sleep 5

# Mostrar a hierarquia de logs por data
docker compose exec syslog-ng sh -c "find /var/log/docker -type f"
# /var/log/docker/productivity-api/2026/.../...log
# /var/log/docker/_errors/...log

# 🔑 A PROVA: parar a API e mostrar que os logs SOBREVIVEM
docker compose stop api
docker compose exec syslog-ng sh -c "tail -5 /var/log/docker/productivity-api/2026/*/*.log"
# (logs ainda acessíveis, mesmo com a API parada)

cd ..
```

**Fechamento (citando o professor):** *"um sistema sem logging adequado é como dirigir um
carro sem painel"* — mostramos o painel (Grafana) **E** a caixa-preta que sobrevive ao
acidente (Syslog-ng).

---

## 📋 Folha de cola (tela aberta durante a demo)

```
M2  kubectl get all -n productivity-dev
M2  kubectl get hpa,ingress -n productivity-dev
M3  helm history productivity -n productivity-dev
M3  helm upgrade ... --set image.tag=versao-que-nao-existe ...
M3  kubectl get pods -n productivity-dev -w
M3  helm rollback productivity <REV_BOA> -n productivity-dev
M4  hey -z 60s -c 10 http://localhost:8080/tasks
M4  docker compose exec syslog-ng sh -c "find /var/log/docker -type f"
M4  docker compose stop api  +  tail nos logs
```

---

## 🧯 Plano B (se algo falhar ao vivo)

| Problema | Saída rápida |
|---|---|
| Port-forward caiu | Reabrir o `kubectl port-forward` no terminal correspondente |
| Pod não sobe no rollback | `kubectl describe pod -n productivity-dev <pod>` para mostrar o evento |
| Grafana sem dados | Aumentar a janela de tempo (canto sup. direito) p/ "Last 15 min" |
| `hey` não instalado | Loop de curl: `for i in $(seq 1 200); do curl -s localhost:8080/tasks >/dev/null; done` |
| Syslog-ng sem logs | `docker compose exec syslog-ng sh -c "tail -20 /config/log/current"` (procurar erro) |

---

## 🧹 Pós-demo (limpeza, opcional)

```bash
# Religar a API do Syslog-ng (se for usar de novo)
cd syslog-ng && docker compose start api && cd ..

# Restaurar a app do Kind para a última revisão boa (se ficou na quebrada)
helm rollback productivity <REV_BOA> -n productivity-dev

# Derrubar tudo (fim do dia)
docker compose -f syslog-ng/docker-compose.yml down -v
kind delete cluster --name productivity
```