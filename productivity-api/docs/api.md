# 📡 API Reference — Productivity API

Referência completa dos endpoints da Productivity API. Para uma visão geral e exemplos rápidos, veja o [README](../../README.md).

> **Base URL local:** `http://localhost:8080`
> **OpenAPI spec:** `GET /v3/api-docs`
> **Swagger UI:** `/swagger-ui.html`

---

## 📑 Sumário

- [Modelo de Dados](#modelo-de-dados)
- [Paginação e Ordenação](#paginação-e-ordenação)
- [Endpoints de Tarefas](#endpoints-de-tarefas)
- [Tratamento de Erros](#tratamento-de-erros)
- [Códigos HTTP](#códigos-http-utilizados)

---

## Modelo de Dados

### `Task` (resposta da API — `TaskResponse`)

| Campo | Tipo | Descrição |
|---|---|---|
| `id` | `Long` | Identificador único, gerado pelo banco |
| `title` | `String` | Título (máx. 100 chars, obrigatório) |
| `description` | `String` | Descrição (máx. 500 chars, obrigatório no create) |
| `status` | `TaskStatus` | `PENDING`, `IN_PROGRESS`, `COMPLETED` |
| `priority` | `Priority` | `LOW`, `MEDIUM`, `HIGH` |
| `createdAt` | `LocalDateTime` | Preenchido automaticamente na criação |
| `completedAt` | `LocalDateTime` | Preenchido ao transitar para `COMPLETED`; limpo ao reabrir |

### Regras de validação

| Campo | Create (`POST`) | Update (`PUT`) |
|---|---|---|
| `title` | obrigatório, 1–100 chars | opcional, máx. 100 |
| `description` | obrigatório, 1–500 chars | opcional, máx. 500 |
| `status` | obrigatório | opcional |
| `priority` | obrigatório | opcional |

> No `PUT`, qualquer campo omitido permanece inalterado (atualização parcial).

---

## Paginação e Ordenação

Endpoints de listagem (`GET /tasks`, `GET /tasks/status/{status}`, `GET /tasks/priority/{priority}`, `GET /tasks/search`) aceitam três query params:

| Param | Default | Descrição |
|---|---|---|
| `page` | `0` | Número da página (zero-indexed) |
| `size` | `20` | Itens por página |
| `sort` | `createdAt,desc` | Formato `campo,direção` (ex: `title,asc`) |

### Resposta paginada

```json
{
  "content": [ /* array de TaskResponse */ ],
  "pageable": { /* metadados de paginação */ },
  "totalElements": 47,
  "totalPages": 3,
  "number": 0,
  "size": 20,
  "first": true,
  "last": false,
  "empty": false
}
```

---

## Endpoints de Tarefas

### `POST /tasks` — Criar tarefa

**Request body:**

```json
{
  "title": "Estudar Spring Boot",
  "description": "Revisar JPA, validações e tratamento de exceções",
  "status": "PENDING",
  "priority": "HIGH"
}
```

**Response `200 OK`:**

```json
{
  "id": 1,
  "title": "Estudar Spring Boot",
  "description": "Revisar JPA, validações e tratamento de exceções",
  "status": "PENDING",
  "priority": "HIGH",
  "createdAt": "2026-05-10T09:30:00",
  "completedAt": null
}
```

**Erros possíveis:**

| Status | Quando |
|---|---|
| `400` | Campos obrigatórios ausentes ou inválidos |

---

### `GET /tasks` — Listar todas (paginado)

```bash
curl "http://localhost:8080/tasks?page=0&size=10&sort=priority,desc"
```

**Response `200 OK`:** objeto `Page<TaskResponse>` (estrutura na seção de paginação).

---

### `GET /tasks/{id}` — Buscar por ID

```bash
curl http://localhost:8080/tasks/1
```

**Response `200 OK`:** objeto `TaskResponse`.

**Erros:**

| Status | Quando |
|---|---|
| `404` | ID não existe |

---

### `PUT /tasks/{id}` — Atualizar (parcial)

Atualização parcial: campos não enviados ficam inalterados.

**Request body (exemplo — só muda o status):**

```json
{ "status": "COMPLETED" }
```

**Response `200 OK`:** objeto `TaskResponse` atualizado.

#### Regra de negócio do `completedAt`

| Transição de status | Efeito em `completedAt` |
|---|---|
| qualquer → `COMPLETED` | preenchido com `LocalDateTime.now()` |
| `COMPLETED` → qualquer outro | setado para `null` (tarefa reaberta) |
| `PENDING` ↔ `IN_PROGRESS` | inalterado |

**Erros:**

| Status | Quando |
|---|---|
| `400` | Validação (ex.: title > 100 chars) |
| `404` | ID não existe |

---

### `DELETE /tasks/{id}` — Remover

```bash
curl -X DELETE http://localhost:8080/tasks/1
```

**Response `204 No Content`:** sem corpo.

**Erros:**

| Status | Quando |
|---|---|
| `404` | ID não existe |

---

### `GET /tasks/status/{status}` — Filtrar por status

```bash
curl http://localhost:8080/tasks/status/PENDING
curl "http://localhost:8080/tasks/status/IN_PROGRESS?page=0&size=20"
```

**Valores aceitos:** `PENDING`, `IN_PROGRESS`, `COMPLETED` (case-sensitive).

**Erros:**

| Status | Quando |
|---|---|
| `400` | Valor não é um `TaskStatus` válido (com mensagem listando os valores aceitos) |

---

### `GET /tasks/priority/{priority}` — Filtrar por prioridade

```bash
curl http://localhost:8080/tasks/priority/HIGH
```

**Valores aceitos:** `LOW`, `MEDIUM`, `HIGH`.

---

### `GET /tasks/search` — Buscar por título

Busca *contains*, case-insensitive.

```bash
curl "http://localhost:8080/tasks/search?title=spring"
```

| Param | Obrigatório | Descrição |
|---|---|---|
| `title` | sim | Termo de busca |
| `page`/`size`/`sort` | não | Paginação padrão |

---

## Tratamento de Erros

Todas as respostas de erro seguem o mesmo formato (`ApiError`):

```json
{
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Tarefa não encontrada com id: 999",
  "path": "/tasks/999",
  "timestamp": "2026-05-10T09:35:12.123456"
}
```

### Códigos de erro retornados

| `error` | HTTP | Causa |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Campos do body violam `@NotBlank`, `@NotNull` ou `@Size` |
| `INVALID_PARAMETER` | 400 | Path/query param com tipo inválido (ex.: enum inexistente) |
| `CONSTRAINT_ERROR` | 400 | `ConstraintViolationException` (geralmente em query params) |
| `NOT_FOUND` | 404 | `ResourceNotFoundException` (ID inexistente) |
| `INTERNAL_SERVER_ERROR` | 500 | Falha inesperada (handler genérico) |

### Exemplo — erro de validação

**Request:**

```bash
curl -X POST http://localhost:8080/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"","description":"","status":"PENDING","priority":"HIGH"}'
```

**Response `400`:**

```json
{
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "title: O título é obrigatório, description: A descrição é obrigatória",
  "path": "/tasks",
  "timestamp": "2026-05-10T09:36:42.918"
}
```

### Exemplo — enum inválido em path param

**Request:**

```bash
curl http://localhost:8080/tasks/status/INVALIDO
```

**Response `400`:**

```json
{
  "status": 400,
  "error": "INVALID_PARAMETER",
  "message": "Parâmetro 'status' com valor inválido: 'INVALIDO'. Valores aceitos: [PENDING, IN_PROGRESS, COMPLETED]",
  "path": "/tasks/status/INVALIDO",
  "timestamp": "2026-05-10T09:37:01.452"
}
```

---

## Códigos HTTP Utilizados

| Código | Significado | Quando |
|---|---|---|
| `200 OK` | Sucesso com corpo | GETs, POST, PUT |
| `204 No Content` | Sucesso sem corpo | DELETE |
| `400 Bad Request` | Erro do cliente | Validação ou param inválido |
| `404 Not Found` | Recurso não existe | ID inexistente |
| `500 Internal Server Error` | Erro inesperado | Falha do servidor (idealmente nunca) |

---

## Observações

- **Datas e horas** são serializadas no formato ISO-8601 (`yyyy-MM-ddTHH:mm:ss.SSS`) sem timezone — o servidor assume o timezone local.
- **Strings vazias** em campos obrigatórios são rejeitadas via `@NotBlank` (diferente de `null`, ambos falham).
- **Em listagens grandes**, sempre use paginação. Não há proteção contra `size` arbitrariamente alto — em produção, considere limitar via `WebMvcConfigurer`.

Para a referência interativa e geração automática de código-cliente, use o Swagger UI ou a spec OpenAPI em JSON.