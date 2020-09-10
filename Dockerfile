FROM maven:3-openjdk-8 AS java-build
LABEL name="ravi ranjan"

WORKDIR /app 
COPY pom.xml .
RUN mvn clean dependency:go-offline
COPY . .
RUN mvn clean package -Dmaven.test.skip=true #TODO: remove skiptest


FROM gcr.io/distroless/java:8
COPY --from=java-build /app/target/*.jar /app/app.jar
ENTRYPOINT ["java","-jar","-Duser.timezone=IST","-Dspring.profiles.active=${PROFILE}", "/app/app.jar"]
