# Stage 1: Build the application using a full JDK 25 image
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
# Compile and package (e.g., using Maven)
RUN ./mvnw clean package -DskipTests

# Stage 2: Create the runtime image using a lean JRE 25 image
FROM eclipse-temurin:25-jre
WORKDIR /app
# Copy the executable JAR from the build stage
COPY --from=build /app/target/*.jar app.jar
# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
