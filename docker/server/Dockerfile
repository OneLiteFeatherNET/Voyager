FROM eclipse-temurin:25.0.3_9-jdk AS build
WORKDIR /build
COPY . .
RUN ./gradlew :server:shadowJar --no-daemon

FROM eclipse-temurin:25.0.3_9-jre-alpine
WORKDIR /app
COPY --from=build /build/server/build/libs/*-all.jar app.jar
EXPOSE 25565
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-Xms256M", "-Xmx512M", "-jar", "app.jar"]
