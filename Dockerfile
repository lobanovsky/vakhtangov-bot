FROM gradle:8.13-jdk21 AS builder

WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon


FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/build/libs/vakhtangov-bot-all.jar app.jar

EXPOSE 8081

CMD ["java", "-jar", "app.jar"]
