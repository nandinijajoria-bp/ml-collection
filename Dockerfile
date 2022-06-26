FROM maven:3-openjdk-8 AS java-build
LABEL name="ravi ranjan"

WORKDIR /app
COPY pom.xml .
COPY settings.xml .
RUN mvn clean dependency:go-offline
COPY . .
RUN mvn clean -s settings.xml install -Dmaven.test.skip=true


FROM openjdk:8
COPY --from=java-build /app/target/*.jar /app/app.jar
COPY --from=java-build /app/newrelic.yml /
COPY --from=java-build /app/newrelic.yml /app/newrelic.yml
RUN mkdir -p /data/bbps-recon/
RUN wget https://bharatpe-cdn.s3.ap-south-1.amazonaws.com/infra/newrelic.jar
ENTRYPOINT ["java","-javaagent:newrelic.jar","-Duser.timezone=IST","-Dspring.profiles.active=${PROFILE}","-jar","/app/app.jar"]
