FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM pedrombmachado/ntu_lubuntu:comp20081

LABEL maintainer="NTU Student"
WORKDIR /app

USER root

RUN apt-get update && apt-get install -y \
    openjdk-17-jre-headless \
    libgtk-3-0 \
    libglu1-mesa \
    libxrender1 \
    libxtst6 \
    && rm -rf /var/lib/apt/lists/*

RUN chown -R ntu-user:ntu-user /app

COPY --from=build --chown=ntu-user:ntu-user /app/target/CloudFileSystem-1.0-SNAPSHOT.jar app.jar

ENV DB_URL=jdbc:mysql://db-node:3306/lbcsystem
ENV DISPLAY=:0

USER ntu-user

ENTRYPOINT ["java", "-Dprism.order=sw", "-jar", "app.jar"]