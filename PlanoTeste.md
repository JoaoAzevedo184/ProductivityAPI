# 🧪 Plano de Teste — productivity-api

**Disciplina:** Teste de Software (20261) · **Projeto AV2**
**Aplicação sob teste:** `productivity-api` — API REST de gerenciamento de tarefas (Spring Boot 3.5 · Java 21)
**Repositório:** github.com/JoaoAzevedo184/productivity-api

---

## 👥 Equipe

| Nome | Matrícula |
|---|---|
| João Victor Azevedo de Sena | 01707269 |
| Vinicius Alencar Murta Costa | 01583753 |
| Paulo Gabriel Morais Santana da Silva | 01710246 |
| Gabriel Leal de Andrade Pereira | 01696408 |
| Edilson Pereira da Silva Filho | 01704733 |

---

## 1. Objetivo

Validar a corretude funcional e estrutural da `productivity-api`, aplicando duas técnicas complementares de teste:

- **Caixa preta (black-box):** valida o comportamento observável da API pela sua interface HTTP, sem conhecimento do código interno. Baseia-se no contrato documentado (`docs/api.md`).
- **Caixa branca (white-box):** valida a lógica interna das classes de serviço, exercitando caminhos, ramos e regras de negócio com conhecimento da implementação.

O alvo principal é o domínio de tarefas (`Task`): criação, atualização, exclusão, busca, validação de entrada e instrumentação de métricas.

---

## 2. Escopo

### Em escopo

| Componente | Tipo de teste |
|---|---|
| `TaskController` (endpoints REST) | Caixa preta (integração HTTP) |
| Validação de DTOs (`CreateTaskRequest`, `UpdateTaskRequest`) | Caixa preta (valor-limite, partição) |
| `GlobalExceptionHandler` (mapeamento de erros) | Caixa preta |
| `TaskService` (regras de negócio) | Caixa branca (unitário) |
| Regra de `completedAt` (transições de status) | Caixa branca |
| Instrumentação Micrometer (`tasks_created_total`, `tasks_completion_duration_seconds`) | Caixa branca |

### Fora de escopo

- Testes de carga/performance (mencionados no roadmap, milestone 10).
- Testes de segurança/autenticação (não há auth no escopo atual).
- Testes de infraestrutura DevOps (cobertos no seminário da outra disciplina).

---

## 3. Estratégia e técnicas

### 3.1 Caixa preta

Aplicamos três técnicas clássicas:

**Particionamento de equivalência.** As entradas são divididas em classes que devem ser tratadas de forma idêntica. Exemplo: para o campo `status` de path param, há duas partições — valores válidos do enum (`PENDING`, `IN_PROGRESS`, `COMPLETED`) e qualquer valor fora dele.

**Análise de valor-limite (Boundary Value Analysis).** Testa as fronteiras das partições, onde defeitos costumam se concentrar. O campo `title` tem `@Size(max = 100)` e `@NotBlank`; as fronteiras testadas são: 0 chars (inválido), 1 char (menor válido), 100 chars (maior válido), 101 chars (primeiro inválido acima).

**Teste de tabela de decisão (implícito).** As transições de status que afetam `completedAt` formam uma tabela de decisão (status anterior × novo status → efeito), testada exaustivamente.

### 3.2 Caixa branca

**Cobertura de ramos e caminhos.** Exercitamos os ramos do método `TaskService.update()`, especialmente os condicionais que decidem o tratamento de `completedAt`:

```
if (request.status() != null && request.status() != statusAnterior) {
    if (request.status() == COMPLETED) { ... }   // ramo A
    else                              { ... }     // ramo B
}                                                  // ramo C (não entra)
```

Cada ramo (A, B e o caminho que não entra no `if`) tem caso de teste correspondente.

**Verificação de estado de instrumentação.** Inspecionamos diretamente o `MeterRegistry` após exercer o código, validando que os meters foram criados e incrementados — um caminho interno invisível pela caixa preta.

### 3.3 Ferramentas

| Ferramenta | Uso |
|---|---|
| JUnit 5 | Framework de teste |
| Mockito | Mock do `TaskRepository` em testes unitários |
| AssertJ | Asserções fluentes |
| Spring MockMvc | Simulação de requisições HTTP (caixa preta sem servidor real) |
| `SimpleMeterRegistry` (Micrometer) | Registry real em memória para testar métricas |
| JaCoCo | Medição de cobertura (gate de 60% no `pom.xml`) |

---

## 4. Casos de teste

### 4.1 Caixa preta — Endpoints e contrato (`TaskApiIntegrationTest`)

| ID | Caso | Entrada | Resultado esperado |
|---|---|---|---|
| CP-01 | Listar tarefas | `GET /tasks` | 200, corpo com array `content` e `totalElements` |
| CP-02 | Criar tarefa válida | `POST /tasks` com dados completos | 200, corpo com `id` e `title` |
| CP-03 | Criar sem title | `POST /tasks` com `title=""` | 400, `error=VALIDATION_ERROR` |
| CP-04 | Buscar inexistente | `GET /tasks/99999` | 404, `error=NOT_FOUND` |

### 4.2 Caixa preta — Valor-limite na validação (`TaskValidationBoundaryTest`) ⭐ novo

| ID | Caso | Entrada (`title`) | Resultado esperado |
|---|---|---|---|
| VL-01 | Vazio | `""` (0 chars) | 400 `VALIDATION_ERROR` |
| VL-02 | Em branco | `"   "` (só espaços) | 400 `VALIDATION_ERROR` |
| VL-03 | Menor válido | `"A"` (1 char) | 200 |
| VL-04 | Limite superior aceito | 100 chars | 200 |
| VL-05 | Primeiro inválido | 101 chars | 400 `VALIDATION_ERROR` |
| VL-06 | `description` ausente | sem campo no create | 400 `VALIDATION_ERROR` |
| VL-07 | `status` ausente | sem campo no create | 400 `VALIDATION_ERROR` |

### 4.3 Caixa preta — Enums em path param (`TaskEnumPathParamTest`) ⭐ novo

| ID | Caso | Entrada | Resultado esperado |
|---|---|---|---|
| EN-01 | Status válido | `GET /tasks/status/PENDING` | 200 |
| EN-02 | Status inválido | `GET /tasks/status/INVALIDO` | 400 `INVALID_PARAMETER`, mensagem lista valores |
| EN-03 | Status caixa errada | `GET /tasks/status/pending` | 400 (case-sensitive) |
| EN-04 | Priority válida | `GET /tasks/priority/HIGH` | 200 |
| EN-05 | Priority inválida | `GET /tasks/priority/URGENTE` | 400 `INVALID_PARAMETER` |

### 4.4 Caixa branca — Regras de negócio do serviço (`TaskServiceTest`)

| ID | Caso | Foco | Resultado esperado |
|---|---|---|---|
| CB-01 | Criar e retornar response | `create()` mapeia campos | response com mesmos campos |
| CB-02 | Não setar `completedAt` na criação | regra mora no `update`, não no `create` | `completedAt` null mesmo se criada COMPLETED |
| CB-03 | Atualizar só campos não-nulos | atualização parcial | campos omitidos inalterados |
| CB-04 | Setar `completedAt` ao concluir | ramo A do `update` | `completedAt` preenchido |
| CB-05 | Limpar `completedAt` ao reabrir (→PENDING) | ramo B | `completedAt` null |
| CB-06 | Limpar `completedAt` (→IN_PROGRESS) | ramo B | `completedAt` null |
| CB-07 | Transição neutra não mexe em `completedAt` | caminho que não entra no `if` | inalterado |
| CB-08 | Re-envio do mesmo status preserva `completedAt` | guarda `status != statusAnterior` | inalterado |
| CB-09 | Update de id inexistente | exceção | `ResourceNotFoundException`, sem `save` |
| CB-10 | Delete existente | fluxo feliz | `deleteById` chamado |
| CB-11 | Delete inexistente | exceção | `ResourceNotFoundException`, sem `deleteById` |
| CB-12 | FindById existente / inexistente | fluxo + exceção | response / `ResourceNotFoundException` |
| CB-13 | Paginação e filtros | `findAll`, `findByStatus`, `findByPriority`, `searchByTitle` | páginas corretas |

> O `TaskServiceTest` contém ~20 casos no total (incluindo variações aninhadas). A tabela acima agrupa por regra.

### 4.5 Caixa branca — Métricas de negócio (`TaskMetricsTest`) ⭐ novo

| ID | Caso | Foco | Resultado esperado |
|---|---|---|---|
| MT-01 | Contador por prioridade | `tasks_created_total{priority}` | counter HIGH = 1.0 |
| MT-02 | Contadores separados por tag | duas prioridades distintas | LOW=2.0, HIGH=1.0 |
| MT-03 | Timer registra na conclusão | transição real → COMPLETED | timer count = 1 |
| MT-04 | Timer não registra em transição neutra | PENDING→IN_PROGRESS | timer count = 0 |

### 4.6 Caixa preta — Listagem, filtros e ciclo CRUD (`TaskControllerIntegrationTest`) ⭐ novo

| ID | Caso | Entrada | Resultado esperado |
|---|---|---|---|
| CT-01 | Filtrar por status | `GET /tasks/status/PENDING` | 200, página com `content` |
| CT-02 | Filtrar por prioridade | `GET /tasks/priority/HIGH` | 200, página com `content` |
| CT-03 | Buscar por título | `GET /tasks/search?title=a` | 200, página (contains case-insensitive) |
| CT-04 | Paginação custom | `GET /tasks?size=5&sort=title,asc` | 200, `size=5` respeitado |
| CT-05 | Ciclo CRUD completo | criar → PUT COMPLETED → GET → DELETE → GET | 200/200/200/204/404, `completedAt` preenchido |
| CT-06 | Deletar inexistente | `DELETE /tasks/99999` | 404 `NOT_FOUND` |
| CT-07 | Atualizar inexistente | `PUT /tasks/99999` | 404 `NOT_FOUND` |

---

## 5. Casos de teste novos — justificativa

Os arquivos marcados com ⭐ foram criados especificamente para esta AV2, preenchendo lacunas reais identificadas na análise do código:

1. **Valor-limite no `title`** — a suíte original validava `title=""`, mas nunca as fronteiras de 100/101 caracteres do `@Size(max = 100)`. É o caso de teste de caixa preta mais clássico e estava ausente.

2. **Enums inválidos em path param** — o comportamento (`400 INVALID_PARAMETER` com lista de valores aceitos) estava documentado em `docs/api.md` e implementado no `GlobalExceptionHandler`, porém sem teste automatizado que o protegesse contra regressão.

3. **Instrumentação Micrometer** — o `TaskService` cria e incrementa meters de negócio, mas nenhum teste verificava se a métrica era de fato registrada com a tag correta. Como essas métricas alimentam o dashboard Grafana do projeto, sua corretude importa.

4. **Listagem, filtros e ciclo CRUD** — o `TaskController` tinha apenas 55% de cobertura porque os endpoints de filtro (`findByStatus`, `findByPriority`, `search`) e o ciclo completo de atualização/exclusão não eram exercitados pela borda HTTP. O novo teste levou o controller a 100%.

### 5.1 Convergência entre análise estática e teste dinâmico

Durante a execução, a análise estática (SpotBugs) sinalizou um possível *null pointer dereference* no método `GlobalExceptionHandler.handleTypeMismatch` — exatamente o método exercitado pelos casos **EN-02** e **EN-05** (enums inválidos). A correção (extrair `getRequiredType()` numa variável local e usar `String.valueOf` para tratar null) foi validada rodando o `TaskEnumPathParamTest`, que continuou verde.

Isso demonstra a **complementaridade entre teste estático e dinâmico**: o SpotBugs apontou o risco na estrutura do código, e o teste de caixa preta confirmou que o comportamento observável permaneceu correto após a correção. Duas técnicas distintas de qualidade convergindo no mesmo ponto.

---

## 6. Como executar

### Rodar toda a suíte com cobertura

```bash
cd productivity-api
./mvnw clean verify
```

O `verify` dispara os testes e o JaCoCo. O build falha se a cobertura de linhas cair abaixo de 60% (gate configurado no `pom.xml`).

### Rodar apenas os testes novos desta AV2

```bash
./mvnw test -Dtest="TaskValidationBoundaryTest,TaskEnumPathParamTest,TaskMetricsTest"
```

### Rodar um teste específico

```bash
./mvnw test -Dtest="TaskValidationBoundaryTest"
```

### Relatório de cobertura

Após `./mvnw verify`, abrir:

```
target/site/jacoco/index.html
```

---

## 7. Critérios de aceitação

- ✅ Todos os casos de teste passam (`BUILD SUCCESS`).
- ✅ **Cobertura medida: 88% de instruções e 77% de ramos** (relatório JaCoCo), acima do gate de 60% e próximo da meta de 80% do roadmap.
- ✅ Análise estática (SpotBugs) sem bugs reais — restam apenas 2 falsos positivos `EI_EXPOSE_REP2` (padrão de injeção de dependência do Spring).
- ✅ Cada técnica (caixa preta e caixa branca) tem casos demonstráveis.
- ✅ As quatro lacunas identificadas (valor-limite, enum, métrica, controller) têm cobertura nova.

### Cobertura por pacote (JaCoCo)

| Pacote | Instruções | Ramos |
|---|---|---|
| `service` | 100% | 90% |
| `controller` | 100% | n/a |
| `domain.dto.mapper` | 87% | 75% |
| `domain.enums` | 100% | n/a |
| `domain.dto.request` | 100% | n/a |
| `domain.dto.response` | 100% | n/a |
| `domain` | 100% | n/a |
| `exception` | 70% | 50% |
| **Total** | **88%** | **77%** |

> O pacote `exception` é o menor — o handler genérico de 500 e o `ConstraintViolationException` não têm teste dedicado. Fora do escopo desta entrega por baixo retorno; registrado como melhoria futura.

---

## 8. Localização dos arquivos

```
productivity-api/src/test/java/com/github/joaovictor/productivity_api/
├── ProductivityApiApplicationTests.java          # smoke (contexto sobe)
├── integration/
│   └── TaskApiIntegrationTest.java               # caixa preta (CP-01..04)
├── service/
│   └── TaskServiceTest.java                       # caixa branca (CB-01..13)
└── boundary/                                      # ⭐ novos (AV2)
    ├── TaskValidationBoundaryTest.java            # caixa preta valor-limite (VL)
    ├── TaskEnumPathParamTest.java                 # caixa preta enums (EN)
    ├── TaskMetricsTest.java                        # caixa branca métricas (MT)
    └── TaskControllerIntegrationTest.java          # caixa preta filtros + CRUD (CT)
```