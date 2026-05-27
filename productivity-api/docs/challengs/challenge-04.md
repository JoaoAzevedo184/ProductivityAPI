# 📜 Desafio 04 — Logging Centralizado com Syslog-ng

> **Coletar logs de todos os contêineres** num único ponto, organizados por container e data, prontos para auditoria e análise.

| Campo | Valor |
|---|---|
| **Status** | ✅ **Concluído** (resta apenas slides) |
| **Aplicação-base** | productivity-api (imagem do GHCR publicada pelo Desafio 01) |
| **Ferramentas** | Syslog-ng + Docker Compose |
| **Modo** | Solo |

---

## 🎯 Objetivos atingidos

1. ✅ Coletor centralizado de logs com Syslog-ng.
2. ✅ Cada container envia logs via driver `syslog`.
3. ✅ Organização em hierarquia `<container>/<ano>/<mês>/<dia>.log`.
4. ✅ Bucket separado para erros (`_errors/<data>.log`).
5. ✅ Setup reprodutível (`docker compose down -v && up -d` funciona do zero).
6. ✅ Logs sobrevivem aos containers que os geraram.

---

## 🏗️ Arquitetura final

```
   ┌─────────────────────┐    ┌────────────────────┐    ┌──────────────────────┐
   │  productivity-api   │    │                    │    │                      │
   │  productivity-      │    │  Docker driver     │    │  Syslog-ng           │
   │  postgres           │───▶│  syslog (UDP)      │───▶│  Container           │
   │                     │    │  udp://127.0.0.1:  │    │  porta interna 5514  │
   │                     │    │  514               │    │                      │
   └─────────────────────┘    └────────────────────┘    └──────────┬───────────┘
                                                                   │
                                                                   ▼
                                                      ┌────────────────────────┐
                                                      │  /var/log/docker/      │
                                                      │  ├── productivity-api/ │
                                                      │  │   └── 2026/05/      │
                                                      │  │       └── 27.log    │
                                                      │  ├── productivity-     │
                                                      │  │   postgres/...      │
                                                      │  └── _errors/          │
                                                      │      └── 2026-05-27.log│
                                                      └────────────────────────┘
```

---

## 📦 Estrutura entregue

```
productivity-api/
└── syslog-ng/
    ├── docker-compose.yml         # init-logs + syslog-ng + postgres + api
    └── config/
        └── syslog-ng.conf         # sources + destinations + pipelines
```

---

## ✅ O que foi implementado

### 1. `docker-compose.yml` com 4 serviços

| Serviço | Função |
|---|---|
| **`init-logs`** | Container que faz `chown -R 1000:1000 /var/log/docker` e morre. Roda uma vez, garante permissão correta do volume. |
| **`syslog-ng`** | Coletor central. Imagem `linuxserver/syslog-ng`, expõe porta 514 (mapeada pra 5514 interna). |
| **`postgres`** | PostgreSQL 16. Loga via driver `syslog` apontando pro coletor. |
| **`api`** | productivity-api (imagem do GHCR). Loga via driver `syslog`. |

### 2. Logging por serviço (não global)

Cada serviço declara seu próprio driver de log no compose:

```yaml
logging:
  driver: syslog
  options:
    syslog-address: "udp://127.0.0.1:514"
    tag: "productivity-api"
    syslog-format: "rfc5424"
```

Por que **por serviço** em vez de global via `/etc/docker/daemon.json`:
- No Docker Desktop, o daemon é configurado via GUI, não por arquivo.
- Reiniciar o daemon afeta todos os containers do host (incluindo Kind dos Desafios 1-3).
- Logging por serviço fica versionado no compose e isolado da stack.

### 3. `syslog-ng.conf` com pipelines

- **Sources** — TCP e UDP na porta interna **5514** (não 514 — explicado abaixo).
- **Destination `d_logs`** — escreve em `/var/log/docker/${PROGRAM}/${YEAR}/${MONTH}/${DAY}.log`.
- **Destination `d_errors`** — bucket separado para `level(err..emerg)`.
- **Filter `f_errors`** — captura `err..emerg`.
- **Dois pipelines** — todos os logs vão pro `d_logs`; erros TAMBÉM vão pro `d_errors`.

---

## 🧠 Decisões de arquitetura (e por quê)

Estas decisões foram **descobertas durante o debug**. Cada uma resolve um problema real.

### 1. Porta interna **5514**, não 514

A imagem `linuxserver/syslog-ng` roda como usuário não-root (UID 1000). Portas < 1024 (como 514) são privilegiadas no Linux e não podem ser abertas por non-root. Por isso a imagem escuta internamente na **5514**. O compose faz o mapeamento `514:5514` pra preservar a porta padrão no host.

### 2. `udp://127.0.0.1:514`, não `tcp://localhost:514`

Dois problemas resolvidos com essa troca:

- **`localhost` resolve pra `::1` (IPv6) primeiro.** O driver tentava conectar via IPv6, dava timeout, e o container morria com `dial tcp [::1]:514: connect: connection timed out`. `127.0.0.1` força IPv4.
- **TCP exige conexão estabelecida.** Se o syslog-ng não está 100% pronto quando a API tenta logar, o container **morre**. UDP é fire-and-forget — o pacote sai mesmo que o destino ainda não esteja escutando. No primeiro segundo, perde-se algumas mensagens; no segundo seguinte, tudo flui.

### 3. `${PROGRAM}`, não `${HOST}`, no template

No formato **RFC 5424**, o campo `HOST` é o nome da máquina que enviou o log — sempre `docker-desktop`. O `tag` do compose chega como `APP-NAME`, que no syslog-ng é a variável `${PROGRAM}`.

Sem essa correção, todos os logs iriam parar em `/var/log/docker/docker-desktop/...`, agrupados num arquivo só. Com `${PROGRAM}`, ficam separados por container (`productivity-api`, `productivity-postgres`).

### 4. Init container para permissão

O volume `/var/log/docker` é criado pelo Docker como `root:root`. Mas o syslog-ng roda como UID 1000 (por causa do `PUID=1000`). Resultado: `Error opening file for writing; error='Permission denied (13)'`.

A solução: um container `alpine:3.20` minúsculo que sobe **antes** do syslog-ng, faz `chown -R 1000:1000 /var/log/docker`, e morre. O syslog-ng tem `depends_on: condition: service_completed_successfully`, então só sobe depois do init terminar.

Vantagem dessa abordagem: **funciona em qualquer ambiente sem `chown` manual**, inclusive após `docker compose down -v`.

### 5. Sintaxe `file("caminho-com-macros" opcoes...)`, não `template t_logs {...}`

Versões antigas do syslog-ng aceitavam:

```conf
template t_logs { template("/var/log/docker/..."); };
destination d_logs {
    file( template(t_logs) create_dirs(yes) );    # ← sintaxe antiga
};
```

Mas o syslog-ng 4.x rejeita isso com `unexpected KW_TEMPLATE`. A forma idiomática hoje é colocar o template direto na string do `file()`:

```conf
destination d_logs {
    file("/var/log/docker/${PROGRAM}/${YEAR}/${MONTH}/${DAY}.log"
        create_dirs(yes)
    );
};
```

Mais limpo, sem ponteiros indiretos.

### 6. Sem healthcheck no syslog-ng

A imagem `linuxserver/syslog-ng` não traz `netstat`, `ss` nem `nc`. E o syslog-ng escuta em UDP, que não aparece em `/proc/net/tcp`. Tentei três variações de healthcheck e todas falharam por falta de ferramenta ou porque UDP não cria estado de "listening" visível.

Solução: remover o healthcheck e usar `depends_on: condition: service_started` nos outros serviços. Como o syslog-ng sobe em ~2s e UDP não exige conexão, qualquer mensagem perdida no primeiro segundo é tolerável.

---

## 🎬 Comandos e resultados (executados)

### Setup do zero (reprodutível)

```bash
cd syslog-ng

# Down completo
docker compose down -v

# Up — init-logs roda, depois syslog-ng, depois postgres + api
docker compose up -d
```

Saída real:
```
✔ Container syslog-ng-init        Exited      1.6s
✔ Container syslog-ng             Started     1.9s
✔ Container productivity-postgres Started     2.0s
✔ Container productivity-api      Started     2.1s
```

### Gerar tráfego e validar

```bash
# Tráfego normal + um erro 404
curl -s http://localhost:8080/tasks > /dev/null
curl -s http://localhost:8080/tasks/99999 > /dev/null
sleep 5

# Ver os arquivos criados
docker compose exec syslog-ng sh -c "find /var/log/docker -type f"
```

Saída real:
```
/var/log/docker/productivity-api/2026/05/27.log
/var/log/docker/productivity-postgres/2026/05/27.log
/var/log/docker/_errors/2026-05-27.log
```

### Conteúdo dos logs (exemplo real)

```bash
docker compose exec syslog-ng sh -c "tail -5 /var/log/docker/productivity-api/2026/05/27.log"
```

```
May 27 20:22:22 productivity-api[120]: 2026-05-27 20:22:22 INFO  o.f.core.internal.command.DbMigrate - Schema "public" is up to date. No migration necessary.
May 27 20:22:24 productivity-api[120]: 2026-05-27 20:22:24 WARN  o.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration - spring.jpa.open-in-view is enabled by default...
May 27 20:22:25 productivity-api[120]: 2026-05-27 20:22:25 INFO  c.g.j.p.ProductivityApiApplication - Started ProductivityApiApplication in 8.507 seconds
May 27 20:22:25 productivity-api[120]: 2026-05-27 20:22:25 WARN  o.s.c.events.SpringDocAppInitializer - SpringDoc /v3/api-docs endpoint is enabled by default...
```

### Demonstração de resiliência (logs sobrevivem ao container)

```bash
docker compose stop api

# A API foi parada, mas os logs dela continuam acessíveis
docker compose exec syslog-ng sh -c "tail -5 /var/log/docker/productivity-api/2026/05/27.log"
# (mesmo output de antes)
```

**Mensagem-chave:** sem centralização, `docker logs productivity-api` morreria junto com o container. Com o Syslog-ng, os logs **sobrevivem**.

---

## ✅ Checklist de validação

- [x] `docker compose up -d` sobe os 4 serviços sem erro
- [x] `init-logs` ajusta permissão antes do syslog-ng iniciar
- [x] Driver `syslog` confirmado nos containers da API e Postgres
- [x] Pacotes RFC 5424 chegando na porta 5514 (confirmado via tcpdump)
- [x] Hierarquia `<container>/<ano>/<mês>/<dia>.log` criada automaticamente
- [x] Bucket `_errors/<data>.log` recebe erros (4xx do Spring)
- [x] Logs sobrevivem a `docker compose stop`
- [x] Setup reprodutível: `down -v && up -d` funciona do zero

---

## 🚧 Troubleshooting (lições aprendidas)

### Pacotes chegam mas `/var/log/docker` vazio

Causa mais provável: **permissão**. Veja o log interno:
```bash
docker compose exec syslog-ng sh -c "tail -20 /config/log/current"
```
Procurar por `Permission denied (13)`. Solução: o init container faz isso automaticamente. Se removeu por engano, rode manual:
```bash
docker compose exec syslog-ng chown -R 1000:1000 /var/log/docker
```

### Erro `unexpected KW_TEMPLATE` no log interno

Causa: sintaxe antiga `template(t_logs)` dentro de `file()`. Solução: colocar o template direto na string do `file("caminho")`.

### `Error opening file ... '/var/log/docker/docker-desktop/...'`

Causa: usando `${HOST}` no template. `${HOST}` é o nome da máquina (`docker-desktop`), não do container. Use `${PROGRAM}`.

### `dial tcp [::1]:514: connect: connection timed out` no container que loga

Causa: `localhost` resolveu pra IPv6. Use `127.0.0.1` explícito.

### Container morre no boot com "logging driver initialization failed"

Causa: driver `tcp://` e syslog-ng ainda não pronto. Mude pra `udp://`.

### Diagnosticar tráfego que chega na porta

```bash
docker compose exec syslog-ng sh -c "apk add --no-cache tcpdump"
docker compose exec syslog-ng sh -c "tcpdump -i any -n 'udp port 5514' -A -c 5"
```

Em outro terminal, gere tráfego. Se pacotes aparecem no tcpdump mas nada é escrito, é problema da config do syslog-ng. Se nem pacotes aparecem, é problema do driver/rede.

---

## 📌 Próximos passos

- [ ] Capturar screenshots da demo (find, tail, _errors/)
- [ ] Atualizar slides (template em `apresentacao-prompts-e-roteiro.md`)
- [ ] (opcional) Adicionar `logstash-logback-encoder` na productivity-api para logs JSON estruturados em prod
- [ ] (opcional) Rotação automática com `logrotate` configurado no syslog-ng
- [ ] (opcional) Encaminhar logs pra ELK / Loki (rumo ao Desafio 05)

---

## 📚 Referências

- [Syslog-ng — File destination](https://www.syslog-ng.com/technical-documents/doc/syslog-ng-open-source-edition/3.38/administration-guide/35#TOPIC-1829138)
- [Docker — Configure logging drivers](https://docs.docker.com/config/containers/logging/configure/)
- [Docker — Syslog logging driver](https://docs.docker.com/config/containers/logging/syslog/)
- [RFC 5424 — Syslog Protocol](https://datatracker.ietf.org/doc/html/rfc5424)
- [LinuxServer.io — syslog-ng image](https://docs.linuxserver.io/images/docker-syslog-ng)

> 💡 **Lição transversal:** logs são a primeira fonte de verdade quando algo dá errado. Centralizar e organizar logs **antes** do incidente é o que diferencia "vamos descobrir o que aconteceu" de "perdemos os dados". E a maior parte do trabalho não é arquitetura — é diagnosticar pequenos detalhes (porta interna, IPv6 vs IPv4, formato RFC, ownership de volume) que só aparecem quando a coisa toda tenta funcionar junto.