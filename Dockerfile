FROM 288163305855.dkr.ecr.ap-south-1.amazonaws.com/bharatpe/custom-java8:latest-amd AS java-build
LABEL name="ravi ranjan"

WORKDIR /app
COPY pom.xml .
COPY settings.xml .
RUN mvn -T 1C -s settings.xml dependency:resolve -Dmaven.test.skip=true
COPY . .
RUN mvn clean -s settings.xml -T 1C package -Dmaven.test.skip=true #TODO: remove skiptest

FROM 288163305855.dkr.ecr.ap-south-1.amazonaws.com/bharatpe/custom-java8:latest-amd
COPY --from=java-build /app/target/*.jar /app/app.jar
COPY --from=java-build /app/newrelic.yml /
COPY --from=java-build /app/newrelic.yml /app/newrelic.yml
COPY --from=java-build /app/newrelic.sh  /
RUN wget https://bharatpe-cdn.s3.ap-south-1.amazonaws.com/infra/newrelic.jar
RUN mkdir -p /data/logs && \
    chown -R bharatpe:bharatpe /data && \
    chmod -R 777 /data && \
    chown -R bharatpe:bharatpe /app
USER bharatpe
ENTRYPOINT ["java","-javaagent:newrelic.jar","-Xms2560M", "-Xmx5000M","-Duser.timezone=IST","-Dspring.profiles.active=${PROFILE}","-jar","/app/app.jar"]
# ENTRYPOINT ["/bin/bash", "-x", "/newrelic.sh"]