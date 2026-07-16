FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM mcr.microsoft.com/openjdk/jdk:21-mariner

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# The application writes its H2 database under /app/data. Keep the application
# binary root-owned and grant the runtime user access only to the data directory.
RUN mkdir -p /app/data \
    && chown 10001:10001 /app/data \
    && chmod 750 /app/data

USER 10001:10001

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:-} -jar app.jar"]
