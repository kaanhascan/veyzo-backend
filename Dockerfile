FROM eclipse-temurin:21-jdk-jammy


RUN apt-get update && \
    apt-get install -y ffmpeg && \
    apt-get clean

WORKDIR /app

RUN mkdir -p /app/uploads/processed

COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]