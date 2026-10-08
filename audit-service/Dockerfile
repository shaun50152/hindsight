FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY common ./common
COPY policy-engine ./policy-engine
COPY audit-service ./audit-service
RUN ./gradlew :audit-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/audit-service/build/libs/*-SNAPSHOT.jar /app/app.jar
RUN mkdir -p /tmp && chmod 1777 /tmp
USER 65532
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
