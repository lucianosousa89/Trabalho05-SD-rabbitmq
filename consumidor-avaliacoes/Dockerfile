FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY consumidor-avaliacoes/pom.xml .
COPY consumidor-avaliacoes/src ./src
RUN mvn -q -DskipTests package dependency:copy-dependencies \
    -DincludeScope=runtime -DoutputDirectory=target/dependency

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/classes ./classes
COPY --from=build /app/target/dependency ./dependency
COPY modelos/modelo-avaliacoes.bin ./modelos/modelo-avaliacoes.bin
ENTRYPOINT ["java", "-cp", "classes:dependency/*", "ConsumidorAvaliacoes"]
