#  Fitness Microservices — Free Cloud Deployment Guide

> **Complete beginner friendly.** Every step explained in detail.
> Based on your **actual config files** read from the project.

---

##  Your Questions Answered First

### "Why does Keycloak need its own database? I already have a user-service database."

This is a great question. Here is the simple answer:

**Keycloak** stores authentication data:
- Password hashes (encrypted passwords so even admins cannot read them)
- Active login sessions (who is currently logged in)
- OAuth tokens and refresh tokens
- Realm settings (your `fitness-app` realm config, client settings, roles)
- JWT signing keys

**Your user-service** stores application profile data:
- User's name, email, fitness goals
- Which Keycloak user maps to which app record (`keycloakId` field)

They **cannot share the same database** because Keycloak uses its own internal table structure that has nothing to do with your app's tables. Think of it like this:

```
When a user registers:
  1. Keycloak stores:  username "saif", hashed-password, session, realm config
                       in Keycloak's own PostgreSQL database

  2. User-Service stores:  name "Saif Shamsi", email, keycloakId "abc-123"
                           in your app's PostgreSQL database
```

They are two completely different systems that happen to both involve "users" but for different purposes.

---

##  Your Real Architecture (from actual config files)

| Service | Port | Database | Key Env Variables (from config YMLs) |
|---------|------|----------|--------------------------------------|
| **Config Server** | 8888 | None | Serves YML files from classpath/config/ |
| **Eureka** | 8761 | None | Service registry, no persistence |
| **Gateway** | 8080 | None | Routes via Eureka lb://, validates JWT via Keycloak JWKS |
| **User Service** | 8086 | **PostgreSQL** | `FITNESS_USER_DB_URL/USERNAME/PASSWORD`, Keycloak admin creds |
| **Activity Service** | 8082 | **MongoDB** | `DB_URL`, `DB_NAME`, Kafka bootstrap |
| **AI Service** | 8083 | **MongoDB** | `AI_DB_URL`, `DB_NAME`, Kafka bootstrap, `OPENROUTER_API_KEY` |
| **Keycloak** | 8181 | **PostgreSQL** (its own) | Realm config, sessions, credentials |

> Note: Activity service uses `DB_URL`, AI service uses `AI_DB_URL` — different variable names. Both share `DB_NAME`.

### How services communicate:

```
Android App
    |
    v
[Gateway :8080]  ---- validates JWT ---->  [Keycloak :8181]
    |                                              |
    |-- /api/users/**  --> [User Service :8086]    |-- stores sessions/creds in Keycloak PostgreSQL
    |-- /api/auth/**   --> [User Service :8086] <--+-- admin API (create/reset users)
    |                          |
    |                    PostgreSQL (user profiles) [Neon]
    |
    |-- /api/activities/** --> [Activity Service :8082]
    |                               |              |
    |                         MongoDB [Atlas]      |-- publishes to Kafka
    |                                              v
    |-- /api/recommendations/** --> [AI Service :8083]  <-- consumes from Kafka
                                          |
                                    MongoDB [Atlas]
                                    (recommendations)
```

---

##  Free Services to Use (matched to your actual stack)

| What | Provider | Free Tier |
|------|----------|-----------|
| All Spring Boot apps + Keycloak | [Railway](https://railway.app) | $5/month credit, no sleep |
| **PostgreSQL** (user-service + Keycloak) | [Neon](https://neon.tech) | 0.5 GB, free forever |
| **MongoDB** (activity-service + ai-service) | [MongoDB Atlas](https://cloud.mongodb.com) | 512 MB M0 cluster, free forever |
| **Kafka** (activity-events topic) | [Upstash Kafka](https://upstash.com/kafka) | 10,000 msgs/day, free forever |

> Railway is better than Render here because it does NOT sleep your services on the free tier.

---

##  Step 0: Create These Accounts First

1. [railway.app](https://railway.app) Sign up with GitHub
2. [neon.tech](https://neon.tech) Sign up with GitHub
3. [cloud.mongodb.com](https://cloud.mongodb.com) Create free account
4. [upstash.com](https://upstash.com) Sign up with GitHub

---

##  Step 1: PostgreSQL on Neon (User Service + Keycloak)

### 1.1 Create Neon Project

1. Go to [neon.tech](https://neon.tech) -> New Project
2. Name: `fitness-postgres`
3. Region: Pick closest to your users (AWS ap-south-1 for India)
4. Click Create Project

You will see a connection string like:
```
postgresql://saif:password123@ep-cool-name-12345.ap-south-1.aws.neon.tech/neondb?sslmode=require
```

Save these parts separately:
- Host: `ep-cool-name-12345.ap-south-1.aws.neon.tech`
- Username: `saif`
- Password: `password123`

### 1.2 Create Two Databases

Go to SQL Editor in Neon dashboard and run:

```sql
CREATE DATABASE userservice;
CREATE DATABASE keycloak;
```

Your JDBC URLs will be:
```
For user-service:
jdbc:postgresql://ep-xxxx.neon.tech/userservice?sslmode=require

For Keycloak:
jdbc:postgresql://ep-xxxx.neon.tech/keycloak?sslmode=require
```

> IMPORTANT: Always keep ?sslmode=require at the end. Neon refuses connections without SSL.

---

##  Step 2: MongoDB Atlas (Activity Service + AI Service)

Both services use MongoDB. They can share one free Atlas cluster.

### 2.1 Create Free Cluster

1. Go to [cloud.mongodb.com](https://cloud.mongodb.com)
2. Create a New Project -> name it `fitness-app`
3. Build a Cluster -> choose M0 Free (forever free, 512 MB)
4. Cloud Provider: AWS, Region: closest to you
5. Cluster Name: `fitness-cluster`
6. Click Create

### 2.2 Create a Database User

1. Left sidebar -> Database Access -> Add New Database User
2. Username: `fitness-user`
3. Password: generate a strong one and save it
4. Role: Atlas admin
5. Click Add User

### 2.3 Allow All IPs (required for Railway)

1. Left sidebar -> Network Access -> Add IP Address
2. Click "Allow Access From Anywhere" (adds 0.0.0.0/0)
3. This is needed because Railway IPs change dynamically

### 2.4 Get Your Connection String

1. Clusters page -> Connect -> Connect your application
2. Driver: Java, Version: 4.3+
3. Copy the string. It looks like:
```
mongodb+srv://fitness-user:yourpassword@fitness-cluster.abc12.mongodb.net/?retryWrites=true&w=majority
```

You will use this string as:

| Service | Env Variable Name | Value |
|---------|------------------|-------|
| Activity Service | `DB_URL` | the mongodb+srv string above |
| AI Service | `AI_DB_URL` | same mongodb+srv string |
| Both | `DB_NAME` | `fitness_db` (any name you choose) |

Note: Both services connect to the same cluster. Activity service writes to `activities` collection, AI service reads `activities` and writes to `recommendations` collection.

---

##  Step 3: Kafka on Upstash (activity-events topic)

### 3.1 Create Kafka Cluster

1. Go to [upstash.com](https://upstash.com) -> Kafka -> Create Cluster
2. Name: `fitness-kafka`
3. Region: same as your other services
4. Click Create

### 3.2 Create the Topic

1. In your cluster -> Topics -> Create Topic
2. Name: `activity-events`  <- must match exactly what is in your config YMLs
3. Partitions: 1
4. Click Create

### 3.3 Save Your Credentials

From the Details tab:
```
Bootstrap Server: lasting-lemur-12345-us1-kafka.upstash.io:9092
Username: abc123
Password: your-long-password
```

---

##  Step 4: Deploy Keycloak on Railway

Keycloak must be deployed and running BEFORE all other services because the gateway validates tokens against it.

### 4.1 Create Railway Project

1. Go to [railway.app](https://railway.app) -> New Project -> Empty Project
2. Name it: `fitness-microservices`

### 4.2 Add Keycloak Service

1. Add Service -> Docker Image
2. Image: `quay.io/keycloak/keycloak:24.0.1`

### 4.3 Set Environment Variables for Keycloak

Click the service -> Variables tab -> add:

```
KC_DB=postgres
KC_DB_URL=jdbc:postgresql://ep-xxxx.neon.tech/keycloak?sslmode=require
KC_DB_USERNAME=your-neon-username
KC_DB_PASSWORD=your-neon-password
KC_HOSTNAME_STRICT=false
KC_HOSTNAME_STRICT_HTTPS=false
KC_HTTP_ENABLED=true
KC_PROXY=edge
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=YourStrongAdminPassword123
```

### 4.4 Set Start Command

Settings -> Custom Start Command:
```
start --optimized
```

### 4.5 Generate Public URL

Settings -> Networking -> Generate Domain

You will get: `keycloak-production-1234.up.railway.app`

SAVE THIS. This is your KEYCLOAK_PUBLIC_URL used in all other services.

### 4.6 Recreate Your Realm in Cloud Keycloak

Option A (easy - export from local):
1. Go to your local Keycloak: http://localhost:8181/admin
2. Click `fitness-app` realm -> Realm Settings -> Action -> Partial Export
3. Check: include groups, roles, clients -> Export
4. In cloud Keycloak admin, click Create Realm -> upload the downloaded JSON file

Option B: Manually create the realm, client (fitness-android-app), enable Direct Access Grants.

Also add this to your Keycloak client Valid Redirect URIs:
```
com.saif.fitnessapp://oauth-callback
```

---

##  Step 5: Update Config Server YML Files and Push to GitHub

Your config server serves these files to every service. Update the localhost references to cloud URLs then git commit and push.

### gateway-service.yml

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: https://YOUR_KEYCLOAK.up.railway.app/realms/fitness-app/protocol/openid-connect/certs
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: user-service-users
              uri: lb://USER-SERVICE
              predicates:
                - Path=/api/users/**
            - id: user-service-auth
              uri: lb://USER-SERVICE
              predicates:
                - Path=/api/auth/**
            - id: activity-service
              uri: lb://ACTIVITY-SERVICE
              predicates:
                - Path=/api/activities/**
            - id: ai-service
              uri: lb://AI-SERVICE
              predicates:
                - Path=/api/recommendations/**
server:
  port: 8080
eureka:
  client:
    serviceUrl:
      defaultZone: https://YOUR_EUREKA.up.railway.app/eureka/
  instance:
    prefer-ip-address: false
```

### user-service.yml (no changes needed — already uses env vars)

```yaml
spring:
  datasource:
    url: ${FITNESS_USER_DB_URL}
    username: ${FITNESS_USER_DB_USERNAME}
    password: ${FITNESS_USER_DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update
    database-platform: org.hibernate.dialect.PostgreSQLDialect
eureka:
  client:
    serviceUrl:
      defaultZone: https://YOUR_EUREKA.up.railway.app/eureka/
  instance:
    prefer-ip-address: false
keycloak:
  server-url: ${KEYCLOAK_SERVER_URL}
  realm: ${KEYCLOAK_REALM}
  admin-client-id: ${KEYCLOAK_ADMIN_CLIENT_ID}
  admin-username: ${KEYCLOAK_ADMIN_USERNAME}
  admin-password: ${KEYCLOAK_ADMIN_PASSWORD}
server:
  port: 8086
```

### activity-service.yml (add SASL config for Upstash)

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    properties:
      security.protocol: SASL_SSL
      sasl.mechanism: SCRAM-SHA-256
      sasl.jaas.config: "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"${KAFKA_USERNAME}\" password=\"${KAFKA_PASSWORD}\";"
kafka:
  topic:
    name: activity-events
eureka:
  client:
    serviceUrl:
      defaultZone: https://YOUR_EUREKA.up.railway.app/eureka/
  instance:
    prefer-ip-address: false
server:
  port: 8082
```

### ai-service.yml (add SASL config for Upstash)

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    consumer:
      group-id: activity-processor-group
      enable-auto-commit: false
      auto-offset-reset: earliest
      max-poll-records: 1
      key-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.key.delegate.class: org.apache.kafka.common.serialization.StringDeserializer
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.use.type.headers: false
        spring.json.value.default.type: com.saif.fitness.aiservice.model.Activity
        spring.json.trusted.packages: "*"
        session.timeout.ms: 120000
        max.poll.interval.ms: 300000
        request.timeout.ms: 120000
        security.protocol: SASL_SSL
        sasl.mechanism: SCRAM-SHA-256
        sasl.jaas.config: "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"${KAFKA_USERNAME}\" password=\"${KAFKA_PASSWORD}\";"
kafka:
  topic:
    name: activity-events
eureka:
  client:
    serviceUrl:
      defaultZone: https://YOUR_EUREKA.up.railway.app/eureka/
  instance:
    prefer-ip-address: false
server:
  port: 8083
openrouter:
  api:
    key: ${OPENROUTER_API_KEY}
    url: ${OPENROUTER_API_URL}
```

After editing all YML files:
```
git add .
git commit -m "Update config YMLs for cloud deployment"
git push
```

---

##  Step 6: Deploy Spring Boot Services on Railway

Deploy IN THIS ORDER. Each one depends on the previous.

### 6.1 Config Server

1. Add Service -> GitHub Repo -> `saifshamsi28/fitness-microservices`
2. Root Directory: `configserver`

Environment Variables:
```
SERVER_PORT=8888
SPRING_PROFILES_ACTIVE=native
```

Generate Domain -> save as CONFIG_SERVER_URL

---

### 6.2 Eureka

1. Add Service -> GitHub Repo -> Root Directory: `eureka`

Environment Variables:
```
SERVER_PORT=8761
SPRING_APPLICATION_NAME=eureka
SPRING_CONFIG_IMPORT=optional:configserver:https://YOUR_CONFIG_SERVER.up.railway.app
```

Generate Domain -> save as EUREKA_URL

---

### 6.3 Gateway

1. Add Service -> GitHub Repo -> Root Directory: `gateway`

Environment Variables:
```
SERVER_PORT=8080
SPRING_APPLICATION_NAME=gateway-service
SPRING_CONFIG_IMPORT=optional:configserver:https://YOUR_CONFIG_SERVER.up.railway.app
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=https://YOUR_EUREKA.up.railway.app/eureka/
```

Generate Domain -> THIS is what you put in the Android app as API_BASE_URL

---

### 6.4 User Service

1. Add Service -> GitHub Repo -> Root Directory: `userservice`

Environment Variables:
```
SERVER_PORT=8086
SPRING_APPLICATION_NAME=user-service
SPRING_CONFIG_IMPORT=optional:configserver:https://YOUR_CONFIG_SERVER.up.railway.app
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=https://YOUR_EUREKA.up.railway.app/eureka/

FITNESS_USER_DB_URL=jdbc:postgresql://ep-xxxx.neon.tech/userservice?sslmode=require
FITNESS_USER_DB_USERNAME=your-neon-username
FITNESS_USER_DB_PASSWORD=your-neon-password

KEYCLOAK_SERVER_URL=https://YOUR_KEYCLOAK.up.railway.app
KEYCLOAK_REALM=fitness-app
KEYCLOAK_ADMIN_CLIENT_ID=admin-cli
KEYCLOAK_ADMIN_USERNAME=admin
KEYCLOAK_ADMIN_PASSWORD=YourKeycloakAdminPassword
```

---

### 6.5 Activity Service

1. Add Service -> GitHub Repo -> Root Directory: `activityservice`

Environment Variables:
```
SERVER_PORT=8082
SPRING_APPLICATION_NAME=activity-service
SPRING_CONFIG_IMPORT=optional:configserver:https://YOUR_CONFIG_SERVER.up.railway.app
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=https://YOUR_EUREKA.up.railway.app/eureka/

DB_URL=mongodb+srv://fitness-user:yourpassword@fitness-cluster.abc.mongodb.net/?retryWrites=true&w=majority
DB_NAME=fitness_db

KAFKA_BOOTSTRAP_SERVERS=lasting-lemur-xxxx.upstash.io:9092
KAFKA_USERNAME=your-upstash-username
KAFKA_PASSWORD=your-upstash-password
```

---

### 6.6 AI Service

1. Add Service -> GitHub Repo -> Root Directory: `aiservice`

Environment Variables:
```
SERVER_PORT=8083
SPRING_APPLICATION_NAME=ai-service
SPRING_CONFIG_IMPORT=optional:configserver:https://YOUR_CONFIG_SERVER.up.railway.app
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=https://YOUR_EUREKA.up.railway.app/eureka/

AI_DB_URL=mongodb+srv://fitness-user:yourpassword@fitness-cluster.abc.mongodb.net/?retryWrites=true&w=majority
DB_NAME=fitness_db

KAFKA_BOOTSTRAP_SERVERS=lasting-lemur-xxxx.upstash.io:9092
KAFKA_USERNAME=your-upstash-username
KAFKA_PASSWORD=your-upstash-password

OPENROUTER_API_KEY=your-openrouter-api-key
OPENROUTER_API_URL=https://openrouter.ai/api/v1/chat/completions
```

---

##  Step 7: Update Android App

Open `android-fitness-app/local.properties`:

```properties
KEYCLOAK_DEVICE_URL=https://your-keycloak.up.railway.app
API_DEVICE_URL=https://your-gateway.up.railway.app

KEYCLOAK_EMULATOR_URL=http://10.0.2.2:8181
API_EMULATOR_URL=http://10.0.2.2:8080

KEYCLOAK_REALM=fitness-app
KEYCLOAK_CLIENT_ID=fitness-android-app
```

AuthConfig.java already switches between emulator and device URLs using isEmulator() so no Java changes needed.

Rebuild:
```
./gradlew assembleDebug
```

---

##  Step 8: Health Check

Test in browser after deployment:

```
1. https://your-keycloak.up.railway.app/realms/fitness-app
   Expected: JSON with realm info

2. https://your-configserver.up.railway.app/user-service/default
   Expected: Your user-service.yml contents

3. https://your-eureka.up.railway.app/
   Expected: Eureka dashboard showing all 4 registered services

4. https://your-gateway.up.railway.app/api/auth/health
   Expected: "Auth service is running"
```

---

##  Common Problems and Fixes

### Keycloak HTTPS required error
Add to Keycloak env vars:
```
KC_PROXY=edge
KC_HOSTNAME_STRICT_HTTPS=false
KC_HTTP_ENABLED=true
```

### User service cannot connect to Keycloak admin API
KEYCLOAK_SERVER_URL must be the full URL with https:// and NO trailing slash.
Correct:   https://keycloak-production-1234.up.railway.app
Wrong:     https://keycloak-production-1234.up.railway.app/

### Kafka SASL authentication failed
Make sure the SASL block is in both activity-service.yml and ai-service.yml under spring.kafka.properties. Upstash requires SCRAM-SHA-256 over SSL.

### MongoDB Atlas connection timeout
You must add 0.0.0.0/0 to Network Access in MongoDB Atlas. Railway uses dynamic IPs that change, so you cannot whitelist a specific IP.

### Neon SSL error
Always append ?sslmode=require to every Neon JDBC URL. Without it Neon refuses the connection.

### Gateway 503 No instances available
Eureka may not have registered all services yet. Wait 30-60 seconds after startup. Check https://your-eureka.up.railway.app/ to see which services are registered.

### Keycloak password reset emails not sending
Keycloak needs SMTP. Go to Keycloak Admin -> fitness-app realm -> Realm Settings -> Email:
```
SMTP Host: smtp.gmail.com
Port: 587
Enable StartTLS: ON
From: youremail@gmail.com
Username: youremail@gmail.com
Password: Gmail App Password (NOT your regular password)
         Get it from: https://myaccount.google.com/apppasswords
```

---

##  Free Tier Limits

| Provider | Free Amount | Runs Out When |
|----------|------------|---------------|
| Railway | $5/month | Running 6 services 24/7 uses ~$4-5/month |
| Neon PostgreSQL | 0.5 GB storage | Millions of user records (not an issue early on) |
| MongoDB Atlas M0 | 512 MB storage | Millions of activity records (not an issue early on) |
| Upstash Kafka | 10,000 msgs/day | Users log more than 10,000 activities in a day |

---

##  Deployment Order Summary

```
1.  Create Neon project -> create databases: userservice and keycloak
2.  Create MongoDB Atlas M0 cluster -> create db user -> allow all IPs
3.  Create Upstash Kafka cluster -> create topic: activity-events
4.  Deploy Keycloak on Railway -> set env vars -> generate domain -> import realm
5.  Update gateway-service.yml, activity-service.yml, ai-service.yml with cloud URLs
6.  git commit and git push all config YML changes
7.  Deploy Config Server on Railway -> generate domain
8.  Deploy Eureka on Railway -> generate domain
9.  Deploy Gateway on Railway -> generate domain (this is your Android API_DEVICE_URL)
10. Deploy User Service on Railway -> set all env vars
11. Deploy Activity Service on Railway -> set all env vars
12. Deploy AI Service on Railway -> set all env vars
13. Update local.properties in Android app with new URLs -> rebuild -> test
```
