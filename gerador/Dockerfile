FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests package dependency:copy-dependencies \
    -DincludeScope=runtime -DoutputDirectory=target/dependency

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/classes ./classes
COPY --from=build /app/target/dependency ./dependency

ENTRYPOINT ["java", "-cp", "classes:dependency/*", "br.edu.gerador.Gerador"]
