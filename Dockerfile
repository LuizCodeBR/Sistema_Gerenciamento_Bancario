FROM eclipse-temurin:21-jdk-jammy AS build
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp -DskipTests dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system banco && useradd --system --gid banco banco
WORKDIR /app
COPY --from=build /workspace/target/banco-0.0.1-SNAPSHOT.jar app.jar
USER banco
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
