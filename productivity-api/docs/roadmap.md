# 🗺️ Roadmap

Evolução planejada da Productivity API. Itens marcados como ✅ já foram implementados; ⏳ estão em andamento; 🔲 são planejados.

> **Convenção:** itens são revisados a cada milestone fechado. O roadmap não é um contrato, é um norte.

---

## Milestone 1 — Fundação ✅

Status: **Concluído** (versão atual `0.0.1-SNAPSHOT`)

- ✅ CRUD completo de tarefas
- ✅ Validação com Bean Validation
- ✅ Paginação, filtros e busca
- ✅ Tratamento de erros padronizado (`ApiError` + `GlobalExceptionHandler`)
- ✅ Regra de negócio: `completedAt` automático em transições de status
- ✅ Profiles `dev` (H2) e `prod` (PostgreSQL)
- ✅ Documentação OpenAPI/Swagger
- ✅ Documentação técnica em `docs/`

---

## Milestone 2 — Qualidade

Status: **Próximo passo**

- 🔲 Suite de testes unitários (`TaskService` com Mockito)
- 🔲 Testes de integração de controller (`@WebMvcTest` + MockMvc)
- 🔲 Testes de repositório (`@DataJpaTest`)
- 🔲 Plugin **JaCoCo** com meta de cobertura ≥ 80%
- 🔲 Configurar **Checkstyle** ou **Spotless** para formatação consistente
- 🔲 Configurar **SonarLint**/**SonarQube** local para análise estática

**Critério de saída:** `./mvnw verify` roda em CI com cobertura ≥ 80% e zero warnings.

---

## Milestone 3 — Migrations e Auditoria

- 🔲 Adicionar **Flyway** ou **Liquibase** para versionar o schema
- 🔲 Migrar de `ddl-auto: update` (dev) para migrations explícitas
- 🔲 Adicionar campos de auditoria via JPA Auditing:
    - `createdBy`, `lastModifiedBy`, `lastModifiedAt`
- 🔲 Adicionar índice em `tasks.status` e `tasks.priority` para acelerar filtros

**Por quê:** schema versionado é pré-requisito para qualquer ambiente sério. Sem isso, `ddl-auto: validate` em prod falha em qualquer mudança de modelo.

---

## Milestone 4 — Containerização

Aborda o [Desafio 2](challengs/challenge-02.md).

- 🔲 `Dockerfile` multi-stage (build no Maven + runtime no JRE Alpine)
- 🔲 `.dockerignore` para evitar vazamento de arquivos
- 🔲 `docker-compose.yml` com API + PostgreSQL
- 🔲 Imagem rodando com usuário não-root
- 🔲 `HEALTHCHECK` no Dockerfile
- 🔲 Publicar imagem no GHCR (GitHub Container Registry)

---

## Milestone 5 — CI/CD

Aborda o [Desafio 1](challengs/challenge-01.md).

- 🔲 Workflow GitHub Actions: `lint → test → build → docker push`
- 🔲 Trigger em `push` para `main` e em `pull_request`
- 🔲 Cache de dependências Maven entre execuções
- 🔲 Upload de relatório de cobertura (Codecov ou artifact)
- 🔲 Tag de imagem por SHA do commit + `latest` em main
- 🔲 **Manual approval** para deploy em produção

**Critério de saída:** abrir um PR e ver o pipeline rodando completo em < 5 minutos.

---

## Milestone 6 — Orquestração

Aborda o [Desafio 3](challengs/challenge-03.md).

- 🔲 Helm Chart com:
    - `Deployment`, `Service`, `Ingress`
    - `ConfigMap` para configurações não-secretas
    - `Secret` para credenciais do banco
    - `HPA` (Horizontal Pod Autoscaler) com base em CPU
    - Probes (`liveness`, `readiness`)
- 🔲 `values-dev.yaml`, `values-staging.yaml`, `values-prod.yaml`
- 🔲 Deploy local em **Kind** ou **k3d**
- 🔲 Documentação de comandos: `helm install`, `helm upgrade`, `helm rollback`

---

## Milestone 7 — Observabilidade

Aborda os Desafios [4](challengs/challenge-04.md) e [5](challengs/challenge-05.md).

- 🔲 Logs estruturados em JSON (Logstash encoder)
- 🔲 Integração com **Syslog-ng** para centralização
- 🔲 Endpoint `/actuator/prometheus` expondo métricas
- 🔲 Métricas customizadas:
    - `tasks_created_total{priority,status}`
    - `tasks_completion_duration_seconds`
- 🔲 Dashboard Grafana com painéis para:
    - Taxa de requisições (req/s)
    - Latência (p50, p95, p99)
    - Taxa de erro (5xx %)
    - Saturação (CPU, memória, JVM heap)
- 🔲 Alertmanager com regras:
    - 5xx > 1% em 5 minutos
    - Latência p95 > 500ms
    - Pod restartando em loop

---

## Milestone 8 — Segurança

- 🔲 Autenticação via **Spring Security + JWT**
- 🔲 Relacionamento `Task → User` (cada usuário só vê suas tarefas)
- 🔲 Endpoint `POST /auth/login`, `POST /auth/register`
- 🔲 Refresh token via cookies httpOnly
- 🔲 Rate limiting via **Bucket4j** ou no Ingress (Nginx)
- 🔲 CORS configurado para frontends específicos
- 🔲 Audit log de ações sensíveis (delete, mudança de senha)

---

## Milestone 9 — Funcionalidades de Domínio

Evolução do modelo de tarefas.

- 🔲 Campo `dueDate` com validação (não pode ser passado)
- 🔲 Notificações de tarefas próximas do prazo (job agendado)
- 🔲 **Tags** para categorização (`@ManyToMany`)
- 🔲 **Subtarefas** (auto-relacionamento)
- 🔲 **Recorrência** com regras RRULE (RFC 5545)
- 🔲 Endpoint `GET /tasks/stats` retornando estatísticas:
    - Total por status
    - Tempo médio de conclusão
    - Tarefas atrasadas
- 🔲 Importação/exportação em CSV e JSON

---

## Milestone 10 — Cache e Performance

- 🔲 Spring Cache + Redis para listagens frequentes
- 🔲 Invalidação correta em writes (`@CacheEvict`)
- 🔲 Connection pool tuning (HikariCP em prod)
- 🔲 Testes de carga com **k6** ou **Gatling**
- 🔲 Otimização de queries (analisar com `EXPLAIN`)

---

## Ideias para o Futuro Distante 🔮

Nada planejado para sprint específica, mas vale anotar:

- Migração para **virtual threads** do Java 21 (`spring.threads.virtual.enabled=true`)
- Eventos de domínio com **Spring Modulith** ou Kafka
- **GraphQL** como alternativa à API REST
- Frontend **Next.js** consumindo a API
- App mobile em **React Native** (aproveitando experiência prévia com Oxe-Comprei)
- Integração com calendar (Google Calendar API) para sincronização bidirecional
- AI: usar a Anthropic API para sugerir prioridade automaticamente baseada no título/descrição

---

## Como contribuir com o roadmap

Mudanças no roadmap acontecem em três momentos:

1. **Fechamento de milestone** — itens marcados como ✅, próxima milestone vira a "próxima".
2. **Descoberta de débito técnico** — itens críticos podem subir de prioridade.
3. **Mudança de escopo do projeto** — milestones podem ser fundidos, divididos ou abandonados.

Toda alteração relevante deve ser registrada em [`decisions.md`](decisions.md) como ADR.