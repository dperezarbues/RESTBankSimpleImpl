FROM openjdk:8-jdk-alpine
VOLUME /tmp
RUN gradle build
ADD build/libs/challenge-all-0.0.1-SNAPSHOT.jar app.jar
ENV JAVA_OPTS=""
ENTRYPOINT exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app.jar

