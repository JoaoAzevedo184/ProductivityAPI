# 📜 Desafio 04 — Logging Centralizado com Syslog-ng

> **Coletar logs de todos os contêineres** da productivity-api num único ponto, organizados por host e data, prontos para auditoria e análise.

| Campo | Valor |
|---|---|
| **Status** | 🔲 Planejado |
| **Aplicação-base** | productivity-api (stack do [Desafio 02](challenge-02.md): API + Postgres) |
| **Ferramentas** | Syslog-ng + Docker Compose |
| **Modo** | Solo |

---

## 🎯 Objetivos

1. Implementar coletor centralizado de logs com Syslog-ng.
2. Configurar o Docker para enviar logs de todos os containers via driver `syslog`.
3. Organizar logs em hierarquia `host/ano/mês/dia.log`.
4. Demonstrar consulta a logs de containers já encerrados.
5. Documentar a arquitetura e ganhos para auditoria/observabilidade.

---

## 🏗️ Arquitetura

```
┌─────────────────────┐    ┌────────────────────┐    ┌──────────────────────┐
│  productivity-api   │    │                    │    │                      │
│  productivity-postgres   │    │  Docker Daemon     │    │  Syslog-ng Server    │
│  syslog-ng (self)   │───▶│  (log-driver:      │───▶│  Container           │
│                     │    │   syslog)          │    │  porta 514 TCP/UDP   │
└─────────────────────┘    └────────────────────┘    └──────────┬───────────┘
                                                                │
                                                                ▼
                                                   ┌────────────────────────┐
                                                   │  /var/log/docker/      │
                                                   │  └── productivity-api/ │
                                                   │      └── 2026/         │
                                                   │          └── 05/       │
                                                   │              ├── 10.log│
                                                   │              └── 11.log│
                                                   └────────────────────────┘
```

---

## 🛠️ Implementação

### Etapa 1 — Servidor Syslog-ng

**`syslog-ng/docker-compose.yml`**

```yaml
services:
  syslog-ng:
    image: lscr.io/linuxserver/syslog-ng:latest
    container_name: syslog-ng
    ports:
      - "514:514/tcp"
      - "514:514/udp"
      - "601:601/tcp"   # syslog estruturado (RFC 5425)
    volumes:
      - ./config:/config
      - /var/log/docker:/var/log/docker
    environment:
      - PUID=1000
      - PGID=1000
      - TZ=America/Recife
    restart: unless-stopped
```

**Decisões:**

- `version` removido (deprecated no Compose v2).
- `PUID`/`PGID=1000` para que os logs sejam graváveis pelo meu usuário no host (não-root).
- `TZ=America/Recife` para que os timestamps fiquem no fuso correto.
- 601/TCP exposto para evolução futura (transporte syslog estruturado / TLS).

---

### Etapa 2 — Configuração do Syslog-ng

**`syslog-ng/config/syslog-ng.conf`**

```conf
@version: 4.5
@include "scl.conf"

# ============================================================
# Sources — onde os logs chegam
# ============================================================
source s_network {
    # TCP é mais confiável (sem perda em rajadas)
    network(
        ip("0.0.0.0")
        port(514)
        transport("tcp")
        flags(syslog-protocol)
    );

    # UDP mantido para clientes legados (não-Docker)
    network(
        ip("0.0.0.0")
        port(514)
        transport("udp")
        flags(syslog-protocol)
    );
};

# ============================================================
# Template — como organizar os arquivos
# ============================================================
template t_logs {
    template("/var/log/docker/${HOST}/${YEAR}/${MONTH}/${DAY}.log");
    template_escape(no);
};

# ============================================================
# Destinations — para onde escrever
# ============================================================
destination d_logs {
    file(
        template(t_logs)
        create_dirs(yes)
        dir-perm(0755)
        perm(0644)
    );
};

# Logs de erro num bucket separado (extra mile)
filter f_errors { level(err..emerg); };

destination d_errors {
    file(
        "/var/log/docker/_errors/${YEAR}-${MONTH}-${DAY}.log"
        create_dirs(yes)
    );
};

# ============================================================
# Pipelines
# ============================================================
log {
    source(s_network);
    destination(d_logs);
};

log {
    source(s_network);
    filter(f_errors);
    destination(d_errors);
};
```

**Pontos importantes:**

- `flags(syslog-protocol)` — interpreta corretamente RFC 5424 (formato que o Docker envia).
- `${HOST}` — vem do `tag` configurado no Docker (que mapeio para o nome do container).
- `create_dirs(yes)` — cria a hierarquia automaticamente, sem precisar de mkdir manual.
- Permissões explícitas (`0755`/`0644`) seguem o princípio do menor privilégio.

---

### Etapa 3 — Configuração do Docker

**`/etc/docker/daemon.json`**

```json
{
  "log-driver": "syslog",
  "log-opts": {
    "syslog-address": "tcp://localhost:514",
    "tag": "{{.Name}}",
    "syslog-format": "rfc5424",
    "syslog-tls": "false"
  }
}
```

**Parâmetros:**

| Parâmetro | Função |
|---|---|
| `log-driver` | Driver global de logs = syslog |
| `syslog-address` | Onde mandar (Syslog-ng local na 514) |
| `tag` | `{{.Name}}` = nome do container, vira `${HOST}` no Syslog-ng |
| `syslog-format` | RFC 5424 traz timestamp preciso, severity e structured data |
| `syslog-tls` | Desabilitado em local; obrigatório em prod |

**Aplicar:**

```bash
sudo systemctl restart docker
```

> ⚠️ **Atenção:** isso derruba **todos** os containers em execução. Em produção: aplicar em janela de manutenção ou usar log driver por container (`docker run --log-driver=syslog ...`) em vez de global.

---

### Etapa 4 — Aplicação envia logs estruturados

Para tirar o máximo do Syslog-ng, a productivity-api deve emitir logs em JSON. Adicionar ao `logback-spring.xml`:

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

```xml
<!-- logback-spring.xml -->
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <springProfile name="prod">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON"/>
        </root>
    </springProfile>

    <springProfile name="dev">
        <!-- mantém o console pretty pra desenvolvimento -->
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%clr(%d{HH:mm:ss}){faint} %clr(%-5level) %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
</configuration>
```

**Por quê:** em produção, logs em JSON permitem parsing por ferramentas (ELK, Loki) sem regex frágil. Em dev, formato humano é melhor.

---

## 🧪 Testes de Validação

### 1. Subir o Syslog-ng

```bash
cd syslog-ng
docker compose up -d

# Validar
docker compose logs -f syslog-ng
docker compose ps
# syslog-ng   Up   0.0.0.0:514->514/tcp, 0.0.0.0:514->514/udp
```

### 2. Confirmar portas abertas

```bash
sudo ss -tulpn | grep 514
# tcp   LISTEN   0  511  *:514   *:*
# udp   UNCONN   0  0    *:514   *:*
```

### 3. Subir a productivity-api com o novo driver

```bash
cd ..
docker compose up -d

# Verificar que o driver de log é syslog
docker inspect productivity-api | grep -A 5 "LogConfig"
```

### 4. Gerar tráfego

```bash
# Bater na API algumas vezes
for i in {1..10}; do
  curl -s http://localhost:8080/tasks > /dev/null
done

# Provocar um erro pra ver no _errors/
curl http://localhost:8080/tasks/99999  # 404
```

### 5. Validar arquivos criados

```bash
tree /var/log/docker/
# /var/log/docker/
# ├── productivity-api/
# │   └── 2026/
# │       └── 05/
# │           └── 10.log
# ├── productivity-postgres/
# │   └── 2026/
# │       └── 05/
# │           └── 10.log
# └── _errors/
#     └── 2026-05-10.log

# Ver os logs em tempo real
tail -f /var/log/docker/productivity-api/2026/05/10.log
```

### 6. Demonstrar resiliência

```bash
# Derrubar a API
docker compose stop api

# Os logs anteriores continuam disponíveis
cat /var/log/docker/productivity-api/2026/05/10.log
```

Esse é o ponto crítico: **logs sobrevivem ao container**. Sem essa centralização, `docker logs` deixa de funcionar quando o container é removido.

---

## ✅ Checklist de Validação

- [ ] Syslog-ng inicia sem erros (`docker compose logs syslog-ng`)
- [ ] Portas 514/TCP e 514/UDP escutando (`ss -tulpn | grep 514`)
- [ ] `/etc/docker/daemon.json` aplicado e Docker reiniciado
- [ ] Containers criam diretórios automaticamente
- [ ] Estrutura `host/ano/mês/dia.log` respeitada
- [ ] Erros vão para `_errors/`
- [ ] Permissões corretas nos arquivos (`644`) e diretórios (`755`)
- [ ] Logs sobrevivem a `docker compose down`

---

## 🎤 Roteiro de Apresentação

### Slide 1: Problema
> "Quantas vezes você precisou debugar um container que já não existe mais?"

- Sem centralização: `docker logs` morre com o container.
- Com 10+ containers: difícil correlacionar eventos.

### Slide 2: Solução
- Diagrama da arquitetura.

### Slide 3: Live demo
1. Mostrar Syslog-ng rodando.
2. Subir productivity-api + Postgres.
3. Gerar tráfego.
4. `tree /var/log/docker/` mostrando organização automática.
5. `tail -f` mostrando logs em tempo real.
6. `docker compose down` + mostrar que os logs continuam acessíveis.
7. Mostrar pasta `_errors/` com erros filtrados.

### Slide 4: Ganhos
- Auditabilidade (logs sobrevivem).
- Organização (por host/data).
- Escalabilidade (recebe de múltiplos hosts).
- Pronto pra evolução (ELK, Loki).

### Slide 5: Próximos passos
- Integrar com Prometheus + Grafana ([Desafio 05](challenge-05.md)).
- TLS na 601 para ambientes externos.
- Encaminhamento para Elasticsearch.

---

## 🚧 Troubleshooting

### Porta 514 ocupada pelo `rsyslog` do host

```bash
sudo systemctl status rsyslog
sudo systemctl stop rsyslog
sudo systemctl disable rsyslog
```

### Containers não enviam logs

Verificar nesta ordem:

```bash
# 1. Daemon aplicou o config?
sudo systemctl restart docker
docker info | grep -i logging
# Logging Driver: syslog

# 2. Syslog-ng acessível?
nc -vz localhost 514

# 3. Firewall não bloqueia?
sudo ufw status
```

### Logs aparecem ilegíveis

Sintoma: caracteres estranhos ou linhas concatenadas.
Causa: incompatibilidade RFC 3164 vs RFC 5424.
Solução: garantir `syslog-format: rfc5424` no Docker **e** `flags(syslog-protocol)` no Syslog-ng.

### Permissão negada nos diretórios

```bash
sudo chown -R 1000:1000 /var/log/docker
sudo chmod -R 755 /var/log/docker
```

---

## 🔐 Considerações de Produção

| Tópico | Recomendação |
|---|---|
| **TLS** | Habilitar na porta 6514 com certificados |
| **Volume dedicado** | `/var/log/docker` num disco separado (não na partição raiz) |
| **Rotação** | `logrotate` diário, comprimido, mantendo 30 dias |
| **Monitoramento do coletor** | Métricas do próprio Syslog-ng (uso de CPU/memória/disco) |
| **Alertas** | Disco > 80%, taxa de mensagens caindo abruptamente |
| **Retenção** | Política clara: 30 dias online, 1 ano em cold storage (S3 Glacier) |

### Exemplo de `logrotate`

**`/etc/logrotate.d/docker-syslog`**

```
/var/log/docker/*/*/*/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 0644 root root
}
```

---

## 📂 Estrutura Final

```
productivity-api/
├── docker-compose.yml             # API + Postgres (Desafio 02)
├── syslog-ng/
│   ├── docker-compose.yml         # serviço syslog-ng
│   └── config/
│       └── syslog-ng.conf         # config das pipelines
├── daemon.json.example            # template do /etc/docker/daemon.json
└── (resto do projeto)
```

---

## 📌 Status e Próximos Passos

**Concluído:**

- [ ] Nada ainda.

**A fazer:**

1. Criar `syslog-ng/docker-compose.yml` + config.
2. Subir o Syslog-ng standalone, validar portas.
3. Aplicar `daemon.json` e reiniciar Docker.
4. Subir productivity-api e validar logs centralizados.
5. Configurar logstash-logback-encoder na app para JSON em prod.
6. Capturar logs/screenshots para a apresentação.

---

## 📚 Referências

- [Syslog-ng Documentation](https://www.syslog-ng.com/technical-documents/)
- [Docker logging drivers](https://docs.docker.com/config/containers/logging/configure/)
- [RFC 5424 — Syslog Protocol](https://datatracker.ietf.org/doc/html/rfc5424)
- [LinuxServer.io — syslog-ng image](https://docs.linuxserver.io/images/docker-syslog-ng)
- [Logstash Logback Encoder](https://github.com/logfellow/logstash-logback-encoder)

> 💡 **Lição transversal:** logs são a primeira fonte de verdade quando algo dá errado. Centralizar e organizar logs **antes** do incidente é o que diferencia "vamos descobrir o que aconteceu" de "perdemos os dados".