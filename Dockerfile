FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY Attend.java .
RUN javac Attend.java

# Render injects $PORT at runtime; the app reads it automatically.
EXPOSE 10000

CMD ["java", "-Dfile.encoding=UTF-8", "Attend"]
