# 🚀 Desafio 01 — Do Commit ao Deployment Automatizado

> **Pipeline completo de CI/CD** com GitHub Actions, garantindo qualidade de código e entrega automatizada em staging/produção.

| Campo | Valor |
|---|---|
| **Status** | 🔲 Planejado |
| **Aplicação-base** | productivity-api (Spring Boot 3.5 + Java 21) |
| **Entrega** | 04 / Março / 2026 |
| **Modo** | Solo |

---

## 🎯 Objetivos

Aplicados à productivity-api:

1. Cada `push` e `pull_request` dispara build, testes e análise estática.
2. Merge na `main` empurra imagem Docker para o GitHub Container Registry (GHCR).
3. Deploy automatizado em **staging**.
4. Deploy em **produção** exige aprovação manual.
5. Observabilidade básica pós-deploy (health check + métricas iniciais).

---

## 👤 Distribuição de Papéis (solo)

Como vou fazer sozinho, cada "papel" do enunciado vira uma etapa do meu próprio fluxo. Trato cada um como um chapéu que coloco em momentos distintos:

| Chapéu | Responsabilidade | Quando uso |
|---|---|---|
| **CI/CD Engineer** | Workflow `.github/workflows/ci-cd.yml` | Início do desafio |
| **Backend Dev** | Garantir que a app tem testes suficientes | Antes do pipeline |
| **Container Specialist** | Dockerfile otimizado | Junto com [Desafio 02](challenge-02.md) |
| **QA/Quality** | Configurar coverage gate, linter | Refinamento do pipeline |
| **Observability Eng.** | Logs + health check expostos | Etapa final |

---

## 🛠️ Plano de Implementação

### Etapa 1 — Preparar a aplicação para CI

Antes do pipeline, garantir que a app tem o mínimo:

- [ ] Suite de testes funcional (`./mvnw test` passa)
- [ ] Endpoint de health: `/actuator/health` (adicionar `spring-boot-starter-actuator`)
- [ ] Cobertura mínima alvo: **80%** (configurar JaCoCo no `pom.xml`)
- [ ] Linter: usar **Spotless** ou **Checkstyle** com config minimalista

**Tarefas:**

```bash
# Adicionar Actuator ao pom.xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

# Adicionar plugin JaCoCo
# (snippet completo no roadmap quando implementar)
```

---

### Etapa 2 — Workflow CI (Continuous Integration)

**`.github/workflows/ci.yml`** — roda em todo push/PR.

```yaml
name: CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'maven'

      - name: Build + test
        run: ./mvnw -B verify

      - name: Upload coverage report
        if: success()
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: target/site/jacoco/

      - name: Coverage gate (>= 80%)
        run: |
          # Falha o build se cobertura cair abaixo de 80%
          # Implementação detalhada quando JaCoCo estiver configurado
          echo "Coverage gate placeholder"
```

**Pontos-chave:**

- `actions/setup-java@v4` com cache Maven reduz tempo de build em ~70%.
- `./mvnw -B verify` roda compile + test + integration test em sequência (`-B` = batch mode, sem prompts).
- Upload de artifact permite inspecionar relatório de cobertura mesmo sem Codecov.

---

### Etapa 3 — Workflow CD (Continuous Delivery)

**`.github/workflows/cd.yml`** — dispara após merge na `main`.

```yaml
name: CD

on:
  push:
    branches: [ main ]
  workflow_dispatch:  # permite trigger manual

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4

      - name: Setup JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'maven'

      - name: Build JAR
        run: ./mvnw -B package -DskipTests

      - name: Login to GHCR
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push image
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:sha-${{ github.sha }}
            ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

  deploy-staging:
    needs: build-and-push
    runs-on: ubuntu-latest
    environment: staging
    steps:
      - name: Deploy to staging
        run: echo "Deploy via helm/kubectl — ver Desafio 03"

  deploy-production:
    needs: deploy-staging
    runs-on: ubuntu-latest
    environment:
      name: production
      # 🚨 Approval required — configurado no GitHub Settings → Environments
    steps:
      - name: Deploy to production
        run: echo "Deploy via helm/kubectl — ver Desafio 03"
```

**Como configurar o approval gate:**

1. **Settings → Environments → New environment** → "production".
2. ✅ "Required reviewers" → adicionar seu próprio usuário (em equipe real, seriam outras pessoas).
3. ✅ "Deployment branches" → restringir para `main`.

Quando o pipeline chegar nesse job, GitHub envia notificação e aguarda clique manual em "Approve and deploy".

---

### Etapa 4 — Observabilidade básica pós-deploy

Para satisfazer o requisito de "demonstrar que a aplicação está saudável":

- **Adicionar Actuator** com endpoints expostos:

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  endpoint:
    health:
      show-details: when-authorized
```

- **Smoke test no pipeline** após deploy de staging:

```yaml
- name: Smoke test staging
  run: |
    curl -fsSL https://staging.productivity-api.local/actuator/health \
      | jq -e '.status == "UP"'
```

Falha o pipeline se `/actuator/health` não retornar `UP` em 30 segundos.

---

## ✅ Critérios de Avaliação (do enunciado)

| # | Critério | Como vou atender |
|---|---|---|
| 1 | Automação extensiva | Pipeline executa build → test → push → deploy staging sem clique humano |
| 2 | Pipeline < 10 min | Cache Maven + GHA cache do Docker; meta < 5 min |
| 3 | Falhas bloqueiam deploy | `needs:` entre jobs; teste falho impede `build-and-push` |
| 4 | YAML claro em `.github/workflows/` | Dois arquivos: `ci.yml` (PR/push) e `cd.yml` (main only) |
| 5 | Cultura DevOps (em equipe) | N/A no modo solo; documento o processo no README |

---

## 📂 Estrutura Final do Projeto

```
productivity-api/
├── .github/
│   └── workflows/
│       ├── ci.yml                  # build + test em todo push/PR
│       └── cd.yml                  # deploy em merge na main
├── Dockerfile                       # multi-stage (Desafio 02)
├── docker-compose.yml               # ambiente local com Postgres
└── (resto do projeto)
```

---

## 🧪 Como Testar Localmente

Antes de subir o workflow:

```bash
# 1. Garantir que o build local passa
./mvnw -B verify

# 2. Testar com act (executor local do GitHub Actions)
# Instalar: https://github.com/nektos/act
act push -W .github/workflows/ci.yml
```

> **Atenção:** `act` simula bem, mas não é 100% idêntico ao GitHub runner. Sempre validar com um push real antes de confiar.

---

## 🔐 Secrets Necessários

Configurar em **Settings → Secrets and variables → Actions**:

| Secret | Uso | Vem de onde |
|---|---|---|
| `GITHUB_TOKEN` | Push para GHCR | Automático |
| `KUBECONFIG` | Acesso ao cluster (Desafio 03) | `kubectl config view --raw --minify` |
| `STAGING_URL` | Smoke test pós-deploy | Definido por mim |

---

## 📌 Status e Próximos Passos

**Concluído:**

- [ ] Nada ainda — está na fila.

**A fazer (ordem):**

1. Adicionar `spring-boot-starter-actuator` + endpoints expostos
2. Configurar JaCoCo com gate de 80%
3. Criar `.github/workflows/ci.yml`
4. Validar primeiro PR passando pelo pipeline
5. Criar `.github/workflows/cd.yml` (depende do Dockerfile do [Desafio 02](challenge-02.md))
6. Configurar environments (staging/production) no GitHub
7. Documentar logs do primeiro deploy bem-sucedido

---

## 📚 Referências

- [GitHub Actions — Docs oficiais](https://docs.github.com/en/actions)
- [docker/build-push-action](https://github.com/docker/build-push-action)
- [actions/setup-java](https://github.com/actions/setup-java)
- [JaCoCo Maven Plugin](https://www.jacoco.org/jacoco/trunk/doc/maven.html)
- [GitHub Environments — approval gates](https://docs.github.com/en/actions/deployment/targeting-different-environments/using-environments-for-deployment)

> 💡 **Lição transversal:** o pipeline é o caminho **mais curto** entre código e produção. Toda fricção adicionada nele se paga em qualidade, mas precisa ser justificada — cada etapa deve responder à pergunta "o que esta verificação previne?".