# 🗂️ Productivity API

> API REST para gerenciamento de tarefas e produtividade pessoal, construída com **Spring Boot 3.5** e **Java 21**. Serve também como base de implementação para os desafios da disciplina de DevOps do **Prof. Cloves Rocha** (UNINASSAU Olinda).

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.14-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

---

## 📑 Sumário

- [Visão Geral](#-visão-geral)
- [Stack Técnica](#-stack-técnica)
- [Estrutura do Projeto](#-estrutura-do-projeto)
- [Como Executar](#-como-executar)
- [Endpoints](#-endpoints)
- [Perfis (dev / prod)](#-perfis-de-execução)
- [Testes](#-testes)
- [Documentação Estendida](#-documentação-estendida)
- [Roadmap](#-roadmap)
- [Licença](#-licença)

---

## 🎯 Visão Geral

A **Productivity API** expõe operações CRUD sobre tarefas (`Task`), com:

- **Status** (`PENDING`, `IN_PROGRESS`, `COMPLETED`) e **prioridade** (`LOW`, `MEDIUM`, `HIGH`)
- **Listagem paginada** com filtros por status, prioridade e busca por título
- **Validação** completa via Bean Validation (`@NotBlank`, `@NotNull`, `@Size`)
- **Tratamento de erros padronizado** (`ApiError` com status, código, mensagem, path e timestamp)
- **Regra de negócio:** ao mover uma tarefa para `COMPLETED`, o campo `completedAt` é preenchido automaticamente; ao reabrir, é limpo
- **Documentação OpenAPI** gerada automaticamente via Springdoc

---

## 🧰 Stack Técnica

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.5.14 |
| Persistência | Spring Data JPA + Hibernate 6.6 |
| Banco (dev) | H2 (in-memory) |
| Banco (prod) | PostgreSQL (configurável via variáveis de ambiente) |
| Validação | Spring Validation (Jakarta Validation) |
| Documentação API | Springdoc OpenAPI 2.7 |
| Build | Maven (via wrapper `./mvnw`) |
| Utilitários | Lombok, DevTools |
| Testes | JUnit 5 + Mockito + MockMvc |

---

## 📁 Estrutura do Projeto

```
productivity-api/
├── docs/                              # Documentação técnica (este projeto)
│   ├── api.md                         # Referência completa dos endpoints
│   ├── architecture.md                # Arquitetura em camadas
│   ├── decisions.md                   # ADRs — registros de decisão
│   ├── devops.md                      # Visão DevOps consolidada
│   ├── roadmap.md                     # Evolução planejada
│   ├── setup.md                       # Setup detalhado do ambiente
│   └── challengs/                     # Desafios da disciplina
│       ├── challenge-01.md            # CI/CD com GitHub Actions
│       ├── challenge-02.md            # Containerização Docker
│       ├── challenge-03.md            # Helm + Kubernetes
│       ├── challenge-04.md            # Logging com Syslog-ng
│       └── challenge-05.md            # Observabilidade 360°
├── src/main/java/com/github/joaovictor/productivity_api/
│   ├── ProductivityApiApplication.java
│   ├── controller/                    # REST controllers
│   ├── service/                       # Regras de negócio
│   ├── repository/                    # Spring Data repositories
│   ├── domain/                        # Entidades, enums e DTOs
│   │   ├── dto/request/
│   │   ├── dto/response/
│   │   ├── dto/mapper/
│   │   └── enums/
│   └── exception/                     # GlobalExceptionHandler + ApiError
├── src/main/resources/
│   ├── application.yml                # Configuração base
│   ├── application-dev.yml            # Profile dev (H2)
│   ├── application-prod.yml           # Profile prod (Postgres)
│   └── logback-spring.xml             # Configuração de logging
└── src/test/java/                     # Testes unitários e de integração
```

---

## 🚀 Como Executar

### Pré-requisitos

- Java 21+ (`java -version`)
- Git
- (Opcional) Docker + Docker Compose, para os perfis de orquestração nos desafios

### Clonar e rodar

```bash
git clone https://github.com/JoaoAzevedo184/productivity-api.git
cd productivity-api

# Roda em modo dev (H2 em memória)
./mvnw spring-boot:run
```

Aplicação disponível em **http://localhost:8080**.

### Recursos úteis em dev

| Recurso | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| H2 Console | http://localhost:8080/h2-console |

**Login do H2 Console:**

| Campo | Valor |
|---|---|
| JDBC URL | `jdbc:h2:mem:taskdb-dev` |
| User | `sa` |
| Password | `sa` |

---

## 🛣️ Endpoints

Resumo. A referência completa com exemplos de payload e respostas está em [`docs/api.md`](docs/api.md).

| Método | Path | Descrição |
|---|---|---|
| `GET` | `/tasks` | Lista paginada de tarefas |
| `GET` | `/tasks/{id}` | Busca tarefa por ID |
| `POST` | `/tasks` | Cria nova tarefa |
| `PUT` | `/tasks/{id}` | Atualiza tarefa (parcial) |
| `DELETE` | `/tasks/{id}` | Remove tarefa |
| `GET` | `/tasks/status/{status}` | Filtra por status (paginado) |
| `GET` | `/tasks/priority/{priority}` | Filtra por prioridade (paginado) |
| `GET` | `/tasks/search?title=...` | Busca por título (contains, case-insensitive) |

### Parâmetros de paginação

Todos os endpoints de listagem aceitam:

```
?page=0&size=20&sort=createdAt,desc
```

Padrão: `size=20`, ordenado por `createdAt` decrescente.

### Exemplo rápido (curl)

```bash
# Criar
curl -X POST http://localhost:8080/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"Estudar Spring","description":"Revisar JPA","status":"PENDING","priority":"HIGH"}'

# Listar
curl http://localhost:8080/tasks

# Concluir (seta completedAt automaticamente)
curl -X PUT http://localhost:8080/tasks/1 \
  -H "Content-Type: application/json" \
  -d '{"status":"COMPLETED"}'
```

### Collection Postman

O repositório inclui `productivity-api.postman_collection.json` com 27 requests organizados em três pastas: *Tasks - Success*, *Error Cases* e *Health & Docs*.

---

## ⚙️ Perfis de Execução

| Profile | Banco | DDL | Quando usar |
|---|---|---|---|
| `dev` (default) | H2 in-memory | `update` | Desenvolvimento local |
| `prod` | PostgreSQL | `validate` | Ambientes containerizados / produção |

### Ativar perfil de produção

```bash
# Via variável de ambiente
SPRING_PROFILES_ACTIVE=prod \
DB_URL=jdbc:postgresql://localhost:5432/productivity \
DB_USER=postgres \
DB_PASSWORD=secret \
./mvnw spring-boot:run
```

Em produção o DDL fica em `validate` — o schema é gerenciado por **migrações externas** (Flyway/Liquibase planejado no roadmap).

---

## 🧪 Testes

```bash
# Roda todos os testes
./mvnw test

# Roda com relatório de cobertura (quando JaCoCo for adicionado)
./mvnw test jacoco:report
# Abrir target/site/jacoco/index.html
```

Estrutura de testes prevista:

- **Unitários:** `TaskService` com Mockito (mock do repositório)
- **Integração de controller:** `@WebMvcTest` com `MockMvc`
- **Integração de repositório:** `@DataJpaTest` (futuro)

---

## 📚 Documentação Estendida

Documentos detalhados em [`docs/`](docs/):

- **[api.md](docs/api.md)** — referência completa dos endpoints com payloads e respostas
- **[architecture.md](docs/architecture.md)** — arquitetura em camadas, fluxo de uma requisição, padrões adotados
- **[decisions.md](docs/decisions.md)** — ADRs (Architecture Decision Records) registrando o "porquê" de cada escolha técnica
- **[devops.md](docs/devops.md)** — visão consolidada das práticas DevOps aplicadas ao projeto
- **[roadmap.md](docs/roadmap.md)** — evolução planejada (migrations, observabilidade, autenticação, etc.)
- **[setup.md](docs/setup.md)** — guia passo a passo para configurar o ambiente local
- **[challengs/](docs/challengs/)** — implementação dos cinco desafios DevOps da disciplina

---

## 🗺️ Roadmap

Visão resumida. Detalhes em [`docs/roadmap.md`](docs/roadmap.md).

- [x] CRUD completo de tarefas
- [x] Paginação, filtros e busca
- [x] Validação e tratamento de exceções
- [x] Documentação OpenAPI/Swagger
- [x] Profiles dev/prod separados
- [ ] Testes automatizados com cobertura >= 80%
- [ ] Migrations com Flyway
- [ ] Containerização (Docker + docker-compose)
- [ ] Pipeline CI/CD no GitHub Actions
- [ ] Deploy em Kubernetes via Helm
- [ ] Métricas Prometheus + dashboards Grafana
- [ ] Autenticação JWT
- [ ] Tarefas com prazo (`dueDate`) e recorrência

---

## 🎓 Contexto Acadêmico

Este projeto integra a disciplina de **DevOps** ministrada pelo **Prof. Cloves Rocha** no curso de **Bacharelado em Sistemas de Informação** da **UNINASSAU Olinda**.

A productivity-api serve como aplicação-base para os cinco desafios práticos da disciplina, documentados em [`docs/challengs/`](docs/challengs/).

---

## 👤 Autor

**João Victor Azevedo**

- GitHub: [@JoaoAzevedo184](https://github.com/JoaoAzevedo184)

---

## 📄 Licença

Distribuído sob a licença MIT. Veja [LICENSE](LICENSE) para mais informações.