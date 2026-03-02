# Fitness App — Complete Deployment Guide (Render + Free Tiers)

> **Stack:** Spring Boot 4 · Java 21 · Spring Cloud 2025.1.0 · Keycloak 26  
> **Total monthly cost: ~$0** (all free tiers, no Railway needed)  
> **Last updated:** March 2026

---

## Table of Contents

1. [Architecture & Platform Map](#1-architecture--platform-map)
2. [Render: Docker Hub vs Direct GitHub — Which to use?](#2-render-docker-hub-vs-direct-github--which-to-use)
3. [Railway $5 — Honest Analysis](#3-railway-5--honest-analysis)
4. [Keeping Render Free Tier Awake (No Sleep)](#4-keeping-render-free-tier-awake-no-sleep)
5. [Phase 1 — MongoDB Atlas (Free)](#5-phase-1--mongodb-atlas-free)
6. [Phase 2 — PostgreSQL on Supabase (Free)](#6-phase-2--postgresql-on-supabase-free)
7. [Phase 3 — Upstash Kafka (Free, 10k msg/day)](#7-phase-3--upstash-kafka-free-10k-msgday)
8. [Phase 4 — Keycloak on Oracle Cloud (Free forever)](#8-phase-4--keycloak-on-oracle-cloud-free-forever)
9. [Phase 5 — Deploy Config Server + Eureka on Render](#9-phase-5--deploy-config-server--eureka-on-render)
10. [Phase 6 — Deploy Gateway on Render](#10-phase-6--deploy-gateway-on-render)
11. [Phase 7 — Deploy User Service on Render](#11-phase-7--deploy-user-service-on-render)
12. [Phase 8 — Deploy Activity Service on Render](#12-phase-8--deploy-activity-service-on-render)
13. [Phase 9 — Deploy AI Service on Render](#13-phase-9--deploy-ai-service-on-render)
14. [Phase 10 — Configure Android App](#14-phase-10--configure-android-app)
15. [Environment Variables Master Reference](#15-environment-variables-master-reference)
16. [Post-Deployment Checklist](#16-post-deployment-checklist)

---

## 1. Architecture & Platform Map

```
Android App
     |  HTTPS
     v
+----------------------------------+
|   API Gateway                    |  Render (free)
|   Validates JWT with Keycloak    |
+----+----------+--------+---------+
     |          |        |
+----v--+  +----v--+  +--v------+
| User  |  |Activ- |  |   AI    |  All on Render (free)
|Service|  |ity    |  | Service |
| Render|  |Render |  | Render  |
|Postgr-|  |Mongo  |  | Mongo   |
|  SQL  |  |Atlas  |  | Atlas   |
+-------+  +--+----+  +--^------+
              | Kafka    |
              +----------+
           Upstash Kafka (free)

All services register with:
+-----------------------------+
|  Eureka Server  (Render)    |
|  Config Server  (Render)    |
+-----------------------------+

Auth tokens issued by:
+-----------------------------+
|  Keycloak (Oracle Cloud VM) |
|  Always-free ARM instance   |
+-----------------------------+
```

**Platform summary:**

| Service | Platform | Cost |
|---|---|---|
| Config Server + Eureka | Render Free | $0 (sleeps, we fix this) |
| Gateway | Render Free | $0 |
| User Service | Render Free | $0 |
| Activity Service | Render Free | $0 |
| AI Service | Render Free | $0 |
| Keycloak | Oracle Cloud ARM VM | $0 forever |
| PostgreSQL | Supabase Free | $0 |
| MongoDB | Atlas M0 Free | $0 |
| Kafka | Upstash Kafka Free | $0 (10k msg/day) |
| **TOTAL** | | **$0/month** |

---

## 2. Render: Docker Hub vs Direct GitHub — Which to use?

### Your previous approach: Push Docker image to Docker Hub then connect to Render

```
You change code -> Build image locally -> docker push -> Render pulls image
```

**Pros:** Full control over the build, you already know this workflow.  
**Cons:** You must manually rebuild and push every time you change code. Gets tedious.

### Better approach for this project: Connect Render directly to GitHub

```
You push code to GitHub -> Render auto-detects Dockerfile -> Builds + deploys automatically
```

**Pros:**
- Zero manual steps after the initial setup — just `git push` and it deploys
- Render caches Docker layers so rebuilds are fast
- No Docker Hub account or local Docker needed

**How it works with this repo:** Each service folder (`gateway/`, `userservice/`, etc.)
now has a `Dockerfile`. When you connect Render to the repo and tell it the root
directory is `gateway`, it finds and uses that `Dockerfile` automatically.

**Verdict: Use direct GitHub connection.** It is strictly better for this project.

---

## 3. Railway $5 — Honest Analysis

**Conclusion: You do not need Railway at all. Render gives the same thing for free.**

| | Render Free | Railway $5/month |
|---|---|---|
| RAM | 512 MB | 512 MB |
| CPU | 0.1 vCPU | 0.1 vCPU |
| Sleep on inactivity | Yes (15 min) | No |
| Monthly cost | $0 | $5 |
| Services you can run | Unlimited (each sleeps) | ~1-2 |

Railway's only advantage is no sleep. We solve the sleep problem with a free external
cron scheduler. So Railway is not needed unless you want to pay to avoid sleep.

**What does "$5 credit" actually buy?**

Railway charges by RAM + CPU consumed per minute. One 512 MB Spring Boot service running
24/7 costs about $6/month in RAM alone. So $5 covers roughly one service — not six.

---

## 4. Keeping Render Free Tier Awake (No Sleep)

Render free tier sleeps after 15 minutes of inactivity. A `/ping` endpoint has been
added to every service in this project. Use a free external cron to hit it every 5
minutes — the service never goes to sleep.

### Setup: cron-job.org (Recommended — completely free, no limit)

1. Go to [https://cron-job.org](https://cron-job.org) and sign up free
2. After deploying all services (later phases), create one cron job per service:
   - Click **CREATE CRONJOB**
   - Title: e.g. `ping-gateway`
   - URL: `https://fitness-gateway.onrender.com/ping`
   - Schedule: Every 5 minutes (select **Every minute** then set to `*/5 * * * *`)
   - Click **CREATE**
3. Repeat for all 6 services

| Cron job title | URL to ping | Schedule |
|---|---|---|
| ping-configserver | `https://[configserver-url].onrender.com/actuator/health` | `*/5 * * * *` |
| ping-eureka | `https://[eureka-url].onrender.com/actuator/health` | `*/5 * * * *` |
| ping-gateway | `https://[gateway-url].onrender.com/ping` | `*/5 * * * *` |
| ping-userservice | `https://[userservice-url].onrender.com/ping` | `*/5 * * * *` |
| ping-activityservice | `https://[activityservice-url].onrender.com/ping` | `*/5 * * * *` |
| ping-aiservice | `https://[aiservice-url].onrender.com/ping` | `*/5 * * * *` |

cron-job.org will email you if any of these start failing.

### Alternative: UptimeRobot (also free, 50 monitors)

1. [https://uptimerobot.com](https://uptimerobot.com) -> free account
2. **Add New Monitor** -> Monitor Type: **HTTP(s)**
3. URL: your service `/ping` endpoint
4. Monitoring Interval: **5 minutes**
5. Repeat for all services

---

## 5. Phase 1 — MongoDB Atlas (Free)

### 5.1 Create Cluster

1. Go to [https://cloud.mongodb.com](https://cloud.mongodb.com) and sign up free
2. Click **Create** -> **M0 Free** -> AWS -> pick the region closest to you
3. Cluster Name: `fitness-cluster`
4. Click **Create Deployment**

### 5.2 Create Database User

1. Left menu: **Security -> Database Access** -> **Add New Database User**
2. Authentication Method: **Password**
   - Username: `fitnessapp`
   - Password: click **Autogenerate Secure Password** -> **Copy** and save it
3. Built-in Role: **Atlas admin**
4. Click **Add User**

### 5.3 Allow All IPs (Required — Render IPs change every deploy)

1. Left menu: **Security -> Network Access** -> **Add IP Address**
2. Click **Allow Access from Anywhere** button
3. This adds `0.0.0.0/0`
4. Click **Confirm**

### 5.4 Get Connection String

1. Left menu: **Deployment -> Database** -> click **Connect** on your cluster
2. **Drivers** -> Driver: **Java** -> Version: **5.1 or later**
3. Copy the connection string shown:
   ```
   mongodb+srv://fitnessapp:<password>@fitness-cluster.xxxxxxx.mongodb.net/
   ```
4. Replace `<password>` with the password you saved in step 5.2

Your two final URIs (add the database name and options at the end):
```
MONGO_ACTIVITY_URI=mongodb+srv://fitnessapp:YOURPASSWORD@fitness-cluster.xxxxx.mongodb.net/activity_db?retryWrites=true&w=majority&appName=fitness-cluster

MONGO_AI_URI=mongodb+srv://fitnessapp:YOURPASSWORD@fitness-cluster.xxxxx.mongodb.net/ai_db?retryWrites=true&w=majority&appName=fitness-cluster
```

Spring Data MongoDB creates the collections automatically on first write.

---

## 6. Phase 2 — PostgreSQL on Supabase (Free)

### 6.1 Create Project

1. Go to [https://supabase.com](https://supabase.com) and sign up with GitHub
2. Click **New project**
   - Organization: your personal org
   - Project name: `fitness-userdb`
   - Database password: set something strong, **save it**
   - Region: closest to you
3. Click **Create new project** and wait about 2 minutes

### 6.2 Get Connection Details

1. Left menu: **Project Settings -> Database**
2. Scroll down to **Connection string** section
3. Click the **JDBC** tab
4. Copy the connection string shown. It looks like:
   ```
   jdbc:postgresql://db.xxxxxxxxxxxxxxxxxxxx.supabase.co:5432/postgres
   ```

Your variables:
```
FITNESS_USER_DB_URL=jdbc:postgresql://db.xxxxxxxxxxxxxxxxxxxx.supabase.co:5432/postgres
FITNESS_USER_DB_USERNAME=postgres
FITNESS_USER_DB_PASSWORD=YOURPASSWORD
```

Supabase free tier: 500 MB database, 2 CPU, 1 GB RAM. More than enough.

---

## 7. Phase 3 — Upstash Kafka (Free, 10k msg/day)

Upstash is better than Confluent Cloud for this project:
- Free tier is **free forever** (not a 30-day trial)
- 10,000 messages/day is enough for a portfolio/personal fitness app
- SCRAM-SHA-256 authentication which the services are already configured for

### 7.1 Create Kafka Cluster

1. Go to [https://console.upstash.com](https://console.upstash.com) and sign up free
2. Left menu: **Kafka** -> **Create Cluster**
   - Name: `fitness-kafka`
   - Region: AWS -> pick closest region
   - Type: **Single Region** (this is the free tier)
3. Click **Create**

### 7.2 Create Topic

1. Click on your cluster name -> **Topics** tab -> **Create Topic**
   - Topic Name: `activity-events`
   - Partitions: `1` (free tier limit)
   - Retention Time: `1 day`
2. Click **Create**

### 7.3 Get Credentials

1. On the cluster page -> **Details** tab (or **Credentials** tab)
2. Copy:
   - **Endpoint** (this is your bootstrap server): `xxxx-xxxx.upstash.io:9092`
   - **SASL Username**
   - **SASL Password**

Your variables:
```
KAFKA_BOOTSTRAP_SERVERS=xxxx-xxxx.upstash.io:9092
KAFKA_SASL_USERNAME=your-upstash-username
KAFKA_SASL_PASSWORD=your-upstash-password
```

The config YAMLs already have `SCRAM-SHA-256` configured. No code changes needed.

---

## 8. Phase 4 — Keycloak on Oracle Cloud (Free Forever)

Oracle Cloud Always Free gives you 4 ARM cores + 24 GB RAM across VMs, completely free
with no expiry. A Keycloak instance needs about 512 MB RAM so this is plenty.

### 8.1 Create Oracle Cloud Account

1. Go to [https://cloud.oracle.com](https://cloud.oracle.com) -> **Sign Up**
2. Fill in your details. It **requires a credit card** for identity verification but
   you will never be charged as long as you only use Always Free resources
3. Choose your home region (cannot be changed — pick the closest one)
4. Complete signup (takes 5-10 minutes, email verification required)

### 8.2 Create the VM

1. Open the left menu (hamburger icon) -> **Compute -> Instances** -> **Create Instance**
2. Name: `keycloak-vm`
3. Click **Edit** next to Image and shape:
   - Image: **Ubuntu 22.04** (by Canonical)
   - Click **Change shape** -> select **Ampere** tab -> `VM.Standard.A1.Flex`
   - OCPUs: **1** | Memory: **6 GB**
   - Click **Select shape**
4. Leave networking as default (Oracle auto-creates a VCN)
5. SSH keys section:
   - Select **Generate a key pair for me**
   - Click **Save private key** -> this downloads `ssh-key-xxxxxxxx.key`
6. Click **Create**
7. Wait about 2 minutes for the state to change to **Running**
8. Copy the **Public IP address** shown — you will need this many times

### 8.3 Open the Firewall (Two steps required)

**Step A — Oracle Cloud Security List:**
1. Click on your VM instance -> click the **Subnet** link -> click **Default Security List**
2. Click **Add Ingress Rules** and add:

   | Stateless | Source CIDR | Protocol | Port Range | Description |
   |---|---|---|---|---|
   | No | `0.0.0.0/0` | TCP | `8181` | Keycloak |

3. Click **Add Ingress Rules**

**Step B — Ubuntu firewall (run after SSH in):**
```bash
sudo ufw allow 8181/tcp
sudo ufw reload
sudo ufw status
```

### 8.4 SSH into the VM

Open PowerShell on your Windows machine:

```powershell
# Fix key permissions first (required or SSH refuses the key)
icacls "C:\Users\saifs\Downloads\ssh-key-xxxxxxxx.key" /inheritance:r /grant:r "$env:USERNAME:(R)"

# Connect
ssh -i "C:\Users\saifs\Downloads\ssh-key-xxxxxxxx.key" ubuntu@YOUR_VM_PUBLIC_IP
```

### 8.5 Install Docker on the VM

Run these commands inside the VM (after SSH):

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y docker.io docker-compose curl
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker ubuntu
newgrp docker
```

Verify it works:
```bash
docker ps
# Should show empty table, no error
```

### 8.6 Run Keycloak with Docker Compose

```bash
mkdir -p ~/keycloak && cd ~/keycloak

cat > docker-compose.yml << 'COMPOSEFILE'
version: '3.8'
services:
  keycloak-db:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: keycloak
      POSTGRES_USER: keycloak
      POSTGRES_PASSWORD: keycloak_db_secret_123
    volumes:
      - keycloak_db_data:/var/lib/postgresql/data

  keycloak:
    image: quay.io/keycloak/keycloak:26.0
    restart: unless-stopped
    depends_on:
      - keycloak-db
    environment:
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://keycloak-db:5432/keycloak
      KC_DB_USERNAME: keycloak
      KC_DB_PASSWORD: keycloak_db_secret_123
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: FitnessApp@Keycloak2026!
      KC_HTTP_ENABLED: "true"
      KC_HOSTNAME_STRICT: "false"
      KC_HOSTNAME_STRICT_HTTPS: "false"
      KC_PROXY: edge
      KC_HTTP_PORT: 8080
    ports:
      - "8181:8080"
    command: start-dev

volumes:
  keycloak_db_data:
COMPOSEFILE

docker-compose up -d
```

Watch until it is ready (takes about 60 seconds):
```bash
docker-compose logs -f keycloak
# Wait until you see this line:
# "Running the server in development mode."
# Then press Ctrl+C
```

Verify Keycloak is accessible — open this in your browser:
```
http://YOUR_VM_PUBLIC_IP:8181
```

### 8.7 Configure Keycloak Realm

1. Open `http://YOUR_VM_PUBLIC_IP:8181` in your browser
2. Click **Administration Console**
3. Login: username `admin`, password `FitnessApp@Keycloak2026!`

**Create the realm:**
1. Click **Keycloak** dropdown at the top-left -> **Create realm**
2. Realm name: `fitness-app`
3. Enabled: ON
4. Click **Create**

**Create public client (used by Android app):**
1. Left menu: **Clients** -> **Create client**
2. Fill in:
   - Client type: **OpenID Connect**
   - Client ID: `fitness-app-client`
3. Click **Next**
4. Settings page:
   - Client authentication: **OFF** (this makes it a public client)
   - Authorization: OFF
   - Authentication flow: tick **Direct access grants** <- this is critical for Android login
5. Click **Next** -> **Save**
6. On the settings page that opens:
   - Valid redirect URIs: `com.saif.fitnessapp://*`
   - Web origins: `*`
7. Click **Save**

**Create admin client (used by User Service backend):**
1. Left menu: **Clients** -> **Create client**
2. Fill in:
   - Client type: **OpenID Connect**
   - Client ID: `fitness-admin-client`
3. Click **Next**
4. Settings page:
   - Client authentication: **ON** (confidential)
   - Service accounts roles: **ON**
5. Click **Next** -> **Save**
6. Click **Service account roles** tab -> **Assign role**
7. Change filter to **Filter by clients** -> search `realm-management`
8. Check **realm-admin** -> click **Assign**
9. Click **Credentials** tab -> copy the **Client secret** -> save it

**Realm Login settings:**
1. Left menu: **Realm settings** -> **Login** tab
2. Set these:
   - Edit username: **OFF**  <- this prevents the HTTP 400 bug
   - User registration: **ON**
3. Click **Save**

Your Keycloak environment variables:
```
KEYCLOAK_SERVER_URL=http://YOUR_VM_PUBLIC_IP:8181
KEYCLOAK_REALM=fitness-app
KEYCLOAK_ADMIN_CLIENT_ID=fitness-admin-client
KEYCLOAK_ADMIN_USERNAME=admin
KEYCLOAK_ADMIN_PASSWORD=FitnessApp@Keycloak2026!
KEYCLOAK_APP_CLIENT_ID=fitness-app-client
KEYCLOAK_PASSWORD_RESET_REDIRECT_URI=http://YOUR_VM_PUBLIC_IP:8181
```

---

## 9. Phase 5 — Deploy Config Server + Eureka on Render

Config Server and Eureka must be deployed first because all other services pull their
configuration from Config Server on startup.

### 9.1 Connect GitHub to Render

1. Go to [https://render.com](https://render.com) -> Sign up with GitHub
2. Click **New +** -> **Web Service**
3. On the left: **Connect a repository** -> authorize Render to access your GitHub
4. Find and select `fitness-microservices` -> click **Connect**

### 9.2 Deploy Config Server

1. Click **New +** -> **Web Service** -> select `fitness-microservices`
2. Fill in settings:
   - **Name:** `fitness-configserver`
   - **Root Directory:** `configserver`
   - **Runtime:** Docker  (Render detects the Dockerfile automatically)
   - **Instance Type:** Free
3. Add environment variables (click **Add Environment Variable**):

   | Key | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `native` |

4. Click **Create Web Service**
5. First build takes about 4-5 minutes. Watch the logs.
6. Once deployed, copy the URL shown at the top: `https://fitness-configserver.onrender.com`
7. Test it: open `https://fitness-configserver.onrender.com/user-service/default` in browser
   — you should see the user-service config as JSON

### 9.3 Deploy Eureka

1. Click **New +** -> **Web Service** -> same repo
2. Fill in settings:
   - **Name:** `fitness-eureka`
   - **Root Directory:** `eureka`
   - **Runtime:** Docker
   - **Instance Type:** Free
3. Add environment variables:

   | Key | Value |
   |---|---|
   | `SPRING_CLOUD_CONFIG_URI` | `https://fitness-configserver.onrender.com` |

4. Click **Create Web Service**
5. Copy URL: `https://fitness-eureka.onrender.com`
6. Test: open `https://fitness-eureka.onrender.com` in browser — you see the Eureka dashboard

---

## 10. Phase 6 — Deploy Gateway on Render

1. **New +** -> **Web Service** -> same repo
2. Settings:
   - **Name:** `fitness-gateway`
   - **Root Directory:** `gateway`
   - **Runtime:** Docker
   - **Instance Type:** Free
3. Environment variables:

   | Key | Value |
   |---|---|
   | `SPRING_CLOUD_CONFIG_URI` | `https://fitness-configserver.onrender.com` |
   | `EUREKA_URL` | `https://fitness-eureka.onrender.com/eureka/` |
   | `KEYCLOAK_SERVER_URL` | `http://YOUR_VM_PUBLIC_IP:8181` |

4. Click **Create Web Service**
5. Copy URL: `https://fitness-gateway.onrender.com`

This is the URL that goes in the Android app. All API requests go here.

Test: `https://fitness-gateway.onrender.com/ping` should return `{"status":"ok",...}`

---

## 11. Phase 7 — Deploy User Service on Render

### 11.1 Get Gmail App Password First

This is how you allow the app to send OTP emails from your Gmail:

1. Go to [https://myaccount.google.com](https://myaccount.google.com)
2. **Security** tab -> make sure **2-Step Verification** is turned ON
3. In the **Security** tab, scroll down to **How you sign in to Google**
4. Click **2-Step Verification** -> scroll all the way to the bottom
5. You will see **App passwords** -> click it
6. Under "Select app" choose **Mail**, under "Select device" choose **Other** -> type `FitnessApp`
7. Click **Generate**
8. Copy the 16-character password shown (like: `abcd efgh ijkl mnop`)
9. **Remove the spaces** when using it: `abcdefghijklmnop`

### 11.2 Deploy User Service

1. **New +** -> **Web Service** -> same repo
2. Settings:
   - **Name:** `fitness-userservice`
   - **Root Directory:** `userservice`
   - **Runtime:** Docker
   - **Instance Type:** Free
3. Environment variables:

   | Key | Value |
   |---|---|
   | `SPRING_CLOUD_CONFIG_URI` | `https://fitness-configserver.onrender.com` |
   | `EUREKA_URL` | `https://fitness-eureka.onrender.com/eureka/` |
   | `FITNESS_USER_DB_URL` | `jdbc:postgresql://db.xxx.supabase.co:5432/postgres` |
   | `FITNESS_USER_DB_USERNAME` | `postgres` |
   | `FITNESS_USER_DB_PASSWORD` | your-supabase-password |
   | `MAIL_USERNAME` | `yourgmail@gmail.com` |
   | `MAIL_PASSWORD` | `abcdefghijklmnop` (app password, no spaces) |
   | `KEYCLOAK_SERVER_URL` | `http://YOUR_VM_PUBLIC_IP:8181` |
   | `KEYCLOAK_REALM` | `fitness-app` |
   | `KEYCLOAK_ADMIN_CLIENT_ID` | `fitness-admin-client` |
   | `KEYCLOAK_ADMIN_USERNAME` | `admin` |
   | `KEYCLOAK_ADMIN_PASSWORD` | `FitnessApp@Keycloak2026!` |
   | `KEYCLOAK_APP_CLIENT_ID` | `fitness-app-client` |
   | `KEYCLOAK_PASSWORD_RESET_REDIRECT_URI` | `http://YOUR_VM_PUBLIC_IP:8181` |

4. Click **Create Web Service**

---

## 12. Phase 8 — Deploy Activity Service on Render

1. **New +** -> **Web Service** -> same repo
2. Settings:
   - **Name:** `fitness-activityservice`
   - **Root Directory:** `activityservice`
   - **Runtime:** Docker
   - **Instance Type:** Free
3. Environment variables:

   | Key | Value |
   |---|---|
   | `SPRING_CLOUD_CONFIG_URI` | `https://fitness-configserver.onrender.com` |
   | `EUREKA_URL` | `https://fitness-eureka.onrender.com/eureka/` |
   | `MONGO_ACTIVITY_URI` | `mongodb+srv://fitnessapp:PWD@fitness-cluster.xxx.mongodb.net/activity_db?retryWrites=true&w=majority&appName=fitness-cluster` |
   | `KAFKA_BOOTSTRAP_SERVERS` | `xxxx-xxxx.upstash.io:9092` |
   | `KAFKA_SASL_USERNAME` | your-upstash-username |
   | `KAFKA_SASL_PASSWORD` | your-upstash-password |

4. Click **Create Web Service**

---

## 13. Phase 9 — Deploy AI Service on Render

### 13.1 Get OpenRouter API Key

1. Go to [https://openrouter.ai](https://openrouter.ai) -> Sign in with Google
2. Top-right -> **Keys** -> **Create Key**
   - Name: `fitness-app`
3. Copy the key: `sk-or-v1-xxxxxxxxxxxxxxxxxxxx`
4. Many models on OpenRouter are completely free to use.

### 13.2 Deploy

1. **New +** -> **Web Service** -> same repo
2. Settings:
   - **Name:** `fitness-aiservice`
   - **Root Directory:** `aiservice`
   - **Runtime:** Docker
   - **Instance Type:** Free
3. Environment variables:

   | Key | Value |
   |---|---|
   | `SPRING_CLOUD_CONFIG_URI` | `https://fitness-configserver.onrender.com` |
   | `EUREKA_URL` | `https://fitness-eureka.onrender.com/eureka/` |
   | `MONGO_AI_URI` | `mongodb+srv://fitnessapp:PWD@fitness-cluster.xxx.mongodb.net/ai_db?retryWrites=true&w=majority&appName=fitness-cluster` |
   | `KAFKA_BOOTSTRAP_SERVERS` | `xxxx-xxxx.upstash.io:9092` |
   | `KAFKA_SASL_USERNAME` | your-upstash-username |
   | `KAFKA_SASL_PASSWORD` | your-upstash-password |
   | `OPENROUTER_API_KEY` | `sk-or-v1-xxxxxxxxxxxxxxxxxxxx` |
   | `OPENROUTER_API_URL` | `https://openrouter.ai/api/v1/chat/completions` |

4. Click **Create Web Service**

---

## 14. Phase 10 — Configure Android App

After the Gateway is deployed, update the Android app to point at production URLs.

Edit `local.properties` (this file is gitignored and never committed):
```properties
# local.properties
KEYCLOAK_EMULATOR_URL=http://YOUR_VM_PUBLIC_IP:8181
KEYCLOAK_DEVICE_URL=http://YOUR_VM_PUBLIC_IP:8181

API_EMULATOR_URL=https://fitness-gateway.onrender.com
API_DEVICE_URL=https://fitness-gateway.onrender.com

KEYCLOAK_REALM=fitness-app
KEYCLOAK_CLIENT_ID=fitness-app-client
```

Build the APK:
```powershell
cd "C:\Users\saifs\Downloads\android-app-generation\android-fitness-app"
.\gradlew assembleRelease
```

Output APK: `app/build/outputs/apk/release/app-release-unsigned.apk`

---

## 15. Environment Variables Master Reference

### Common (add to every Render service)
| Key | Value |
|---|---|
| `SPRING_CLOUD_CONFIG_URI` | `https://fitness-configserver.onrender.com` |
| `EUREKA_URL` | `https://fitness-eureka.onrender.com/eureka/` |

### User Service
| Key | Description |
|---|---|
| `FITNESS_USER_DB_URL` | Supabase JDBC URL |
| `FITNESS_USER_DB_USERNAME` | `postgres` |
| `FITNESS_USER_DB_PASSWORD` | Supabase project password |
| `MAIL_USERNAME` | Gmail address |
| `MAIL_PASSWORD` | Gmail App Password (16 chars, no spaces) |
| `KEYCLOAK_SERVER_URL` | `http://oracle-vm-ip:8181` |
| `KEYCLOAK_REALM` | `fitness-app` |
| `KEYCLOAK_ADMIN_CLIENT_ID` | `fitness-admin-client` |
| `KEYCLOAK_ADMIN_USERNAME` | `admin` |
| `KEYCLOAK_ADMIN_PASSWORD` | Keycloak admin password |
| `KEYCLOAK_APP_CLIENT_ID` | `fitness-app-client` |
| `KEYCLOAK_PASSWORD_RESET_REDIRECT_URI` | `http://oracle-vm-ip:8181` |

### Gateway
| Key | Description |
|---|---|
| `KEYCLOAK_SERVER_URL` | `http://oracle-vm-ip:8181` |

### Activity Service
| Key | Description |
|---|---|
| `MONGO_ACTIVITY_URI` | MongoDB Atlas URI with `/activity_db` database |
| `KAFKA_BOOTSTRAP_SERVERS` | Upstash bootstrap server |
| `KAFKA_SASL_USERNAME` | Upstash SASL username |
| `KAFKA_SASL_PASSWORD` | Upstash SASL password |

### AI Service
| Key | Description |
|---|---|
| `MONGO_AI_URI` | MongoDB Atlas URI with `/ai_db` database |
| `KAFKA_BOOTSTRAP_SERVERS` | Upstash bootstrap server |
| `KAFKA_SASL_USERNAME` | Upstash SASL username |
| `KAFKA_SASL_PASSWORD` | Upstash SASL password |
| `OPENROUTER_API_KEY` | `sk-or-v1-...` |
| `OPENROUTER_API_URL` | `https://openrouter.ai/api/v1/chat/completions` |

---

## 16. Post-Deployment Checklist

```
DATABASES
[ ] MongoDB Atlas cluster running
[ ] Atlas network access: 0.0.0.0/0 added
[ ] Atlas user "fitnessapp" created
[ ] Supabase project created, password saved
[ ] JDBC connection string copied

KAFKA (Upstash)
[ ] Cluster created
[ ] Topic "activity-events" created (1 partition)
[ ] Credentials (username + password) copied

KEYCLOAK (Oracle)
[ ] Oracle VM running, public IP noted
[ ] Port 8181 open in Security List AND ufw
[ ] Docker running on VM
[ ] Keycloak container running: http://VM_IP:8181 opens in browser
[ ] Realm "fitness-app" created
[ ] Client "fitness-app-client": Direct access grants ON, public
[ ] Client "fitness-admin-client": Service account, realm-admin role assigned
[ ] Realm Login settings: "Edit username" = OFF

RENDER SERVICES (deploy in this order)
[ ] Config Server deployed
[ ] Test: https://configserver-url.onrender.com/user-service/default returns JSON
[ ] Eureka deployed
[ ] Test: https://eureka-url.onrender.com shows dashboard
[ ] Gateway deployed
[ ] Test: https://gateway-url.onrender.com/ping returns {"status":"ok"}
[ ] User Service deployed
[ ] Activity Service deployed
[ ] AI Service deployed
[ ] All 5 services show as "Live" (green) in Render dashboard
[ ] Eureka dashboard shows all 4 services (gateway, user, activity, ai) registered as UP

UPTIME (cron-job.org)
[ ] Account created
[ ] 6 cron jobs created (one per service), every 5 minutes
[ ] All showing green after first execution

ANDROID
[ ] local.properties updated with Render gateway URL + Oracle VM IP
[ ] App builds without error (.\gradlew assembleRelease)
[ ] Installed on physical device
[ ] Signup -> OTP email received
[ ] Login works
[ ] Activity log -> saved + Kafka event sent
[ ] AI recommendation appears after activity
```

---

## Time Estimate (First Setup)

| Step | Time |
|---|---|
| MongoDB Atlas | 5 min |
| Supabase PostgreSQL | 5 min |
| Upstash Kafka | 5 min |
| Oracle Cloud account + VM | 20 min |
| Keycloak setup + realm config | 20 min |
| All 6 Render services | 30 min |
| cron-job.org keep-alive | 5 min |
| Android config + APK build | 5 min |
| **Total** | **~95 minutes** |

After the first setup, deploying changes is just: `git push`

---

*Stack: Spring Boot 4 · Spring Cloud 2025.1.0 · Java 21 · Keycloak 26 · Upstash Kafka · MongoDB Atlas M0 · Supabase PostgreSQL*
