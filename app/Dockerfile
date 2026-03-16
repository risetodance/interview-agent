FROM gradle:8.14-jdk21 AS builder
WORKDIR /workspace

# Copy the entire project to the workspace
COPY . .

# Build the app module
# We use the root settings.gradle which includes 'app'
RUN gradle :app:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
# The jar file will be in app/build/libs/ inside the workspace
COPY --from=builder /workspace/app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
