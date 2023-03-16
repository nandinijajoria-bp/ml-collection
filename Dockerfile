FROM maven:3-openjdk-8 AS java-build
LABEL name="ravi ranjan"

WORKDIR /app
COPY pom.xml .
COPY settings.xml .
RUN mvn -T 1C -s settings.xml dependency:resolve -Dmaven.test.skip=true
COPY . .
RUN mvn clean -s settings.xml -T 1C package -Dmaven.test.skip=true #TODO: remove skiptest

FROM openjdk:8
COPY --from=java-build /app/target/*.jar /app/app.jar
COPY --from=java-build /app/newrelic.yml /
COPY --from=java-build /app/newrelic.yml /app/newrelic.yml
COPY --from=java-build /app/newrelic.sh  /
RUN wget https://bharatpe-cdn.s3.ap-south-1.amazonaws.com/infra/newrelic.jar
ENTRYPOINT ["java","-javaagent:newrelic.jar","-Duser.timezone=IST","-Dspring.profiles.active=${PROFILE}","-jar","/app/app.jar"]
# ENTRYPOINT ["/bin/bash", "-x", "/newrelic.sh"]
