FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY common ./common
COPY backtest-lib ./backtest-lib
COPY policy-engine ./policy-engine
COPY backtest-worker ./backtest-worker
RUN ./gradlew :backtest-worker:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backtest-worker/build/libs/*-SNAPSHOT.jar /app/app.jar
RUN mkdir -p /tmp && chmod 1777 /tmp
USER 65532
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
