FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY consumidor-suporte/pom.xml .
COPY consumidor-suporte/src ./src
RUN mvn -q -DskipTests package dependency:copy-dependencies \
    -DincludeScope=runtime -DoutputDirectory=target/dependency

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/classes ./classes
COPY --from=build /app/target/dependency ./dependency
COPY modelos/modelo-suporte.bin ./modelos/modelo-suporte.bin
ENTRYPOINT ["java", "-cp", "classes:dependency/*", "ConsumidorSuporte"]
