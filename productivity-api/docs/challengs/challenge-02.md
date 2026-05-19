# 🐳 Desafio 02 — Trabalho em Equipe com Docker ou Kubernetes

> **Containerização da productivity-api** com Docker e Docker Compose, demonstrando portabilidade, reprodutibilidade e isolamento.

| Campo | Valor |
|---|---|
| **Status** | ✅ **Concluído** |
| **Aplicação-base** | productivity-api |
| **Ferramenta** | Docker + Docker Compose |
| **Modo** | Solo |

---

## 🎯 Objetivos

1. ✅ Containerizar a productivity-api com `Dockerfile` otimizado (multi-stage, usuário não-root).
2. ✅ Orquestrar localmente API + PostgreSQL via `docker-compose`.
3. ✅ Demonstrar os principais comandos Docker em ação.
4. ✅ Documentar a arquitetura da solução e os benefícios práticos observados.

---

## 🏗️ Arquitetura da Solução

```
┌────────────────────────────────────────────────┐
│              Docker Network                    │
│              "productivity-net"                │
│                                                │
│  ┌─────────────────────┐  ┌─────────────────┐  │
│  │  productivity-api   │  │   postgres:16   │  │
│  │  (Spring Boot 3.5)  │──│   (porta 5432)  │  │
│  │  porta: 8080        │  │                 │  │
│  │  user: appuser      │  │  volume: pgdata │  │
│  └─────────────────────┘  └─────────────────┘  │
│           │                                    │
└───────────┼────────────────────────────────────┘
            │
            ▼ porta 8080 exposta
   ┌────────────────┐
   │  Host (você)   │
   └────────────────┘
```

---

## ✅ O que foi entregue

### 1. Dockerfile multi-stage (`productivity-api/Dockerfile`)

- **Stage 1 (builder)** — `eclipse-temurin:21-jdk-alpine`. Copia o `pom.xml` antes do código pra aproveitar o cache de dependências do Docker. Em rebuilds incrementais, se só o código mudou, o `dependency:go-offline` não roda de novo.
- **Stage 2 (runtime)** — `eclipse-temurin:21-jre-alpine`. Só JRE (mais leve), sem JDK nem Maven, sem código-fonte. Tamanho final ≈ 250MB.
- **Usuário não-root** (`appuser`) — mitigação de escalada de privilégios em caso de RCE.
- **`SPRING_PROFILES_ACTIVE=dev` por padrão** — imagem sobe standalone (H2 in-memory). Sobrescrevível em runtime via `-e SPRING_PROFILES_ACTIVE=prod` quando integrar com Postgres.
- **`JAVA_OPTS`** parametrizável — permite tunar heap (`-Xms256m -Xmx512m`) sem rebuild.
- **HEALTHCHECK** com `wget --spider http://localhost:8080/actuator/health` — `start-period=60s` dá folga pra Spring Boot subir.

### 2. `.dockerignore`

Reduz o contexto de build excluindo `target/`, `.git/`, `.idea/`, `src/test/`, docs e arquivos de IDE. Sem isso, o `docker build` envia ~100MB+ pro daemon mesmo que esses arquivos não sejam usados na imagem.

### 3. `docker-compose.yml` orquestrando API + PostgreSQL

- **PostgreSQL 16 Alpine** com `pg_isready` como healthcheck.
- **API com `depends_on: postgres: condition: service_healthy`** — só sobe depois que o banco aceitar conexões. Elimina o clássico "API morre porque o banco ainda não está pronto".
- **Network bridge isolada** (`productivity-net`) — Postgres é resolvível pelo DNS interno (`postgres:5432`), mas NÃO está exposto no host. Isolamento real.
- **Variáveis via `.env`** — credenciais nunca commitadas. Template em `.env.example`.
- **Volume nomeado `pgdata`** — dados sobrevivem a `docker compose down`. Só somem com `down -v`.

### 4. `.gitattributes`

Força LF (`\n`) em arquivos de código mesmo no Windows. Sem isso, o checkout no Windows converte pra CRLF (`\r\n`) e o Spotless (Google Java Format) quebra o build.

### 5. Flyway migrations

Bonus que veio junto com a containerização: schema versionado em `src/main/resources/db/migration/`:
- `V1__init.sql` — cria tabela `tasks` com índices em `status`, `priority`, `created_at`.
- `V2__seed.sql` — 15 tarefas de exemplo pra dev.

Profile `dev` (H2) usa `MODE=PostgreSQL` no JDBC URL — **mesma migration funciona nos dois bancos**.

---

## ⚙️ Comandos demonstrados

### Build da imagem

```bash
cd productivity-api
docker build -t productivity-api:local .

# Resultado:
docker images productivity-api
# REPOSITORY         TAG     SIZE
# productivity-api   local   ~250MB
```

Sem multi-stage, a imagem teria ~700MB. A diferença é JDK + Maven + código-fonte que ficam no stage builder e são descartados.

### Rodar standalone (sem compose)

```bash
docker run -d --name productivity-test -p 8080:8080 productivity-api:local

# Aguardar startup (~60s na primeira vez)
docker logs -f productivity-test

# Validar
curl http://localhost:8080/actuator/health
# {"status":"UP"}

curl http://localhost:8080/tasks
# {"content":[...],"totalElements":15, ...}   ← os 15 do V2__seed.sql

# Healthcheck do Docker
docker ps
# STATUS                    PORTS
# Up 2 minutes (healthy)    0.0.0.0:8080->8080/tcp

# Confirmar usuário não-root
docker exec productivity-test whoami
# appuser
```

### Rodar com docker-compose

```bash
cp .env.example .env
# ajustar senhas em .env

docker compose up -d

# Acompanhar logs
docker compose logs -f api

# Status
docker compose ps
# NAME                       STATUS                  PORTS
# productivity-api           Up (healthy)            0.0.0.0:8080->8080/tcp
# productivity-postgres      Up (healthy)            5432/tcp   ← sem porta no host
```

### Demonstrar isolamento

```bash
# Postgres NÃO está exposto fora da network
docker compose port postgres 5432
# (vazio — confirma isolamento)

# Mas API enxerga via DNS interno
docker compose exec api wget -qO- http://postgres:5432/
# (resposta do Postgres)
```

### Demonstrar persistência

```bash
# Volume preserva dados entre restarts
docker compose down                # para containers, MANTÉM volume
docker compose up -d               # sobe de novo, dados intactos

# Pra apagar dados também:
docker compose down -v             # remove containers + volume
```

---

## 💡 Decisões aplicadas

| Decisão | Por quê |
|---|---|
| Multi-stage build | Imagem final não carrega Maven, JDK ou código-fonte |
| Alpine como base | ~5MB de SO base; superfície de ataque menor |
| `dependency:go-offline` antes de `COPY src` | Mudanças no código não invalidam cache de deps |
| Usuário não-root | Princípio do menor privilégio |
| Profile `dev` default na imagem | Imagem é testável standalone (`docker run -p 8080:8080 ...`) |
| `wget` adicionado via `apk` | Alpine não vem com curl/wget; precisa explicitamente |
| `start-period=60s` no HEALTHCHECK | Spring Boot pode demorar a subir em hardware modesto |
| Flyway com H2 em modo PostgreSQL | Mesma migration funciona em dev (H2) e prod (Postgres) |
| `depends_on` com `service_healthy` | API só sobe quando Postgres aceita conexão |
| Postgres sem porta no host | Isolamento — clientes externos não conectam direto |

---

## 🎯 Benefícios observados

| Benefício | Exemplo concreto |
|---|---|
| **Portabilidade** | Imagem roda igual no Pop!_OS, Windows (WSL2), macOS, Kubernetes |
| **Reprodutibilidade** | `git clone` + `docker compose up` = ambiente igual ao do colega |
| **Isolamento** | App não acessa filesystem do host; Postgres não na internet |
| **Velocidade de setup** | ~2 minutos do zero ao ambiente rodando |
| **Imutabilidade** | Imagem taggeada nunca muda; rollback = trocar tag |
| **Schema versionado** | Flyway garante que dev e prod tenham a mesma estrutura |

---

## 📂 Estrutura final entregue

```
productivity-api/
├── Dockerfile                       # multi-stage, non-root, healthcheck
├── .dockerignore                    # reduz contexto de build
├── .gitattributes                   # força LF em arquivos de código
├── docker-compose.yml               # API + Postgres + healthcheck dependency
├── .env.example                     # template das variáveis sensíveis
├── .env                             # local-only, gitignored
└── src/main/resources/db/migration/
    ├── V1__init.sql                 # schema da tabela tasks
    └── V2__seed.sql                 # dados iniciais (15 tarefas)
```

---

## 📚 Referências

- [Docker — Best practices for writing Dockerfiles](https://docs.docker.com/develop/develop-images/dockerfile_best-practices/)
- [Spring Boot — Container Images](https://docs.spring.io/spring-boot/docs/current/reference/html/container-images.html)
- [Eclipse Temurin images](https://hub.docker.com/_/eclipse-temurin)
- [Docker Compose specification](https://docs.docker.com/compose/compose-file/)
- [Flyway with Spring Boot](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool.flyway)

> 💡 **Lição transversal:** containerizar não é "empacotar". É repensar como a aplicação se relaciona com o ambiente — variáveis de ambiente em vez de arquivos, logs no stdout em vez de arquivos, estado em volumes em vez de no filesystem.