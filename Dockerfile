FROM eclipse-temurin:17 AS build

WORKDIR /ph-ee-connector-airtel

COPY . .

RUN ./gradlew bootJar

FROM eclipse-temurin:17

WORKDIR /app

COPY --from=build /ph-ee-connector-airtel/build/libs/ph-ee-connector-airtel.jar .
COPY --from=build /ph-ee-connector-airtel/config/elastic/elastic-apm-agent-1.54.0.jar /app/config/elastic/elastic-apm-agent.jar

EXPOSE 5000

ENTRYPOINT ["java", "-jar", "/app/ph-ee-connector-airtel.jar"]
