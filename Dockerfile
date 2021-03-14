# Use https://hub.docker.com/_/oracle-serverjre-8
ARG ARCH=
FROM ${ARCH}openjdk:11.0.6-jdk-slim

# Make a directory
RUN mkdir -p /app
WORKDIR /app

# Copy only the target jar over
COPY target/ecobee-exporter.jar .

# Open the port
EXPOSE 3000

ENV PORT=3000

# Run the JAR
CMD java -cp ecobee-exporter.jar clojure.main -m collector.core
