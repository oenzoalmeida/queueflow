# Interface React: servida pelo proprio backend (mesma origem da API, cookies first-party).
FROM node:22 AS frontend
WORKDIR /fe
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY backend/.mvn .mvn
COPY backend/mvnw ./mvnw
COPY backend/pom.xml ./pom.xml
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /fe/dist ./src/main/resources/static
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/queueflow-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
