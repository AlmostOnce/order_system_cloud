FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY gateway-service/pom.xml ./gateway-service/pom.xml

RUN mvn -B -ntp -f gateway-service/pom.xml dependency:go-offline

COPY gateway-service/src ./gateway-service/src

RUN mvn -B -ntp -f gateway-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/gateway-service/target/gateway-service-*.jar /app/app.jar

ENV JAVA_OPTS=""

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
