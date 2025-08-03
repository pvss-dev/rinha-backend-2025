FROM maven:3.9.7-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM bellsoft/liberica-openjdk-alpine:21
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

ENV PAYMENT_PROCESSOR_DEFAULT_URL=http://payment-processor-default:8080
ENV PAYMENT_PROCESSOR_FALLBACK_URL=http://payment-processor-fallback:8080
ENV SPRING_DATA_REDIS_URL=redis://redis:6379

ENV JAVA_OPTS="-server \
    -Xms64m \
    -Xmx130m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=100 \
    -XX:+UseStringDeduplication \
    -XX:+UseCompressedOops \
    -XX:+UseCompressedClassPointers \
    -XX:+TieredCompilation \
    -XX:TieredStopAtLevel=1 \
    -XX:+DisableExplicitGC \
    -XX:MaxMetaspaceSize=64m \
    -XX:CompressedClassSpaceSize=32m \
    -XX:ReservedCodeCacheSize=32m \
    -Djava.awt.headless=true \
    -Djava.net.preferIPv4Stack=true \
    -Dfile.encoding=UTF-8 \
    -Duser.timezone=UTC"

CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]