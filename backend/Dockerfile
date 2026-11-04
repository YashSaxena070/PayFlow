FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/payflow-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

