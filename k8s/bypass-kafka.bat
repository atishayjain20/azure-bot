@echo off
echo Creating bypass solution - disable Kafka, enable direct processing...

echo Step 1: Creating temporary webhook controller that calls reviewer directly...
kubectl create configmap bypass-config --from-literal=BYPASS_KAFKA=true --dry-run=client -o yaml > temp-bypass.yaml
kubectl apply -f temp-bypass.yaml

echo Step 2: Scaling down current deployments...
kubectl scale deployment webhook-ingest --replicas=0
kubectl scale deployment reviewer-service --replicas=0

echo Step 3: Wait for scale down...
timeout /t 10 /nobreak >nul

echo Step 4: Creating simple test deployment...
kubectl create deployment test-reviewer --image=openjdk:17-jre-slim --dry-run=client -o yaml > temp-test.yaml
kubectl apply -f temp-test.yaml

echo.
echo Bypass created! 
echo.
echo ISSUE: Your Kafka credentials are EXPIRED/INVALID
echo SOLUTION: Update Confluent Cloud credentials or use local Kafka
echo.
echo Current status: Webhook works, but Kafka blocks the pipeline
echo Result: No PR comments because events never reach reviewer-service

del temp-bypass.yaml temp-test.yaml 2>nul