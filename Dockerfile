FROM adoptopenjdk/openjdk13:jdk-13.0.2_8-alpine-slim

ENV KOLIBRI_USER kolibri
ENV KOLIBRI_USER_ID 1000

RUN mkdir -p /app/logs
RUN mkdir -p /app/data
RUN apk --no-cache add curl
WORKDIR /app

COPY target/scala-2.13/kolibri-base.0.1.0-alpha2.jar app.jar

RUN addgroup -g ${KOLIBRI_USER_ID} ${KOLIBRI_USER} && \
    adduser -H -D  -u ${KOLIBRI_USER_ID} -G ${KOLIBRI_USER} ${KOLIBRI_USER} && \
    chown -R ${KOLIBRI_USER}:${KOLIBRI_USER} /app

USER ${KOLIBRI_USER}

EXPOSE ${HTTP_SERVER_PORT}
EXPOSE ${MANAGEMENT_PORT}
EXPOSE ${CLUSTER_NODE_PORT}

ENTRYPOINT java -Dapplication.home="/app" -cp app.jar de.awagen.kolibri.base.cluster.ClusterNode true