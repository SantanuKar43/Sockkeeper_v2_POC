#!/bin/bash

create_topic() {
  $PULSAR_ADMIN_PATH topics create $1
  if [ $? -eq 0 ]; then
      echo "Successfully created topic: $1"
    else
      echo "Failed to create topic: $1"
  fi
}

if [ -z "$1" ]; then
  echo "Usage: $0 <number_of_topics>"
  exit 1
fi

N=$1
PULSAR_ADMIN_PATH=~/Downloads/apache-pulsar-4.0.2/bin/pulsar-admin
NAMESPACE=public/default

for ((i=0; i<N; i++)); do
  TOPIC_NAME="sk-node-${i}-topic"
  RETRY_TOPIC_NAME=${TOPIC_NAME}-sock-subscription-RETRY

  create_topic ${NAMESPACE}/${TOPIC_NAME}
  create_topic ${NAMESPACE}/${RETRY_TOPIC_NAME}
done

create_topic ${NAMESPACE}/sockkeeper-sideline
create_topic ${NAMESPACE}/sockkeeper-sideline-sock-subscription-RETRY