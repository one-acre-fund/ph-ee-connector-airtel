FROM eclipse-temurin:17 AS build

WORKDIR /ph-ee-connector-airtel

COPY . .

RUN ./gradlew bootJar

FROM eclipse-temurin:17

WORKDIR /app

COPY --from=build /ph-ee-connector-airtel/build/libs/ph-ee-connector-airtel.jar .

EXPOSE 5000
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/ph-ee-connector-airtel.jar"]