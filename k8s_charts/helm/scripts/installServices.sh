#!/bin/bash

KOLIBRI_BASE_VERSION=0.1.0-rc2
KOLIBRI_WATCH_VERSION=0.1.0-rc0
RESPONSE_JUGGLER_VERSION=0.1.0

full_current_dir=$(pwd)
relative_script_path=$(dirname "$0")

# change dir to the actual script path
cd $relative_script_path

# pull the images
docker pull awagen/kolibri-base:$KOLIBRI_BASE_VERSION
docker pull awagen/kolibri-watch:$KOLIBRI_WATCH_VERSION
docker pull awagen/response-juggler:$RESPONSE_JUGGLER_VERSION
# tag the images and push to local kind repo
docker tag awagen/kolibri-base:$KOLIBRI_BASE_VERSION localhost:5000/kolibri-base:$KOLIBRI_BASE_VERSION
docker tag awagen/kolibri-watch:$KOLIBRI_WATCH_VERSION localhost:5000/kolibri-watch:$KOLIBRI_WATCH_VERSION
docker tag awagen/response-juggler:$RESPONSE_JUGGLER_VERSION localhost:5000/response-juggler:$RESPONSE_JUGGLER_VERSION
docker push localhost:5000/kolibri-base:$KOLIBRI_BASE_VERSION
docker push localhost:5000/kolibri-watch:$KOLIBRI_WATCH_VERSION
docker push localhost:5000/response-juggler:$RESPONSE_JUGGLER_VERSION
# install services
helm install kolibri-service --debug ../charts/kolibri-service
helm install kolibri-watch --debug ../charts/kolibri-watch
helm install response-juggler --debug ../charts/response-juggler
# change dir back to the directory the script was called from
cd $full_current_dir