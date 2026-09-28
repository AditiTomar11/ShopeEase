# Stage 1: Build the app using Maven
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Resolve dependencies in their own layer so Render's build cache can reuse them
# and only re-downloads when pom.xml changes.
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# Stage 2: Run the jar with a lightweight JRE image (alpine ~100 MB smaller than
# the default image, so cold-start image pulls are faster)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# JVM flags tuned for a 0.1 CPU / 512 MB free-tier instance:
#  -XX:TieredStopAtLevel=1  C1-only JIT: much faster startup, lower peak throughput (irrelevant at 0.1 CPU)
#  -XX:+UseSerialGC         Cheapest collector for a single-CPU, small-heap container
#  -XX:MaxRAMPercentage=50  Heap up to 256 MB, leaving headroom for metaspace/threads so we're never OOM-killed
#  -Djava.security.egd      Never block on entropy while generating session ids
# Override at runtime with the JAVA_OPTS env var if needed.
ENV JAVA_OPTS="-XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:MaxRAMPercentage=50 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
