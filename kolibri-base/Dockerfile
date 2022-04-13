FROM adoptopenjdk/openjdk11:jdk-11.0.11_9-alpine-slim

ENV JVM_OPTS "-XX:+UseG1GC -Xms512m -Xmx1024m"

RUN mkdir -p /app/logs
RUN chown -R 1000:1000 /app
RUN apk --no-cache add curl eudev
WORKDIR /app

COPY target/scala-2.13/kolibri-base.0.1.0-rc4.jar app.jar

EXPOSE ${HTTP_SERVER_PORT}
EXPOSE ${MANAGEMENT_PORT}
EXPOSE ${CLUSTER_NODE_PORT}

ENTRYPOINT java ${JVM_OPTS} -Dapplication.home="/app" -cp app.jar de.awagen.kolibri.base.cluster.ClusterNode true