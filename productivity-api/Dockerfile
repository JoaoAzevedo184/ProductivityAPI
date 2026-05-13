# ============================================================
# Stage 1: builder
# Compila a aplicação com Maven. Usa JDK completo.
# ============================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copia o wrapper e o pom.xml primeiro — em build incremental,
# se só o código mudou e as dependências continuam iguais,
# o Docker reusa o cache desta camada e pula o download.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Agora sim, o código-fonte
COPY src src

# Empacota o JAR. Testes pulados — eles já rodaram no CI antes do build da imagem.
RUN ./mvnw -B clean package -DskipTests

# ============================================================
# Stage 2: runtime
# Imagem final: só JRE + JAR + usuário não-root.
# ============================================================
FROM eclipse-temurin:21-jre-alpine

# wget é necessário para o HEALTHCHECK (não vem por padrão no Alpine)
RUN apk add --no-cache wget

# Usuário não-root: mitiga escalada de privilégios em caso de RCE
RUN addgroup -S app && adduser -S appuser -G app

WORKDIR /app

# Copia só o JAR. O contexto de build (código, .git, etc.) fica no builder.
COPY --from=builder --chown=appuser:app /build/target/*.jar app.jar

USER appuser

# Variáveis sobrescrevíveis em runtime via `docker run -e ...`
ENV SPRING_PROFILES_ACTIVE=dev \
    JAVA_OPTS=""

EXPOSE 8080

# Healthcheck: Docker considera o container "healthy" quando o Actuator responde
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --quiet --spider http://localhost:8080/actuator/health || exit 1

# `sh -c` permite expansão de $JAVA_OPTS em runtime
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]