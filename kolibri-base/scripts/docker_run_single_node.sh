#!/usr/bin/env bash

HTTP_SERVER_PORT=8000

sudo docker run \
--net host \
-p $HTTP_SERVER_PORT:$HTTP_SERVER_PORT \
-e PROFILE='local' \
-e ROLES='compute,httpserver' \
-e CLUSTER_NODE_HOST='127.0.0.1' \
-e CLUSTER_NODE_PORT='0' \
-e HTTP_SERVER_INTERFACE='0.0.0.0' \
-e HTTP_SERVER_PORT=$HTTP_SERVER_PORT \
-e MANAGEMENT_HOST='127.0.0.1' \
-e MANAGEMENT_PORT='8558' \
-e SINGLE_NODE='true' \
-e DISCOVERY_SERVICE_NAME='kolibri-service' \
-e KOLIBRI_USER='kolibri_local' \
-e KOLIBRI_USER_ID=$UID \
-d \
kolibri-base:0.1.0-alpha2
