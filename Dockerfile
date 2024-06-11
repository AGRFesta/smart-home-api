FROM bellsoft/liberica-runtime-container:jdk-17-slim-musl
ARG DEPENDENCY=build
RUN echo ${DEPENDENCY}
COPY ${DEPENDENCY}/libs/*.jar /app/lib/app.jar
ENTRYPOINT ["java","-jar","/app/lib/app.jar"]
