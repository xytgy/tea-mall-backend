FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/target/*.jar app.jar
COPY skywalking-agent/skywalking-agent /app/skywalking-agent
EXPOSE 8082
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
ENV SW_AGENT_COLLECTOR_BACKEND_SERVICE="skywalking-oap:11800"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -javaagent:/app/skywalking-agent/skywalking-agent.jar -jar app.jar"]
