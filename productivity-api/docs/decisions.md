# 📋 Architecture Decision Records (ADRs)

Registros das decisões técnicas relevantes do projeto, com contexto e justificativa.

> **Formato:** ADR enxuto inspirado em [Michael Nygard](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions). Cada decisão tem: contexto, decisão, consequências.

---

## ADR-001 — Spring Boot 3.5 + Java 21

**Status:** Aceito

**Contexto:** Era necessário escolher framework e versão de linguagem para a API. As opções consideradas foram Spring Boot 3.x com Java 17 ou Java 21.

**Decisão:** Spring Boot 3.5.14 + Java 21 (LTS).

**Consequências:**

- Acesso a recursos modernos da linguagem (records, pattern matching, virtual threads — disponível via configuração).
- LTS até 2031, garantindo longevidade.
- Spring Boot 3.5 traz suporte nativo a `RestClient`, observability framework e melhorias no `@ConfigurationProperties`.
- Trade-off: ferramentas e bibliotecas mais antigas podem não suportar Java 21 (não foi um problema no escopo atual).

---

## ADR-002 — DTOs com `record`

**Status:** Aceito

**Contexto:** O padrão tradicional usa classes POJO (com Lombok ou getters/setters manuais) para DTOs. O Java 14+ introduziu `record` como alternativa imutável e concisa.

**Decisão:** Todos os DTOs (`CreateTaskRequest`, `UpdateTaskRequest`, `TaskResponse`, `ApiError`) são `record`s.

**Consequências:**

- ✅ Imutabilidade por padrão — DTOs não podem ser modificados acidentalmente após criação.
- ✅ `equals`/`hashCode`/`toString` automáticos.
- ✅ Código drasticamente mais conciso que classes com Lombok.
- ⚠️ Não funciona com frameworks que exigem construtor sem argumentos (Hibernate, por exemplo) — mas DTOs nunca são entidades, então não há conflito.
- ⚠️ Algumas libs antigas tinham problemas com records; Spring Boot 3 + Jackson já suportam nativamente.

---

## ADR-003 — Banco H2 em dev, PostgreSQL em prod

**Status:** Aceito

**Contexto:** Decidir o stack de persistência para dois ambientes distintos.

**Decisão:**

- **Dev:** H2 in-memory (`jdbc:h2:mem:taskdb-dev`) com `ddl-auto: update`.
- **Prod:** PostgreSQL via variáveis de ambiente, com `ddl-auto: validate`.

**Consequências:**

- ✅ Zero setup para começar a desenvolver — basta `./mvnw spring-boot:run`.
- ✅ Console H2 acessível em `/h2-console` para inspeção visual.
- ✅ PostgreSQL é o padrão da indústria; sintaxe SQL próxima ao H2 evita surpresas na promoção.
- ⚠️ Risco de divergência sutil entre H2 e Postgres (ex.: case-sensitivity de identificadores, comportamento de `LIMIT`/`OFFSET`). Mitigação prevista: testes de integração contra Postgres via Testcontainers (roadmap).

---

## ADR-004 — Enums como `STRING` no banco

**Status:** Aceito

**Contexto:** JPA permite mapear enums como `ORDINAL` (índice numérico) ou `STRING` (nome).

**Decisão:** `@Enumerated(EnumType.STRING)` em todos os enums.

**Consequências:**

- ✅ Leitura direta no banco — `COMPLETED` em vez de `2`.
- ✅ Resiliência a reordenação: adicionar `CANCELED` antes de `PENDING` no enum não corrompe dados existentes (com `ORDINAL`, corromperia).
- ⚠️ Ocupa mais bytes (string vs. int). Aceitável para enums com poucos valores.
- ⚠️ Renomear um valor do enum é uma migração de dados; requer cuidado.

---

## ADR-005 — Atualização parcial via `PUT` (não `PATCH`)

**Status:** Aceito (com plano de revisão)

**Contexto:** O método `PUT` semanticamente significa "substituição completa do recurso". `PATCH` é mais adequado para atualização parcial. A API atual aceita `PUT` com campos opcionais.

**Decisão:** Manter `PUT /tasks/{id}` aceitando body parcial, com campos `null` significando "não alterar".

**Consequências:**

- ✅ Simplicidade — um único endpoint cobre atualização total e parcial.
- ✅ Cliente não precisa enviar a entidade completa, evitando race conditions de "campos não enviados foram zerados".
- ⚠️ Viola a semântica REST estrita do `PUT`. Aceitável para um projeto educacional/interno; em uma API pública, considerar:
    - `PUT` exigindo recurso completo + adicionar `PATCH /tasks/{id}` para parcial.
    - Ou usar **JSON Merge Patch** (RFC 7396) sob `PATCH`.

**Revisar quando:** API for exposta externamente ou se houver requisito de conformidade REST estrita.

---

## ADR-006 — Tratamento global de exceções com `@RestControllerAdvice`

**Status:** Aceito

**Contexto:** Spring oferece várias estratégias para tratamento de erros: `@ExceptionHandler` em controllers individuais, `ResponseEntityExceptionHandler`, `@RestControllerAdvice` global.

**Decisão:** `GlobalExceptionHandler` único anotado com `@RestControllerAdvice(basePackages = "com.github.joaovictor.productivity_api")`, retornando sempre o payload padronizado `ApiError`.

**Consequências:**

- ✅ Formato de erro consistente em toda a API.
- ✅ Controllers ficam limpos — só fluxo feliz.
- ✅ O `basePackages` evita interceptar exceções internas do Springdoc, Actuator, etc. (essa restrição foi adicionada após o Springdoc retornar 500 em `/v3/api-docs` por causa do handler genérico).
- ⚠️ Risco de "tudo vira 500" se um handler novo não for adicionado para uma exceção específica. Mitigação: testes que validam cada caminho de erro.

---

## ADR-007 — Regras de negócio no Service, não no Mapper

**Status:** Aceito

**Contexto:** Versão inicial do `TaskMapper` continha lógica de negócio (`if (status == COMPLETED) setCompletedAt(now())`). Isso espalha regra de negócio em uma classe utilitária.

**Decisão:** Mapper só converte (sem lógica condicional além de "se não-nulo, copiar"). Regras de transição de estado vivem no `TaskService`.

**Consequências:**

- ✅ Mapper é reutilizável em qualquer contexto sem efeitos colaterais.
- ✅ Regra de negócio fica testável em isolamento via mocks do repositório.
- ✅ Novas transições (ex.: bloquear `COMPLETED → COMPLETED`) cabem naturalmente no service.
- ✅ O service também ganhou a regra de "ao reabrir, limpar `completedAt`" — que estava ausente na versão inicial e só veio à tona ao refatorar.

---

## ADR-008 — Paginação obrigatória nas listagens

**Status:** Aceito

**Contexto:** Versão inicial dos endpoints `GET /tasks`, `/tasks/status/...`, `/tasks/priority/...` e `/tasks/search` retornavam `List<TaskResponse>` sem paginação.

**Decisão:** Migrar todas as listagens para `Page<TaskResponse>` usando Spring Data `Pageable` + `@PageableDefault(size = 20, sort = "createdAt", direction = DESC)`.

**Consequências:**

- ✅ Resposta da API limitada por padrão — proteção contra OOM em listagens grandes.
- ✅ Ordenação e tamanho controláveis pelo cliente via `?page=`, `?size=`, `?sort=`.
- ✅ Resposta inclui metadados (`totalElements`, `totalPages`, `first`, `last`).
- ⚠️ **Breaking change** — clientes que esperavam array recebem objeto `Page`. Mitigado por estarmos em `0.0.1-SNAPSHOT` sem clientes externos ainda.

---

## ADR-009 — Springdoc 2.7 (versão compatível com Spring Framework 6.2)

**Status:** Aceito

**Contexto:** Versão inicial do `pom.xml` (gerada pelo Initializr quando o Spring Boot atual era 3.4) trazia Springdoc 2.5.0. Após atualizar para Spring Boot 3.5 (que depende de Spring Framework 6.2), o endpoint `/v3/api-docs` passou a retornar 500 com `NoSuchMethodError: ControllerAdviceBean.<init>(Object)` — o construtor foi removido entre Spring 6.1 e 6.2.

**Decisão:** Atualizar `springdoc-openapi-starter-webmvc-ui` para `2.7.0`.

**Consequências:**

- ✅ Swagger UI e OpenAPI spec voltam a funcionar.
- ✅ Lição registrada: sempre verificar compatibilidade de bibliotecas não gerenciadas pelo `spring-boot-dependencies` ao subir versão do Boot.

---

## ADR-010 — Logback com `<include>` dos defaults do Spring Boot

**Status:** Aceito

**Contexto:** O `logback-spring.xml` customizado usava `%clr(...)` (conversor de cor) que é registrado pelo Spring Boot, mas só quando os defaults dele são carregados. Sem o `<include>`, o Logback quebrava com "no conversion class registered for [clr]".

**Decisão:** Incluir `<include resource="org/springframework/boot/logging/logback/defaults.xml"/>` no topo do `logback-spring.xml`.

**Consequências:**

- ✅ Conversores do Spring Boot (cor, exceção formatada) disponíveis.
- ✅ Pattern customizado funcionando.
- 💡 Alternativa considerada e rejeitada por simplicidade: configurar tudo via `application.yml` (`logging.pattern.console`) e remover o XML.

---

## Como adicionar uma nova ADR

1. Próximo número sequencial.
2. Título curto e descritivo.
3. Status: **Proposto**, **Aceito**, **Rejeitado**, **Substituído por ADR-XYZ**.
4. Três seções obrigatórias: contexto, decisão, consequências.
5. Decisões revisadas não são apagadas — mudam status para **Substituído** e linkam para a nova.