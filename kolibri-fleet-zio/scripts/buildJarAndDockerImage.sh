#!/bin/sh

cd ..
./scripts/buildKolibriFleetZIOJar.sh
cd "$OLDPWD"
docker build . -t kolibri-fleet-zio:0.1.5
