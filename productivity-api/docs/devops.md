# ⚙️ DevOps — Visão Consolidada

Visão geral das práticas DevOps aplicadas à Productivity API e relação com os cinco desafios da disciplina.

---

## Por que DevOps neste projeto?

A Productivity API começou como um exercício didático de Spring Boot, mas serve como **caso de estudo realista** para os desafios de DevOps. A premissa: *"funciona na minha máquina"* não é entrega — entrega é o código rodando em produção, com confiança de que continuará rodando.

Os cinco desafios da disciplina cobrem os pilares modernos:

| Pilar | Desafio | Pergunta que responde |
|---|---|---|
| **Automação** | [01](challengs/challenge-01.md) | Como entregar mudanças sem clique humano? |
| **Containerização** | [02](challengs/challenge-02.md) | Como garantir que rode igual em qualquer lugar? |
| **Orquestração** | [03](challengs/challenge-03.md) | Como escalar e atualizar sem downtime? |
| **Observabilidade (logs)** | [04](challengs/challenge-04.md) | Quando der errado, como entender o que aconteceu? |
| **Observabilidade 360°** | [05](challengs/challenge-05.md) | Como saber que algo vai dar errado antes de dar? |

---

## Princípios Norteadores

### 1. Infraestrutura como Código

Tudo que define o ambiente — `Dockerfile`, `docker-compose.yml`, Helm Charts, workflows do GitHub Actions, configuração do Syslog-ng — vive no Git, versionado junto do código da aplicação.

**Razão:** ambiente reprodutível, revisão de mudanças via pull request, rollback simples.

### 2. Pipeline como Único Caminho

Nada vai para staging/produção sem passar pelo pipeline. Não há "deploy manual via SCP" ou "alteração direta no servidor". Se algo precisa mudar em produção, muda no Git primeiro.

**Razão:** auditabilidade, consistência, eliminação de drift.

### 3. Imutabilidade

Containers não são alterados após o build. Se precisa mudar, builda nova imagem, taggeia com SHA do commit, faz o deploy.

**Razão:** rollback = trocar a tag da imagem; debugging = inspecionar a imagem exata que rodou.

### 4. Observabilidade desde o Dia 1

Logs estruturados, métricas expostas via `/actuator/prometheus`, traces correlacionáveis. Não é "vamos adicionar depois" — é parte do design.

**Razão:** quando o problema aparece em produção às 23h, você precisa dos dados, não vai conseguir adicioná-los naquele momento.

### 5. Segurança Embutida

Secrets nunca commitados. Imagens com usuário não-root. TLS em prod. Análise estática de código no pipeline.

**Razão:** segurança como adição é sempre incompleta; como parte do processo é natural.

---

## Mapa da Stack DevOps

```
┌─────────────────────────────────────────────────────────────┐
│                  Desenvolvedor                              │
│                       │                                     │
│                       │ git push                            │
│                       ▼                                     │
│         ┌──────────────────────────┐                        │
│         │   GitHub (código + CI)   │                        │
│         └────────────┬─────────────┘                        │
│                      │                                      │
│                      ▼                                      │
│         ┌──────────────────────────┐                        │
│         │  GitHub Actions          │  [Desafio 01]          │
│         │  ├─ build + test         │                        │
│         │  ├─ análise estática     │                        │
│         │  ├─ docker build + push  │  [Desafio 02]          │
│         │  └─ helm upgrade         │  [Desafio 03]          │
│         └────────────┬─────────────┘                        │
│                      │                                      │
│                      ▼                                      │
│         ┌──────────────────────────┐                        │
│         │  GHCR (registry)         │                        │
│         └────────────┬─────────────┘                        │
│                      │ pull                                 │
│                      ▼                                      │
│         ┌──────────────────────────┐                        │
│         │  Kubernetes Cluster      │  [Desafio 03]          │
│         │  ├─ Deployment (3 pods)  │                        │
│         │  ├─ Service + Ingress    │                        │
│         │  └─ HPA + Probes         │                        │
│         └──────┬───────────────┬───┘                        │
│                │               │                            │
│                ▼               ▼                            │
│       ┌────────────────┐  ┌────────────────┐                │
│       │  Syslog-ng     │  │  Prometheus    │                │
│       │  (logs)        │  │  + Grafana     │                │
│       │ [Desafio 04]   │  │  (métricas)    │                │
│       └────────────────┘  │ [Desafio 05]   │                │
│                           └────────────────┘                │
└─────────────────────────────────────────────────────────────┘
```

---

## Ciclo de Vida de uma Mudança

Como uma alteração trivial (ex.: adicionar um campo na `Task`) atravessa toda a esteira:

```
1. Desenvolvedor cria branch:    git checkout -b feat/task-due-date
2. Implementa + escreve testes:  ./mvnw test
3. Commit + push:                git push -u origin feat/task-due-date
4. Abre Pull Request no GitHub
                  │
                  ▼
5. CI dispara automaticamente:
   ├─ Checkout
   ├─ Setup Java 21 + cache Maven
   ├─ ./mvnw verify (compile + test + análise)
   ├─ Upload de cobertura
   └─ ❌ Bloqueia merge se algo falhar
                  │
                  ▼
6. Revisão humana (code review)
                  │
                  ▼
7. Merge para main
                  │
                  ▼
8. CI/CD dispara em main:
   ├─ Re-roda testes
   ├─ docker build + tag por SHA
   ├─ docker push para GHCR
   ├─ helm upgrade no staging
   ├─ Smoke test no staging
   └─ ⏸️ Aguarda aprovação manual
                  │
                  ▼ (aprovação manual)
9. Deploy em produção:
   ├─ helm upgrade --namespace prod
   ├─ Rolling update (sem downtime)
   ├─ Probes garantem que pods novos estão saudáveis antes de matar os velhos
   └─ Logs + métricas mostram o novo deploy
                  │
                  ▼
10. Algo deu errado?
    └─ helm rollback app-release 1   (volta em segundos)
```

Esse fluxo, que parece complexo, **roda em ~5 minutos** ponta a ponta após o setup inicial. É o que diferencia uma equipe que entrega 10 vezes ao dia de uma que entrega 10 vezes ao ano.

---

## Anti-Padrões que Estamos Evitando

| Anti-padrão | O que faríamos | Por que é ruim |
|---|---|---|
| Deploy manual via SSH | `helm upgrade` no pipeline | Drift, irrepetibilidade, single point of human failure |
| Branch `production` separado | Trunk-based (main = single source of truth) | Conflitos, divergência, "merge hell" |
| Credenciais no `application.yml` | Variáveis de ambiente + GitHub Secrets | Credenciais vazadas no Git são incidente de segurança |
| Logs só no `stdout` do pod | Centralização via Syslog-ng | Pod morre, logs somem |
| "Vamos testar em produção" | Staging que espelha produção | Surpresas caras, downtime |
| `latest` como tag de imagem | SHA do commit + tag semântica | Não dá pra saber o que rodou; rollback fica adivinhação |
| `ddl-auto: update` em produção | Migrations versionadas (Flyway) | Mudança de schema acidental, dados perdidos |

---

## Cultura > Ferramentas

DevOps não é "instalar Kubernetes". É:

1. **Quebrar silos:** quem desenvolve também opera, quem opera também influencia o design.
2. **Automatizar o que dói:** se algo manual quebra com frequência, é prioridade automatizar.
3. **Aprender com falhas:** post-mortem sem culpa, foco em melhorar o sistema.
4. **Medir tudo:** decisões baseadas em dados (métricas, SLOs), não em opinião.
5. **Iterar pequeno:** mudanças pequenas e frequentes > mudanças grandes e raras.

Os desafios da disciplina são exercícios concretos desses princípios. Cada um, por si só, é um pedaço — juntos, formam uma esteira DevOps funcional.

---

## Próximos Passos

- **[Desafio 01](challengs/challenge-01.md):** começar pelo CI/CD (é o pré-requisito de tudo).
- **[Desafio 02](challengs/challenge-02.md):** containerizar, depois orquestrar.
- **[Desafio 03](challengs/challenge-03.md):** Helm + Kubernetes.
- **[Desafio 04](challengs/challenge-04.md):** logs centralizados.
- **[Desafio 05](challengs/challenge-05.md):** observabilidade 360° (consolida tudo).

Status detalhado de cada um nos respectivos arquivos.