# 🛠️ Setup do Ambiente

Guia passo a passo para configurar o ambiente de desenvolvimento da Productivity API.

---

## Pré-requisitos

| Ferramenta | Versão mínima | Verificar |
|---|---|---|
| Java JDK | 21 | `java -version` |
| Git | 2.30+ | `git --version` |
| Maven | 3.9+ (opcional — usar wrapper) | `./mvnw -v` |
| Docker | 24+ (para desafios 2 em diante) | `docker --version` |
| kubectl | 1.28+ (para desafios 3 em diante) | `kubectl version --client` |

> **Não precisa instalar Maven** — o projeto inclui o wrapper (`./mvnw` e `mvnw.cmd`), que baixa a versão correta automaticamente na primeira execução.

---

## Instalando o JDK 21

### Linux (Ubuntu/Pop!_OS/Debian)

```bash
# Opção 1: SDKMAN (recomendado para gerenciar múltiplas versões)
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21-tem    # Eclipse Temurin

# Opção 2: apt
sudo apt update
sudo apt install openjdk-21-jdk
```

### macOS

```bash
# Com Homebrew
brew install --cask temurin@21

# Ou via SDKMAN
sdk install java 21-tem
```

### Windows

Baixar o instalador do [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21) e seguir o wizard. Lembrar de marcar a opção que adiciona `JAVA_HOME` ao PATH.

### Verificar

```bash
java -version
# openjdk version "21.x.x" ...

javac -version
# javac 21.x.x

echo $JAVA_HOME    # Linux/macOS
# /home/usuario/.sdkman/candidates/java/current
```

---

## Clonando o Projeto

```bash
git clone https://github.com/JoaoAzevedo184/productivity-api.git
cd productivity-api
```

---

## Primeira Execução

```bash
# Linux/macOS
./mvnw spring-boot:run

# Windows (PowerShell)
.\mvnw.cmd spring-boot:run
```

A primeira execução demora mais (~3-5 min) porque baixa todas as dependências do Maven Central. Execuções seguintes sobem em 5-10 segundos.

### Validar que está rodando

```bash
# Em outro terminal
curl http://localhost:8080/tasks

# Esperado: {"content":[],"totalElements":0, ...}
```

Acessar no navegador:

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **H2 Console:** http://localhost:8080/h2-console
    - JDBC URL: `jdbc:h2:mem:taskdb-dev`
    - User: `sa`
    - Password: `sa`

---

## IDE — Configuração Recomendada

### IntelliJ IDEA

1. **Open** → selecionar a pasta `productivity-api`.
2. IntelliJ detecta o `pom.xml` e importa o projeto Maven.
3. Configurar JDK 21: **File → Project Structure → Project SDK** → escolher Java 21.
4. **Habilitar annotation processing** (necessário para Lombok):
    - Settings → Build, Execution, Deployment → Compiler → Annotation Processors → ✅ Enable annotation processing
5. Instalar plugin **Lombok** (se ainda não vier por default).
6. Run configuration: clicar com botão direito em `ProductivityApiApplication` → Run.

### VS Code

1. Instalar extensões:
    - **Extension Pack for Java** (Microsoft)
    - **Spring Boot Extension Pack** (Pivotal/VMware)
    - **Lombok Annotations Support for VS Code** (Gabriel Basilio Brito)
2. Abrir a pasta `productivity-api`.
3. VS Code detecta o `pom.xml` e configura o Java Language Server.
4. **F5** ou **Run → Start Debugging** para rodar.

### Eclipse / Spring Tool Suite (STS)

1. **File → Import → Existing Maven Projects** → selecionar a pasta.
2. Instalar **Lombok**: baixar o jar em [projectlombok.org](https://projectlombok.org/download), executar `java -jar lombok.jar`, apontar para a instalação do Eclipse/STS.
3. Reiniciar o Eclipse.
4. Run As → Spring Boot App.

---

## Estrutura do Workspace

Após o setup inicial, sua árvore deve estar assim:

```
productivity-api/
├── .mvn/wrapper/              # Wrapper Maven (não editar)
├── docs/                       # Documentação (você está aqui)
├── src/main/java/...          # Código-fonte
├── src/main/resources/         # application.yml, etc.
├── src/test/java/...          # Testes
├── target/                     # Build (gerado, ignorado pelo git)
├── .gitignore
├── mvnw                        # Wrapper Unix
├── mvnw.cmd                    # Wrapper Windows
├── pom.xml                     # Definição Maven
└── README.md
```

---

## Variáveis de Ambiente (Perfil `prod`)

Quando rodar com `SPRING_PROFILES_ACTIVE=prod`, três variáveis são obrigatórias:

```bash
export DB_URL="jdbc:postgresql://localhost:5432/productivity"
export DB_USER="postgres"
export DB_PASSWORD="seu_password_aqui"
export SPRING_PROFILES_ACTIVE=prod

./mvnw spring-boot:run
```

> 🔒 **Nunca commite credenciais.** Em produção, use Vault, AWS Secrets Manager, Kubernetes Secrets, ou GitHub Secrets (no pipeline).

### Subindo um PostgreSQL local para testar

```bash
docker run -d \
  --name productivity-postgres \
  -e POSTGRES_DB=productivity \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16-alpine
```

---

## Comandos Maven Úteis

```bash
# Compilar (sem rodar testes)
./mvnw compile

# Rodar testes
./mvnw test

# Empacotar em JAR
./mvnw package

# JAR ignorando testes (não fazer isso em CI/CD)
./mvnw package -DskipTests

# Rodar o JAR gerado
java -jar target/productivity-api-0.0.1-SNAPSHOT.jar

# Limpar build
./mvnw clean

# Atualizar dependências (verificar versões mais novas)
./mvnw versions:display-dependency-updates
```

---

## Postman / Insomnia

O repositório inclui `productivity-api.postman_collection.json` com 27 requests prontos.

### Importar no Postman

1. **File → Import** → arrastar o arquivo `.json`.
2. As variáveis (`baseUrl`, `taskId`) já vêm dentro da collection.
3. Rodar `Create Task` primeiro — ele salva o `id` em `{{taskId}}` automaticamente.

### Importar no Insomnia

1. **Application → Preferences → Data → Import Data → From File**.
2. Mesma collection funciona.

---

## Troubleshooting

### Porta 8080 ocupada

```bash
# Linux/macOS: descobrir o processo
sudo lsof -i :8080

# Matar
kill -9 <PID>

# Ou rodar em outra porta
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
```

### "JAVA_HOME is not defined"

```bash
# Linux/macOS — adicionar ao ~/.bashrc, ~/.zshrc ou ~/.config/fish/config.fish
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
export PATH=$JAVA_HOME/bin:$PATH

# Validar
echo $JAVA_HOME
```

### Lombok não funciona (getters/setters não existem)

- **IntelliJ:** Settings → Plugins → instalar "Lombok" → reiniciar.
- **Eclipse:** rodar o `lombok.jar` (`java -jar lombok.jar`) e apontar para a IDE.
- **VS Code:** instalar a extensão "Lombok Annotations Support".

### Maven baixando dependências muito lento

Adicionar mirror no `~/.m2/settings.xml`:

```xml
<settings>
  <mirrors>
    <mirror>
      <id>maven-central-mirror</id>
      <url>https://repo.maven.apache.org/maven2/</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

### Erro de SSL em rede corporativa

Adicionar certificado da empresa ao truststore do JDK:

```bash
sudo keytool -importcert \
  -alias empresa-ca \
  -keystore $JAVA_HOME/lib/security/cacerts \
  -file /caminho/para/cert.crt \
  -storepass changeit
```

---

## Próximos Passos

Ambiente pronto. Para continuar:

- Ler [`architecture.md`](architecture.md) para entender o design.
- Ler [`api.md`](api.md) para a referência dos endpoints.
- Para implementar os desafios DevOps: [`challengs/`](challengs/).