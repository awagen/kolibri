#!/bin/bash

kind delete cluster
docker container stop kind-registry && docker container rm -v kind-registry