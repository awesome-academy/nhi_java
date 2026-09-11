# ---- Build stage: biên dịch & đóng gói jar (bỏ test vì đã chạy ở CI/local) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy pom trước để layer tải dependency được cache lại khi chỉ đổi source.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src/ src/
RUN mvn -B -q -DskipTests package

# ---- Runtime stage: chỉ JRE + jar, không mang theo Maven và source ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Chạy bằng user thường, không phải root.
RUN addgroup -S tripgo && adduser -S tripgo -G tripgo

COPY --from=build /build/target/tripgo-*.jar app.jar
USER tripgo

EXPOSE 8080

# JWT_SECRET không có default trong application.yaml -> bắt buộc truyền lúc chạy container.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
