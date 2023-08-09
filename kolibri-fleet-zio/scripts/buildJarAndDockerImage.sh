#!/bin/sh

cd ..
./scripts/buildKolibriFleetZIOJar.sh || exit 1
cd "$OLDPWD"
docker build . -t kolibri-fleet-zio:0.2.1
