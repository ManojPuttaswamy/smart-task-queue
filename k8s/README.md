# Kubernetes Deployment — Smart Task Queue

## Prerequisites

```bash
# Install Minikube
brew install minikube

# Start Minikube with enough resources
minikube start --cpus=4 --memory=4096

# Enable Metrics Server (required for HPA)
minikube addons enable metrics-server

# Point Docker to Minikube's Docker daemon
# This lets Minikube use locally built images without a registry
eval $(minikube docker-env)

# Build the Spring Boot image inside Minikube's Docker
cd ..
docker build -t smart-task-queue-app:latest .
```

## Deploy

```bash
# Apply in order — dependencies first
kubectl apply -f k8s/secret.yml
kubectl apply -f k8s/configmap.yml
kubectl apply -f k8s/postgres.yml
kubectl apply -f k8s/redis.yml
kubectl apply -f k8s/app-deployment.yml
kubectl apply -f k8s/hpa.yml

# Watch everything come up
kubectl get pods -w
```

## Verify

```bash
# Check all pods are Running
kubectl get pods

# Check services
kubectl get services

# Check HPA status
kubectl get hpa

# Get the URL to access the app
minikube service smartqueue-app-service --url

# View app logs
kubectl logs -l app=smartqueue-app --tail=50 -f
```

## Useful Commands

```bash
# Describe a pod (shows events, errors)
kubectl describe pod <pod-name>

# Shell into a pod
kubectl exec -it <pod-name> -- /bin/sh

# Scale manually (HPA will override this)
kubectl scale deployment smartqueue-app --replicas=3

# Trigger HPA by generating load
kubectl run load-test --image=busybox --restart=Never -- \
  sh -c "while true; do wget -q -O- http://smartqueue-app-service:8080/actuator/health; done"

# Watch HPA react
kubectl get hpa -w
```

## Teardown

```bash
# Delete all resources
kubectl delete -f k8s/

# Stop Minikube
minikube stop

# Delete Minikube cluster entirely
minikube delete
```

## Architecture

```
                    ┌───────────────────────────────────┐
                    │         Kubernetes Cluster        │
                    │                                   │
  Your Mac  ──────► │  NodePort:30080                   │
                    │       │                           │
                    │  ┌────▼────────────────────────┐  │
                    │  │   smartqueue-app-service    │  │
                    │  │   (load balancer)           │  │
                    │  └────┬─────────────┬───────── ┘  │
                    │       │             │             │
                    │  ┌────▼────┐   ┌────▼────┐        │
                    │  │  app    │   │  app    │  (HPA) │
                    │  │ pod #1  │   │ pod #2  │  2-5   │
                    │  └────┬────┘   └────┬────┘  pods  │
                    │       └──────┬──────┘             │
                    │         ┌────▼──────────────────┐ │
                    │         │  postgres-service     │ │
                    │         │  redis-service        │ │
                    │         └───────────────────────┘ │
                    └───────────────────────────────────┘
```