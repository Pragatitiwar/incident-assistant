# ---- build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q package -DskipTests

# ---- runtime stage ----
FROM eclipse-temurin:25-jre
RUN useradd --system --create-home appuser
WORKDIR /app

# Embedding model files, downloaded once at build time
ADD https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx /models/model.onnx
ADD https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json /models/tokenizer.json
RUN chmod -R a+rX /models

COPY --from=build /app/target/*.jar app.jar
USER appuser
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 --enable-native-access=ALL-UNNAMED \
 -Dspring.ai.embedding.transformer.onnx.model-uri=file:/models/model.onnx \
 -Dspring.ai.embedding.transformer.tokenizer.uri=file:/models/tokenizer.json"

ENTRYPOINT ["java", "-jar", "app.jar"]