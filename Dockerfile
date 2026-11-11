# syntax=docker/dockerfile:1

# ---- Build: đóng gói module api (kèm common/domain/application) thành fat jar
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY common/pom.xml common/
COPY domain/pom.xml domain/
COPY application/pom.xml application/
COPY api/pom.xml api/
COPY common/src common/src
COPY domain/src domain/src
COPY application/src application/src
COPY api/src api/src

RUN sh ./mvnw -B -ntp -Duser.timezone=Asia/Ho_Chi_Minh -DskipTests -pl api -am package \
    && cp api/target/api-*.jar /workspace/app.jar

# ---- Runtime: chỉ JRE + jar, chạy bằng user không phải root
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER app

ENV TZ=Asia/Ho_Chi_Minh \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# Render cấp cổng qua biến PORT; chạy local không có PORT thì dùng 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Duser.timezone=Asia/Ho_Chi_Minh -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
