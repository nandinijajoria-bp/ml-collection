FROM 288163305855.dkr.ecr.ap-south-1.amazonaws.com/bharatpe/custom-java8:latest-amd AS java-build
LABEL name="ravi ranjan"

WORKDIR /app
COPY pom.xml .
COPY settings.xml .
RUN mvn -T 1C -s settings.xml dependency:resolve -Dmaven.test.skip=true
COPY . .
RUN mvn clean -s settings.xml -T 1C package -Dmaven.test.skip=true #TODO: remove skiptest

FROM 288163305855.dkr.ecr.ap-south-1.amazonaws.com/bharatpe/custom-java8:latest-amd
RUN wget https://bharatpe-cdn.s3.ap-south-1.amazonaws.com/opentelemetry-javaagent.jar
COPY --from=java-build /app/target/*.jar /app/app.jar
RUN mkdir -p /data/logs && \
    chown -R bharatpe:bharatpe /data && \
    chmod -R 777 /data && \
    chown -R bharatpe:bharatpe /app
USER bharatpe
ENTRYPOINT ["java","-javaagent:opentelemetry-javaagent.jar","-Xms2048M","-Xmx4608M","-XX:+ParallelRefProcEnabled","-XX:+UseG1GC","-XX:ConcGCThreads=2","-XX:G1HeapRegionSize=8M","-XX:G1ReservePercent=15","-XX:InitiatingHeapOccupancyPercent=50","-XX:MaxGCPauseMillis=200","-XX:ParallelGCThreads=2","-Duser.timezone=IST","-Dspring.profiles.active=${PROFILE}","-jar","/app/app.jar"]
# ENTRYPOINT ["/bin/bash", "-x", "/newrelic.sh"]