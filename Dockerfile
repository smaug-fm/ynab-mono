FROM gradle:6.9.2-jdk11-alpine as BUILD

WORKDIR /opt/app
COPY *.kts ./
COPY *.properties ./
RUN gradle dependencies
COPY src ./src
RUN gradle shadowJar

FROM openjdk:11-alpine

COPY --from=BUILD /opt/app/build/libs/ynab-mono-*-fat.jar /bin/runner/ynab-mono.jar
WORKDIR /bin/runner

CMD ["java", "-jar", "ynab-mono.jar"]
