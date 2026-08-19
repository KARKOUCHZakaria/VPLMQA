FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S vplmqa && adduser -S vplmqa -G vplmqa
WORKDIR /app
COPY target/*.jar app.jar
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
USER vplmqa
EXPOSE 8087
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

