FROM eclipse-temurin:25.0.2_10-jdk
WORKDIR /app
COPY target/billing-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]