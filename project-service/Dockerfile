FROM mcr.microsoft.com/playwright/java:v1.45.0-jammy AS runtime
RUN groupadd -r vplmqa && useradd -r -g vplmqa vplmqa
WORKDIR /app
COPY target/*.jar app.jar
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
USER vplmqa
EXPOSE 8088
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

