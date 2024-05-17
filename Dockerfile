FROM openjdk:21-jdk

ARG JAR_FILE=target/simple-torrent-tracker-1.0.0-SNAPSHOT-jar-with-dependencies.jar

ADD ${JAR_FILE} app.jar

ENV JAVA_OPTS="--enable-preview"

# Run the jar file 
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app.jar $APP_ARGS"]
