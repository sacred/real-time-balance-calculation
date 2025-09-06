# real-time-balance-calculation
A high-performance, resilient Java-based financial transaction processing system deployed on Kubernetes.

---
## Architecture Design
### Tech architecture
#### Key points
- [ ] Use Redis as a hot-cache for account data; dual-write ensures consistency, and the balance-query service reads straight from cache.
- [ ] Lock account writes to prevent concurrent updates, with internal retries.
- [ ] For A-to-B transfers, use the transaction ID with Redis to guarantee idempotence—duplicate external calls are safely accepted.
- [ ] In-flight records left by crashes or restarts are swept periodically so they can be re-processed.
- [ ] Support graceful shutdown and rolling releases during service deployments.

<img width="1016" height="996" alt="image" src="https://github.com/user-attachments/assets/ce1ce03a-9f97-4705-b2f1-b6e9e37e016f" />

### API Design
#### 1. Process Single Transaction
Processes a single financial transaction between two accounts.

**Endpoint:** `POST /api/transactions/single`  
**Content-Type:** `application/json`

##### Request Body
```json 
{ 
  "transactionId": "string",
  "sourceAccount": "string", 
  "destinationAccount": "string", 
  "amount": "number"
}
```
##### Response
```json
{ "code": "integer", "success": "boolean", "message": "string", "data": { "transactionId": "string", "success": "boolean", "message": "string", "errorCode": "string (optional)" }, "timestamp": "long" }
 ```
#### 2. Process Batch Transactions
Processes multiple financial transactions in a single request.

**Endpoint:** `POST /api/transactions/batch`  
**Content-Type:** `application/json`
##### Request Body
```json 
[ 
  { "transactionId": "string", 
    "sourceAccount": "string", 
    "destinationAccount": "string", 
    "amount": "number"
  }
]
```
##### Response
```json 
{ "code": "integer", "success": "boolean", "message": "string", "data": { "batchId": "string", "totalTransactions": "integer", "successfulTransactions": "integer", "failedTransactions": "integer", "results": [ { "transactionId": "string", "success": "boolean", "message": "string", "errorCode": "string" } ] }, "timestamp": "long" }
```


### Deployment architecture
![deploy](https://github.com/user-attachments/assets/b57222a1-bdb6-44dc-bd87-b91ea984f203)

---
## Building
build image with maven and docker

##### Building JAR package.
```bash
mvn clean package -DskipTests
```
#### Build Docker images

```bash
docker build -t repo/real-time-balance-calculation:latest .
docker build -t repo/real-time-balance-calculation:1.0.0 .
```

---
## Testing 
#### Unit Testing
Unit testing with JUnit5 and Jacoco, the report file in the directory `target/site/jacoco`
```bash
mvn test jacoco:report
```
[Unit Testing Report](target/site/jacoco/index.html)

<img width="2184" height="442" alt="image" src="https://github.com/user-attachments/assets/da2bfd79-6b5d-4402-a776-38624300c6c5" />

#### Load testing
load testing with Jmeter
- insert account.csv data to DB, use `file/account.csv`
- load `file/jmeter/balance-calculation.jmx` to jmeter
- the transaction data is in `file/transaction.csv`
- run the test


[Jmeter test plan](file/jmeter/balance-calculation.jmx)


---
## Deployment 
Including local deployment and K8s deployments.

### On Perm
Install containers with docker-compose/podman-compose

Will start up DB¡¢cache and the application
Local testing, for testing use

#### startup containers
```bash
bash deployment/compose/start.sh
```
<img width="2690" height="224" alt="image" src="https://github.com/user-attachments/assets/70de5f2e-c28f-4c8c-b51d-fbdb4fbf25f3" />

#### shutdown containers
```bash
bash deployment/compose/stop.sh
```

### On K8 Cluster
K8s deployment By Helm chart

#### Key points
- [ ] Liveness Probe and readiness probe with `actuator/health`
- [ ] PodAntiAffinity topology to multiple zone for high availability
- [ ] HAP Probe cpu usage > 80%
- [ ] Terminate gracefully with k8 Rolling update and SpringBoot shutdown

#### Deploy to K8s cluster
```bash
bash deployment/k8s/install.sh
```


#### UnDeploy helm chart
```bash
bash deployment/k8s/uninstall.sh
```

