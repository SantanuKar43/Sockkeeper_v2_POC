#!/bin/bash

eval $(minikube docker-env)  # Use Minikube's Docker daemon
docker build -t sockkeeper:latest .
