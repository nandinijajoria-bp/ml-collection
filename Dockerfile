FROM maven:3-openjdk-8 AS java-build
LABEL name="ravi ranjan"

WORKDIR /app 
COPY pom.xml .
RUN mvn clean dependency:go-offline
COPY . .
RUN mvn clean package -Dmaven.test.skip=true #TODO: remove skiptest


FROM openjdk:8
COPY --from=java-build /app/target/*.jar /app/app.jar
RUN wget https://bharatpe-cdn.s3.ap-south-1.amazonaws.com/infra/apm-agent.jar
ENTRYPOINT ["java","-javaagent:apm-agent.jar","-Delastic.apm.service_name=Lending","-Delastic.apm.server_urls=http://apm.bharatpe.in","-Delastic.apm.secret_token=gNSvBzGYqoxh","-jar","-Duser.timezone=IST","-Dspring.profiles.active=${PROFILE}", "/app/app.jar"]
#ENTRYPOINT ["java","-jar","-Duser.timezone=IST","-Dspring.profiles.active=${PROFILE}", "/app/app.jar"]
