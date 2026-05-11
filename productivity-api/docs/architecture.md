# 🏛️ Arquitetura

Visão arquitetural da Productivity API.

---

## Visão Geral em Camadas

A aplicação segue uma arquitetura clássica em camadas (Layered Architecture), com responsabilidades bem definidas e dependências unidirecionais (do topo para a base):

```
┌──────────────────────────────────────────────────────────┐
│                      Controller                          │
│        (REST endpoints, validação de entrada)            │
└──────────────────────────┬───────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────┐
│                       Service                            │
│        (Regras de negócio, orquestração, @Transactional) │
└──────────────────────────┬───────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────┐
│                     Repository                           │
│        (Spring Data JPA, queries derivadas)              │
└──────────────────────────┬───────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────┐
│                       Database                           │
│             (H2 em dev, PostgreSQL em prod)              │
└──────────────────────────────────────────────────────────┘
```

### Princípios aplicados

- **Inversão de dependência** — o controller depende do service via interface implícita (injeção de construtor); o service depende do repositório (interface JPA).
- **Single Responsibility** — cada camada tem um motivo único para mudar.
- **DTO Pattern** — entidades JPA nunca atravessam a fronteira HTTP; requests e responses têm DTOs próprios.
- **Mapper isolado** — conversão entre DTO e entidade fica em `TaskMapper` (utility class), sem regras de negócio.

---

## Pacotes

```
com.github.joaovictor.productivity_api
├── ProductivityApiApplication       ← bootstrap Spring Boot
│
├── controller                       ← camada web (HTTP)
│   └── TaskController
│
├── service                          ← regras de negócio
│   └── TaskService
│
├── repository                       ← acesso a dados
│   └── TaskRepository (interface)
│
├── domain                           ← modelo de domínio
│   ├── Task                         ← entidade JPA
│   ├── enums/
│   │   ├── TaskStatus
│   │   └── Priority
│   └── dto/
│       ├── request/
│       │   ├── CreateTaskRequest    ← record com validação
│       │   └── UpdateTaskRequest    ← record com validação opcional
│       ├── response/
│       │   └── TaskResponse         ← record com todos os campos
│       └── mapper/
│           └── TaskMapper           ← Task ↔ DTO
│
└── exception                        ← tratamento global de erros
    ├── ApiError                     ← payload padronizado de erro
    ├── ResourceNotFoundException
    └── GlobalExceptionHandler       ← @RestControllerAdvice
```

---

## Fluxo de uma Requisição

Caso: `PUT /tasks/1` com `{ "status": "COMPLETED" }`

```
1. HTTP Request
      │
      ▼
2. DispatcherServlet → TaskController.update(1, dto)
      │
      │ Spring valida @Valid → se inválido, dispara MethodArgumentNotValidException
      │   (capturado por GlobalExceptionHandler → 400 VALIDATION_ERROR)
      ▼
3. TaskService.update(1, dto) [@Transactional]
      │
      │ a. taskRepository.findById(1) → Optional<Task>
      │    Se vazio: throw ResourceNotFoundException → 404 NOT_FOUND
      │
      │ b. Captura statusAnterior
      │ c. TaskMapper.updateEntity(task, dto) ← só copia campos não-nulos
      │ d. Regra de negócio:
      │      - se status mudou para COMPLETED → completedAt = now()
      │      - se saiu de COMPLETED → completedAt = null
      │ e. taskRepository.save(task) → INSERT/UPDATE no banco
      │ f. TaskMapper.toResponse(saved) → TaskResponse
      ▼
4. ResponseEntity.ok(response) → 200 OK + JSON
```

---

## Padrões e Decisões Chave

### DTO com `record`

DTOs são `record`s do Java 21 — imutáveis por padrão, com `equals`/`hashCode`/`toString` automáticos. Mais conciso que classes com Lombok e sem mutabilidade acidental.

### Atualização parcial idiomática

`UpdateTaskRequest` aceita todos os campos como opcionais. O `TaskMapper.updateEntity` aplica apenas os não-nulos:

```java
if (dto.title() != null) task.setTitle(dto.title());
```

Permite `PUT` parcial sem precisar de `PATCH` ou JSON Merge Patch — solução pragmática para o escopo atual.

### Regra de negócio fora do Mapper

O Mapper só mapeia. A regra do `completedAt` (setar/limpar conforme transição de status) vive no `TaskService.update`. Isso facilita:

- Testes unitários do serviço sem mockar mapper
- Adicionar novas transições (ex.: bloquear `COMPLETED → COMPLETED`)
- Manter o Mapper reutilizável em qualquer contexto

### Tratamento de erros centralizado

`GlobalExceptionHandler` com `@RestControllerAdvice(basePackages = "...")` captura:

- `ResourceNotFoundException` → 404
- `MethodArgumentNotValidException` → 400 com lista de campos inválidos
- `MethodArgumentTypeMismatchException` → 400 com valores aceitos do enum
- `ConstraintViolationException` → 400
- `Exception` (fallback) → 500

O `basePackages` evita interceptar erros do Springdoc/Actuator/etc.

### Transações

`@Transactional(readOnly = true)` na classe do serviço, sobrescrito por `@Transactional` (escrita) em `create`, `update` e `delete`. Convenção: leituras são o caso default; escritas são exceção explícita.

---

## Decisões de Persistência

### H2 em dev, PostgreSQL em prod

- **Dev:** banco em memória (`jdbc:h2:mem:taskdb-dev`) com `ddl-auto: update`. Zero setup, perde dados no restart.
- **Prod:** PostgreSQL via variáveis de ambiente, com `ddl-auto: validate`. Schema é gerenciado externamente (Flyway/Liquibase planejado).

### Enums como STRING

```java
@Enumerated(EnumType.STRING)
private TaskStatus status;
```

Em vez do default `ORDINAL` (que armazena o índice numérico). Vantagens:

- **Legibilidade** — o banco mostra `COMPLETED`, não `2`
- **Resiliência a reordenação** — adicionar `CANCELED` antes de `PENDING` não corrompe dados existentes
- **Trade-off:** ocupa mais bytes; aceitável para enums pequenos

### `@PrePersist` para `createdAt`

O timestamp de criação é setado automaticamente pelo callback JPA, não pelo controller ou service. Garante consistência mesmo em fluxos alternativos (seeds, importações).

---

## Pontos de Extensão Futuros

| Necessidade | Solução planejada |
|---|---|
| Auditoria completa (quem criou/modificou) | Spring Data JPA Auditing (`@CreatedBy`, `@LastModifiedDate`) |
| Migrations versionadas | Flyway |
| Cache de leituras frequentes | Spring Cache + Redis |
| Eventos de domínio (ex.: `TaskCompletedEvent`) | `ApplicationEventPublisher` |
| Múltiplos usuários | Spring Security + JWT + relacionamento `Task → User` |
| Tarefas recorrentes | Coluna `recurrenceRule` (RRULE) + scheduler |

Detalhes em [`roadmap.md`](roadmap.md).

---

## Diagrama Entidade-Relacionamento (atual)

```
┌─────────────────────────────┐
│           tasks             │
├─────────────────────────────┤
│ id            BIGINT  PK    │
│ title         VARCHAR(255)  │
│ description   VARCHAR(500)  │
│ status        VARCHAR(20)   │ ← enum como string
│ priority      VARCHAR(10)   │ ← enum como string
│ created_at    TIMESTAMP     │
│ completed_at  TIMESTAMP NULL│
└─────────────────────────────┘
```

Apenas uma entidade no escopo atual. Com a adição de usuários, virá `users` com FK `tasks.user_id`.