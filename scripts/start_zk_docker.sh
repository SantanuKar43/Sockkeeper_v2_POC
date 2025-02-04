#!/bin/bash

docker run -d --name zookeeper \
  -p 2181:2181 \
  -e ZOO_PORT=2181 \
  zookeeper:latest