FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system blackout && useradd --system --gid blackout blackout
COPY --from=build /build/target/blackout-users-0.0.1-SNAPSHOT.jar app.jar
USER blackout
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
