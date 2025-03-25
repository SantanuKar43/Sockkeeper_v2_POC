#!/bin/bash

create_topic() {
  $PULSAR_ADMIN_PATH topics create $1
  if [ $? -eq 0 ]; then
      echo "Successfully created topic: $1"
    else
      echo "Failed to create topic: $1"
  fi
}

create_partitioned_topic() {
  $PULSAR_ADMIN_PATH topics create-partitioned-topic -p $2 $1
  if [ $? -eq 0 ]; then
      echo "Successfully created topic: $1"
    else
      echo "Failed to create topic: $1"
  fi
}

if [ -z "$1" ]; then
  echo "Usage: $0 <number_of_partitions>"
  exit 1
fi

N=$1
PULSAR_ADMIN_PATH=~/Downloads/apache-pulsar-4.0.2/bin/pulsar-admin
NAMESPACE=public/default
TOPIC_NAME="sk-node-topic"

create_partitioned_topic ${NAMESPACE}/${TOPIC_NAME} ${N}

for ((i=0; i<N; i++)); do
  RETRY_TOPIC_NAME=${TOPIC_NAME}-partition-${i}-sock-subscription-RETRY
  create_topic ${NAMESPACE}/${RETRY_TOPIC_NAME}
done

create_topic ${NAMESPACE}/sockkeeper-sideline
create_topic ${NAMESPACE}/sockkeeper-sideline-sock-subscription-RETRY