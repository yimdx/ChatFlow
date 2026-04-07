# AWS Deployment Guide (Full Stack)

This guide deploys all required ChatFlow components on AWS EC2:
- RabbitMQ
- PostgreSQL
- server-v2
- consumer-v3
- client-part2

## 1. Topology

Recommended 5 EC2 instances:
- `rabbitmq-host`
- `postgres-host`
- `server-host`
- `consumer-host`
- `client-host`

## 2. Security Group Ports

Open only required ports.

RabbitMQ host:
- `5672` from server/consumer
- `15672` from your admin IP only

PostgreSQL host:
- `5432` from server/consumer

Server host:
- `8080` health (optional admin access)
- `8081` WebSocket (client traffic)
- `8082` broadcast endpoint (from consumer only)
- `8083` metrics API (from client/admin)

## 3. Build Artifacts Locally

```bash
cd deployment
./build-all.sh
```

Expected artifacts:
- `server-v2/target/WebSocketServer-1.0-SNAPSHOT.jar`
- `consumer-v3/target/MessageConsumerV3-1.0-SNAPSHOT.jar`
- `client-part2/target/*.jar`

## 3.1 Configure Once (Recommended)

All deployment scripts support `--config` now.

Create your config files from templates:

```bash
cd deployment/config
cp aws.env.example aws.env
cp server.env.example server.env
cp consumer.env.example consumer.env
cp client.env.example client.env
cp postgres.env.example postgres.env
```

Then edit each `*.env` file with your EC2 private IPs, credentials, and ports.

## 4. Setup RabbitMQ Host

```bash
scp -i <key.pem> setup-rabbitmq.sh <ec2-user>@<rabbitmq-host>:~/chatflow-deploy/
ssh -i <key.pem> <ec2-user>@<rabbitmq-host>
cd ~/chatflow-deploy
chmod +x setup-rabbitmq.sh
./setup-rabbitmq.sh
```

Default credentials created by script:
- user: `admin`
- password: `adminpassword`

## 5. Setup PostgreSQL Host

```bash
scp -i <key.pem> setup-postgres.sh <ec2-user>@<postgres-host>:~/chatflow-deploy/
ssh -i <key.pem> <ec2-user>@<postgres-host>
cd ~/chatflow-deploy
chmod +x setup-postgres.sh
./setup-postgres.sh --config ./config/postgres.env
```

## 6. Deploy server-v2 Host

Upload jar and script:

```bash
scp -i <key.pem> ../server-v2/target/WebSocketServer-1.0-SNAPSHOT.jar <ec2-user>@<server-host>:~/chatflow-deploy/
scp -i <key.pem> deploy-server.sh <ec2-user>@<server-host>:~/chatflow-deploy/
```

Run:

```bash
ssh -i <key.pem> <ec2-user>@<server-host>
cd ~/chatflow-deploy
chmod +x deploy-server.sh
./deploy-server.sh --config ./config/server.env
```

Ports used:
- health: `8080`
- websocket: `8081`
- broadcast: `8082`
- metrics: `8083`

## 7. Deploy consumer-v3 Host

Upload jar and script:

```bash
scp -i <key.pem> ../consumer-v3/target/MessageConsumerV3-1.0-SNAPSHOT.jar <ec2-user>@<consumer-host>:~/chatflow-deploy/
scp -i <key.pem> deploy-consumer-v3.sh <ec2-user>@<consumer-host>:~/chatflow-deploy/
```

Run:

```bash
ssh -i <key.pem> <ec2-user>@<consumer-host>
cd ~/chatflow-deploy
chmod +x deploy-consumer-v3.sh
./deploy-consumer-v3.sh --config ./config/consumer.env
```

## 8. Deploy and Run client-part2

Upload jar and script:

```bash
CLIENT_JAR=$(find ../client-part2/target -maxdepth 1 -type f -name "*.jar" ! -name "original-*" | sort | tail -n 1)
scp -i <key.pem> "$CLIENT_JAR" <ec2-user>@<client-host>:~/chatflow-deploy/client-part2-runner.jar
scp -i <key.pem> deploy-client-part2.sh <ec2-user>@<client-host>:~/chatflow-deploy/
```

Run benchmark:

```bash
ssh -i <key.pem> <ec2-user>@<client-host>
cd ~/chatflow-deploy
chmod +x deploy-client-part2.sh
./deploy-client-part2.sh --config ./config/client.env
```

The client logs metrics API JSON after test completion into `client-part2.log`.

## 9. One-Command Orchestrator (Optional)

You can also run `aws-deploy-all.sh` from local machine.

Required env vars:
- `SSH_KEY_PATH`
- `EC2_USER`
- `RABBITMQ_HOST`
- `POSTGRES_HOST`
- `SERVER_HOST`
- `CONSUMER_HOST`
- `CLIENT_HOST`

Example:

```bash
cd deployment
chmod +x aws-deploy-all.sh
./aws-deploy-all.sh --config ./config/aws.env
```

`aws.env` supports multiple hosts for scale tests:

- `CONSUMER_HOSTS=ip1,ip2,ip3`
- `CLIENT_HOSTS=ip4,ip5`

## 10. Health Checks

```bash
curl http://<server-host>:8080/health
curl http://<server-host>:8083/health
curl http://<server-host>:8083/metrics
```

## 11. Notes

- Use private IPs for intra-VPC communication.
- Restrict `8082` broadcast endpoint to consumer host only.
- Rotate default credentials before production use.
