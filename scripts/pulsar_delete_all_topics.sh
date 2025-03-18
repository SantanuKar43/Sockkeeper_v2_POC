#!/bin/bash

PULSAR_ADMIN_PATH=~/Downloads/apache-pulsar-4.0.2/bin/pulsar-admin
NAMESPACE=public/default

for topic in $($PULSAR_ADMIN_PATH topics list $NAMESPACE); do
  $PULSAR_ADMIN_PATH topics delete $topic
  if [ $? -eq 0 ]; then
      echo "Successfully deleted topic: $topic"
    else
      echo "Failed to delete topic: $topic"
  fi
done