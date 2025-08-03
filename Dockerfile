FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

ENV DEFAULT_PROCESSOR_URL=http://payment-processor-default:8080
ENV REDIS_URI=redis://rinha-redis:6379

ENV JAVA_OPTS="-server \
    -Xms64m \
    -Xmx128m \
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