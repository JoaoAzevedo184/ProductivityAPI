# 🐳 Desafio 02 — Trabalho em Equipe com Docker ou Kubernetes

> **Containerização da productivity-api** com Docker e Docker Compose, demonstrando portabilidade, reprodutibilidade e isolamento.

| Campo | Valor |
|---|---|
| **Status** | 🔲 Planejado |
| **Aplicação-base** | productivity-api |
| **Ferramenta** | Docker + Docker Compose (Kubernetes vem no [Desafio 03](challenge-03.md)) |
| **Modo** | Solo |

---

## 🎯 Objetivos

1. Containerizar a productivity-api com `Dockerfile` otimizado (multi-stage, usuário não-root).
2. Orquestrar localmente API + PostgreSQL via `docker-compose`.
3. Demonstrar os principais comandos Docker em ação.
4. Documentar a arquitetura da solução e os benefícios práticos observados.

---

## 🏗️ Arquitetura da Solução

```
┌────────────────────────────────────────────────┐
│              Docker Network                    │
│              "productivity-net"                │
│                                                │
│  ┌─────────────────────┐  ┌─────────────────┐  │
│  │  productivity-api   │  │   postgres:16   │  │
│  │  (Spring Boot)      │──│   (porta 5432)  │  │
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

## 🛠️ Implementação

### Etapa 1 — Dockerfile multi-stage

**`Dockerfile`** (raiz do projeto)

```dockerfile
# ============================================================
# Stage 1: Build — compila o JAR com Maven
# ============================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copia apenas o pom.xml e o wrapper primeiro para cache de dependências
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Agora copia o código-fonte
COPY src src

# Builda o JAR (pula testes — eles já rodaram no CI)
RUN ./mvnw clean package -DskipTests -B

# ============================================================
# Stage 2: Runtime — imagem final enxuta
# ============================================================
FROM eclipse-temurin:21-jre-alpine

# Cria usuário não-root por segurança
RUN addgroup -S app && adduser -S appuser -G app

WORKDIR /app

# Copia apenas o JAR final, com owner correto
COPY --from=builder --chown=appuser:app /build/target/*.jar app.jar

# Usuário não-root
USER appuser

# Variáveis de ambiente padrão (sobrescrevíveis via -e ou compose)
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS=""

EXPOSE 8080

# Healthcheck — Spring Actuator deve estar habilitado
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Decisões aplicadas:**

| Decisão | Por quê |
|---|---|
| Multi-stage build | Imagem final não carrega Maven nem o código-fonte; só o JAR + JRE |
| `21-jre-alpine` (runtime) | Não precisa de JDK em produção; Alpine é ~5MB |
| `21-jdk-alpine` (build) | Precisa do JDK para compilar |
| Cache de dependências separado | `dependency:go-offline` antes do `COPY src` → mudanças no código não invalidam o cache de deps |
| Usuário não-root (`appuser`) | Mitigação de escalada de privilégios em caso de RCE |
| `HEALTHCHECK` | Permite ao Docker/Kubernetes saber quando o container está realmente pronto |
| `JAVA_OPTS` env var | Permite tunar JVM (heap, GC) sem rebuild |

---

### Etapa 2 — `.dockerignore`

Evita copiar lixo para o contexto do build (acelera + reduz risco de vazamento):

```
# Build artifacts
target/
*.jar
!target/*.jar  # exceção: precisamos do JAR final

# IDEs
.idea/
.vscode/
*.iml

# Git
.git/
.gitignore
.gitattributes

# Logs
*.log
logs/

# Docs (não precisam ir pra imagem)
docs/
README.md

# Postman
*.postman_collection.json

# Sistema operacional
.DS_Store
Thumbs.db
```

---

### Etapa 3 — `docker-compose.yml`

Orquestração local com API + Postgres:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: productivity-postgres
    environment:
      POSTGRES_DB: productivity
      POSTGRES_USER: productivity
      POSTGRES_PASSWORD: ${DB_PASSWORD:-changeme}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U productivity"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - productivity-net

  api:
    build:
      context: .
      dockerfile: Dockerfile
    image: productivity-api:local
    container_name: productivity-api
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_URL: jdbc:postgresql://postgres:5432/productivity
      DB_USER: productivity
      DB_PASSWORD: ${DB_PASSWORD:-changeme}
      JAVA_OPTS: "-Xms256m -Xmx512m"
    ports:
      - "8080:8080"
    networks:
      - productivity-net

volumes:
  pgdata:

networks:
  productivity-net:
    driver: bridge
```

**Detalhes importantes:**

- `depends_on` com `condition: service_healthy` — a API só sobe **depois** que o Postgres responder ao `pg_isready`. Evita o clássico "API morre no startup porque o banco ainda não está pronto".
- `${DB_PASSWORD:-changeme}` — usa variável de ambiente ou fallback `changeme`. Em produção, exportar `DB_PASSWORD` antes do `up`.
- Network customizada (`productivity-net`) — isola os serviços e permite o hostname `postgres` ser resolvido dentro da rede.

---

## ⚙️ Comandos Demonstrativos

Roteiro de comandos para a apresentação:

### Build da imagem

```bash
# Build standalone
docker build -t productivity-api:local .

# Inspecionar tamanho da imagem
docker images productivity-api
# REPOSITORY         TAG     SIZE
# productivity-api   local   ~180MB   ← multi-stage + Alpine
```

**Comparação:** sem multi-stage, a imagem teria ~700MB (carregaria Maven + JDK completo + código-fonte).

### Rodar com docker-compose

```bash
# Subir tudo
docker compose up -d

# Acompanhar logs
docker compose logs -f api

# Status dos containers
docker compose ps

# NAME                    STATUS                  PORTS
# productivity-api        Up (healthy)            0.0.0.0:8080->8080/tcp
# productivity-postgres   Up (healthy)            5432/tcp
```

### Validar que está rodando

```bash
# Health check
curl http://localhost:8080/actuator/health
# {"status":"UP"}

# Criar uma task
curl -X POST http://localhost:8080/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"Teste Docker","description":"funcionou","status":"PENDING","priority":"HIGH"}'
```

### Inspecionar o container em execução

```bash
# Entrar no container (útil para debugging)
docker compose exec api sh

# Dentro do container:
whoami        # appuser (não-root!)
ls -la /app   # só o app.jar
ps aux        # processo java rodando
exit
```

### Demonstrar isolamento

```bash
# Ver redes
docker network ls | grep productivity

# Inspecionar rede
docker network inspect productivity-api_productivity-net

# Ver que o Postgres NÃO está exposto no host (sem porta mapeada)
# — só a API enxerga o banco via DNS interno
docker compose port postgres 5432
# (vazio — confirma isolamento)
```

### Persistência via volume

```bash
# Listar volumes
docker volume ls | grep productivity

# Inspecionar
docker volume inspect productivity-api_pgdata
```

Mesmo derrubando os containers (`docker compose down`), os dados sobrevivem no volume. Para apagar tudo: `docker compose down -v`.

### Teardown

```bash
# Para tudo (preserva volume)
docker compose down

# Para tudo + apaga volume
docker compose down -v

# Limpar imagens dangling (cleanup geral)
docker image prune -f
```

---

## 💡 Benefícios Observados

Lista para a apresentação, com exemplos concretos:

| Benefício | Exemplo concreto |
|---|---|
| **Portabilidade** | A mesma imagem roda no meu Pop!_OS, no Windows do colega, no macOS, no servidor Ubuntu, no Kubernetes |
| **Reprodutibilidade** | Não tem mais "funciona na minha máquina" — todo mundo roda a mesma imagem com o mesmo `Dockerfile` |
| **Isolamento** | A app não consegue acessar arquivos arbitrários do host; o Postgres não está exposto na internet |
| **Velocidade de setup** | Setup do ambiente: `git clone` + `docker compose up` = ~2 minutos |
| **Onboarding** | Novo dev no time não precisa instalar Java, Maven, Postgres — só Docker |
| **Imutabilidade** | Imagem taggeada com SHA do commit nunca muda; rollback é trocar tag |
| **Escalabilidade** | Subir 5 instâncias é `docker compose up --scale api=5` |

---

## 🧪 Testes de Validação

Checklist antes da apresentação:

- [ ] `docker build` completa em < 3 minutos (primeira vez); < 30s (com cache).
- [ ] Imagem final pesa < 250MB.
- [ ] `docker compose up` sobe tudo em < 60 segundos.
- [ ] `curl /actuator/health` retorna `UP` após startup.
- [ ] CRUD funcional via Postman/curl (rodar a collection completa).
- [ ] `docker compose down -v` + `docker compose up` recria o ambiente do zero sem erros.
- [ ] Container roda como `appuser`, não como root (`docker compose exec api whoami`).

---

## 🔐 Considerações de Segurança

Aplicadas no Dockerfile + compose:

- ✅ Usuário não-root no runtime.
- ✅ Imagem base Alpine (superfície de ataque menor).
- ✅ `.dockerignore` evita vazar `.git`, `.env`, etc.
- ✅ Postgres não expõe porta no host.
- ✅ Senha do banco via variável de ambiente, não hardcoded.

**Próximos passos (escopo do [Desafio 05](challenge-05.md)):**

- Imagem scan com **Trivy** ou **Snyk** no CI.
- Secrets via **Docker Secrets** ou Vault.
- Network policies mais restritivas.
- TLS interno entre serviços.

---

## 🎤 Roteiro de Apresentação

### Slide 1: Por que containerizar?
> "Eliminar a frase 'funciona na minha máquina'."

### Slide 2: Arquitetura
- Diagrama da seção "Arquitetura".

### Slide 3: Live demo
- `docker compose up`.
- Mostrar containers subindo.
- `curl` em alguns endpoints.
- Entrar no container e mostrar que roda como `appuser`.
- Derrubar a API (`docker kill productivity-api`).
- Mostrar que o Postgres continua de pé.
- `docker compose up -d` para recuperar.

### Slide 4: Benefícios práticos
- Portabilidade, reprodutibilidade, isolamento (tabela acima).

### Slide 5: Próximos passos
- Pipeline CI/CD ([Desafio 01](challenge-01.md)) buildando essa imagem automaticamente.
- Orquestração em produção via Kubernetes ([Desafio 03](challenge-03.md)).

---

## 📂 Estrutura Final

```
productivity-api/
├── Dockerfile
├── .dockerignore
├── docker-compose.yml
├── docker-compose.override.yml.example   # exemplo para customizações locais
└── (resto do projeto)
```

---

## 📌 Status e Próximos Passos

**Concluído:**

- [ ] Nada ainda.

**A fazer:**

1. Adicionar `spring-boot-starter-actuator` (necessário pro healthcheck).
2. Criar `Dockerfile`, `.dockerignore` e `docker-compose.yml`.
3. Validar checklist da seção "Testes de Validação".
4. Capturar screenshots/logs da demo.
5. Preparar slides (5 slides, foco em demo).

---
# Atualizações do `challenge-02.md`

Substitua a tabela de status no topo:

```markdown
| Campo | Valor |
|---|---|
| **Status** | 🟡 Dockerfile concluído · docker-compose pendente |
| **Aplicação-base** | productivity-api |
| **Ferramenta** | Docker + Docker Compose (Kubernetes vem no [Desafio 03](challenge-03.md)) |
| **Modo** | Solo |
```

E adicione, antes da seção "Implementação", esta nova seção:

```markdown
## ✅ O que foi entregue até agora

### Dockerfile multi-stage (`productivity-api/Dockerfile`)

- **Stage 1 (builder)** — `eclipse-temurin:21-jdk-alpine`. Copia o `pom.xml` antes do código pra aproveitar o cache de dependências do Docker. Em rebuilds incrementais, se só o código mudou, o `dependency:go-offline` não roda de novo.
- **Stage 2 (runtime)** — `eclipse-temurin:21-jre-alpine`. Só JRE (mais leve), sem JDK nem Maven, sem código-fonte. Tamanho final ≈ 250MB.
- **Usuário não-root** (`appuser`) — mitigação de escalada de privilégios em caso de RCE.
- **`SPRING_PROFILES_ACTIVE=dev` por padrão** — imagem sobe standalone (H2 in-memory). Sobrescrevível em runtime via `-e SPRING_PROFILES_ACTIVE=prod` quando integrar com Postgres.
- **`JAVA_OPTS`** parametrizável — permite tunar heap (`-Xms256m -Xmx512m`) sem rebuild.
- **HEALTHCHECK** com `wget --spider http://localhost:8080/actuator/health` — `start-period=60s` dá folga pra Spring Boot subir.

### `.dockerignore`

Reduz o contexto de build excluindo `target/`, `.git/`, `.idea/`, `src/test/`, docs e arquivos de IDE. Sem isso, o `docker build` envia ~100MB+ pro daemon mesmo que esses arquivos não sejam usados na imagem.

### Decisões aplicadas

| Decisão | Por quê |
|---|---|
| Multi-stage build | Imagem final não carrega Maven, JDK ou código-fonte |
| Alpine como base | ~5MB de SO base; superfície de ataque menor |
| `dependency:go-offline` antes de `COPY src` | Mudanças no código não invalidam o cache de dependências |
| Usuário não-root | Princípio do menor privilégio |
| Profile `dev` default | Imagem é testável standalone (`docker run -p 8080:8080 ...`) |
| `wget` adicionado via `apk` | Alpine não vem com curl/wget; precisa explicitamente |
| `start-period=60s` | Spring Boot pode demorar a subir em hardware modesto |

---

## 🧪 Testes locais (antes do compose)

Roteiro pra validar o Dockerfile sozinho:

```bash
cd productivity-api

# Build
docker build -t productivity-api:local .

# Ver tamanho
docker images productivity-api
# REPOSITORY         TAG     SIZE
# productivity-api   local   ~250MB

# Rodar
docker run -d --name productivity-test -p 8080:8080 productivity-api:local

# Aguardar startup (60-90s na primeira vez)
docker logs -f productivity-test

# Validar
curl http://localhost:8080/actuator/health
# {"status":"UP"}

curl http://localhost:8080/tasks
# {"content":[...],"totalElements":15, ...}   ← os 15 do data.sql

# Healthcheck
docker ps
# STATUS                    PORTS
# Up 2 minutes (healthy)    0.0.0.0:8080->8080/tcp

# Confirmar usuário não-root
docker exec productivity-test whoami
# appuser

# Cleanup
docker stop productivity-test && docker rm productivity-test
```
```
## 📚 Referências

- [Docker — Best practices for writing Dockerfiles](https://docs.docker.com/develop/develop-images/dockerfile_best-practices/)
- [Spring Boot — Container Images](https://docs.spring.io/spring-boot/docs/current/reference/html/container-images.html)
- [Eclipse Temurin images](https://hub.docker.com/_/eclipse-temurin)
- [Docker Compose specification](https://docs.docker.com/compose/compose-file/)

> 💡 **Lição transversal:** containerizar não é "empacotar". É repensar como a aplicação se relaciona com o ambiente — variáveis de ambiente em vez de arquivos, logs no stdout em vez de arquivos, estado em volumes em vez de no filesystem.