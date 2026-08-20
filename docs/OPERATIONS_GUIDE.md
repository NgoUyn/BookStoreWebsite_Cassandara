# Operations & Deployment Guide - BOOKOM Platform

**Target Audience**: DevOps Engineers, System Administrators, Operations Teams

---

## 1. Local Development Setup

### 1.1 Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java JDK | 17+ | Runtime |
| Maven | 3.8+ | Build & dependency management |
| SQL Server | 2019+ | Primary database |
| Git | Latest | Version control |
| Docker | 20.10+ | (Optional) Containerization |
| Docker Compose | 1.29+ | (Optional) Multi-container setup |

### 1.2 Development Installation

**Step 1: Clone Repository**
```bash
git clone <repo-url>
cd NgPhucs2375-DA_BookStore
```

**Step 2: SQL Server Setup**

Local SQL Server instance:
```bash
# Windows (Docker)
docker run -e "ACCEPT_EULA=Y" -e "SA_PASSWORD=YourPassword123!" \
  -p 1433:1433 \
  -d mcr.microsoft.com/mssql/server:2019-latest

# Or use native installation
# Download: https://www.microsoft.com/en-us/sql-server/
```

**Step 3: Database Configuration**

Edit `src/main/resources/application.properties`:
```properties
# Database
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=BookStoreDB
spring.datasource.username=sa
spring.datasource.password=YourPassword123!
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver

# Flyway migrations (auto-run on startup)
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# JPA/Hibernate
spring.jpa.database-platform=org.hibernate.dialect.SQLServer2012Dialect
spring.jpa.hibernate.ddl-auto=validate  # DO NOT USE 'create' in production
```

**Step 4: Build & Run**

```bash
# Clean build
mvn clean package

# Run with Maven Spring Boot plugin
mvn spring-boot:run

# Or run JAR directly
java -jar target/bookstore-1.0.jar
```

**Step 5: Verify Installation**

```bash
# Application should be running on http://localhost:8080

# Test endpoints
curl http://localhost:8080/api/health
curl http://localhost:8080/api/books

# Access Swagger UI
open http://localhost:8080/swagger-ui.html

# Access web interface
open http://localhost:8080
```

### 1.3 Initial Data Setup

**Option A: Automatic Seeding (Recommended)**

Access seeding endpoint:
```bash
curl -X POST http://localhost:8080/api/seed \
  -H "Content-Type: application/json" \
  -d '{
    "seedBooks": true,
    "bookCount": 100,
    "seedUsers": true,
    "userCount": 20,
    "seedCategories": true,
    "enrichWithAI": false
  }'
```

**Option B: Manual Database Script**

Execute migrations manually:
```bash
# Migrations run automatically on startup
# Check target database for tables: users, books, categories, etc.

# Verify migration status
SELECT * FROM flyway_schema_history;
```

### 1.4 Environment Configuration

**Development Mode** (`application-dev.properties`):
```properties
server.port=8080
logging.level.root=INFO
logging.level.com.example.bookstore=DEBUG
spring.devtools.restart.enabled=true

# SMTP (development - mock mode)
spring.mail.host=localhost
spring.mail.port=1025  # MailHog test mail server

# Recommendation
recommendation.refresh-rate-ms=60000  # 1 minute for testing
recommendation.min-support=0.01
recommendation.min-confidence=0.1

# Security (dev defaults)
jwt.secret=dev-secret-key-change-in-production
jwt.expiration=86400000  # 24 hours
```

**Test Mode** (`application-test.properties`):
```properties
server.port=8080
# H2 in-memory database for tests
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
```

---

## 2. Docker & Containerization

### 2.1 Build Docker Image

```dockerfile
# Dockerfile
FROM openjdk:17-slim as builder
WORKDIR /app
COPY . .
RUN apt-get update && apt-get install -y maven
RUN mvn clean package -DskipTests

FROM openjdk:17-slim
WORKDIR /app
COPY --from=builder /app/target/bookstore-*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1
CMD ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
```

Build image:
```bash
docker build -t bookstore:1.0 .
```

### 2.2 Docker Compose (Complete Stack)

`docker-compose.yml`:
```yaml
version: '3.8'

services:
  sql-server:
    image: mcr.microsoft.com/mssql/server:2019-latest
    environment:
      SA_PASSWORD: "BookStore@2024"
      ACCEPT_EULA: "Y"
    ports:
      - "1433:1433"
    volumes:
      - sqlserver-data:/var/opt/mssql
    healthcheck:
      test: /opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P "BookStore@2024" -Q "SELECT 1"
      interval: 10s
      timeout: 3s
      retries: 3

  bookstore-app:
    build: .
    depends_on:
      sql-server:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: "jdbc:sqlserver://sql-server:1433;databaseName=BookStoreDB"
      SPRING_DATASOURCE_USERNAME: "sa"
      SPRING_DATASOURCE_PASSWORD: "BookStore@2024"
      SPRING_PROFILES_ACTIVE: "prod"
      JWT_SECRET: "change-this-in-production"
    ports:
      - "8080:8080"
    healthcheck:
      test: curl -f http://localhost:8080/api/health || exit 1
      interval: 10s
      timeout: 3s
      retries: 3

  nginx:
    image: nginx:latest
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - bookstore-app

volumes:
  sqlserver-data:
```

Start stack:
```bash
docker-compose up -d
```

---

## 3. Database Management

### 3.1 SQL Server Setup

**Create Database**:
```sql
CREATE DATABASE BookStoreDB
GO

USE BookStoreDB
GO

-- Create login
CREATE LOGIN sa_bookstore WITH PASSWORD = 'BookStore@2024'
GO

-- Create user and assign permissions
CREATE USER sa_bookstore FOR LOGIN sa_bookstore
GO

ALTER ROLE db_owner ADD MEMBER sa_bookstore
GO
```

**Backup Database**:
```sql
BACKUP DATABASE BookStoreDB 
TO DISK = '/var/opt/mssql/backup/bookstore_backup.bak'
WITH FORMAT, INIT, STATS=10
GO
```

**Restore Database**:
```sql
RESTORE DATABASE BookStoreDB 
FROM DISK = '/var/opt/mssql/backup/bookstore_backup.bak'
WITH REPLACE
GO
```

### 3.2 Flyway Migrations

**Understanding Migration Versions**:

| Version | File Name | Purpose |
|---------|-----------|---------|
| V1 | `V1__init_multivendor_schema.sql` | Initial schema |
| V2 | `V2__create_seller_shop.sql` | Seller marketplace |
| V3-V5 | `V3-V5__align_enum_columns_to_nvarchar.sql` | Enum standardization |
| V6-V7 | `V6-V7__create_notifications.sql` | Real-time notifications |
| V8 | `V8__create_distributed_lock_table.sql` | Queue coordination |
| V9-V11 | `V9-V11__create_sp_*.sql` | Stored procedures for locks |
| V12-V14 | Various | Wishlist, profiles, schema cleanup |
| V15 | `V15__create_association_rules_table.sql` | Association rules (NEW) |

**Manual Migration Check**:
```sql
-- View migration history
USE BookStoreDB
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC

-- Verify all tables exist
SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE='BASE TABLE'
```

**Repair Corrupted Migrations**:
```sql
-- If migration fails and needs reset (DEVELOPMENT ONLY!)
DELETE FROM flyway_schema_history WHERE success = 0
GO
```

### 3.3 Connection Pooling

**Production Connection Pool** (`application.properties`):
```properties
# HikariCP pooling (Spring Boot default)
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

---

## 4. Monitoring & Health Checks

### 4.1 Health Check Endpoints

| Endpoint | Purpose | K8s Probe |
|----------|---------|-----------|
| `GET /api/health` | General health | Startup |
| `GET /api/health/live` | Liveness probe | Liveness |
| `GET /api/health/ready` | Readiness probe | Readiness |
| `GET /api/health/detailed` | Full system info | Manual |
| `GET /api/health/queue-worker` | Background job status | Liveness |
| `GET /api/health/sse` | SSE connection pool | Manual |

**Example Health Response**:
```json
{
  "status": "UP",
  "database": {
    "status": "UP",
    "database": "SQL Server",
    "hello": 1
  },
  "diskSpace": {
    "status": "UP",
    "total": 107374182400,
    "free": 85865816064,
    "threshold": 10485760
  },
  "livenessState": "CORRECT",
  "readinessState": "ACCEPTING_TRAFFIC"
}
```

### 4.2 Kubernetes Deployment

**Deployment Manifest** (`k8s-deployment.yaml`):
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: bookstore-app
  namespace: default
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: bookstore
  template:
    metadata:
      labels:
        app: bookstore
    spec:
      containers:
      - name: bookstore
        image: bookstore:1.0
        ports:
        - containerPort: 8080
        
        # Liveness Probe (restart if dead)
        livenessProbe:
          httpGet:
            path: /api/health/live
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 3
        
        # Readiness Probe (traffic only if ready)
        readinessProbe:
          httpGet:
            path: /api/health/ready
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 2
        
        # Resource limits
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        
        env:
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            configMapKeyRef:
              name: bookstore-config
              key: db-url
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: bookstore-secrets
              key: jwt-secret
        
        # Graceful shutdown
        lifecycle:
          preStop:
            exec:
              command: ["/bin/sh", "-c", "sleep 15"]

---
apiVersion: v1
kind: Service
metadata:
  name: bookstore-svc
spec:
  selector:
    app: bookstore
  ports:
  - port: 8080
    targetPort: 8080
  type: LoadBalancer
```

Deploy:
```bash
kubectl apply -f k8s-deployment.yaml
```

### 4.3 Monitoring Stack (ELK)

**Elasticsearch** (log storage):
```yaml
# docker-compose.yml addition
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:7.14.0
  environment:
    - discovery.type=single-node
    - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
  ports:
    - "9200:9200"
```

**Logstash** (log pipeline):
```yaml
logstash:
  image: docker.elastic.co/logstash/logstash:7.14.0
  volumes:
    - ./logstash.conf:/usr/share/logstash/pipeline/logstash.conf:ro
  ports:
    - "5000:5000"
  depends_on:
    - elasticsearch
```

**Kibana** (log visualization):
```yaml
kibana:
  image: docker.elastic.co/kibana/kibana:7.14.0
  ports:
    - "5601:5601"
  depends_on:
    - elasticsearch
```

---

## 5. Deployment Checklist

### Pre-Deployment

- [ ] Code reviewed and merged to `main` branch
- [ ] All tests passing (`mvn test`)
- [ ] No SNAPSHOT dependencies (unless intentional)
- [ ] Security scan completed (no CVEs)
- [ ] Security regression suite passed (`JwtTokenProviderTest`, `CustomPermissionEvaluatorTest`, controller auth tests)
- [ ] No legacy auth headers or request attributes remain (`X-User-Id`, `CURRENT_*`)
- [ ] Database migrations tested locally
- [ ] Environment variables documented
- [ ] Secrets managed (not in code)
- [ ] Backup created of current production database
- [ ] Rollback plan documented

### Build & Package

- [ ] Build JAR: `mvn clean package`
- [ ] Verify JAR size reasonable (~50-100MB)
- [ ] Build Docker image: `docker build -t bookstore:v1.0 .`
- [ ] Test image locally: `docker run -p 8080:8080 bookstore:v1.0`
- [ ] Push to registry: `docker push registry.example.com/bookstore:v1.0`

### 5.1 Security Rollout Notes

- Verify JWT payloads include `userId`, `roles`, and `sellerId` before rolling out auth-related changes.
- Deploy controller and security filter changes together so route checks and principal extraction stay aligned.
- Watch for spikes in `401` and `403` responses after deployment; they usually indicate missing claims or a stale token.
- Seller approval is admin-controlled; keep audit logging and approval workflow monitoring enabled.

### 5.2 Audit Logging

- Log authentication failures at the entry point and authorization denials at the controller/service boundary.
- Include `userId`, `roles`, request path, and decision outcome in security audit events.
- Retain audit records long enough to investigate IDOR or privilege-escalation reports.

### Database

- [ ] Backup existing database
- [ ] Run Flyway migrations: `mvn flyway:migrate`
- [ ] Verify schema: Check `flyway_schema_history` table
- [ ] Test critical queries post-migration
- [ ] Verify indices exist and statistics updated

### Deployment

**For Single Instance**:
```bash
# Stop current app
systemctl stop bookstore

# Backup current JAR
cp /opt/bookstore/app.jar /opt/bookstore/app.jar.bak

# Deploy new JAR
cp target/bookstore-1.0.jar /opt/bookstore/app.jar

# Start app
systemctl start bookstore

# Monitor logs
tail -f /var/log/bookstore/app.log
```

**For Kubernetes**:
```bash
# Update deployment
kubectl set image deployment/bookstore-app \
  bookstore=registry.example.com/bookstore:v1.0 \
  --record

# Monitor rollout
kubectl rollout status deployment/bookstore-app

# If rollback needed
kubectl rollout undo deployment/bookstore-app
```

### Post-Deployment

- [ ] Health check all endpoints: `curl http://localhost:8080/api/health`
- [ ] Verify database connectivity
- [ ] Check background jobs running (recommendations, notifications)
- [ ] Smoke test critical flows:
  - [ ] User registration
  - [ ] Book search
  - [ ] Checkout process
  - [ ] Seller dashboard
  - [ ] Real-time notifications
- [ ] Monitor error logs for exceptions
- [ ] Performance baseline established
- [ ] Alert rules configured

---

## 6. Troubleshooting

### Database Connection Issues

**Problem**: `Cannot connect to database`

**Solutions**:
```bash
# 1. Verify SQL Server is running
docker ps | grep mssql

# 2. Test connectivity
sqlcmd -S localhost,1433 -U sa -P "YourPassword123!"

# 3. Check connection string in application.properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=BookStoreDB

# 4. Verify network (in Docker)
docker network inspect bridge
```

### Migration Failures

**Problem**: `Flyway migration failed at V15`

**Solutions**:
```bash
# 1. Check migration history
SELECT * FROM flyway_schema_history WHERE success=0

# 2. Identify error
SELECT * FROM flyway_schema_history WHERE version='15'

# 3. Fix manual migration, then repair
DELETE FROM flyway_schema_history WHERE version='15'
mvn flyway:migrate

# 4. Or rollback entire DB and restart
# (development only!)
DROP TABLE association_rules
```

### Background Job Not Running

**Problem**: `Recommendations not updated`

**Solutions**:
```bash
# 1. Check job logs
tail -f logs/bookstore.log | grep RecommendationJob

# 2. Verify job scheduling enabled
# In application.properties
spring.task.scheduling.enabled=true

# 3. Check distributed lock
SELECT * FROM distributed_locks

# 4. Manual trigger (if available)
curl http://localhost:8080/api/admin/recommendation/recompute
```

### SSE Connection Drops

**Problem**: `Real-time notifications stop arriving`

**Solutions**:
```bash
# 1. Check SSE endpoint
curl -N http://localhost:8080/api/notifications/me/subscribe

# 2. Verify notification queue worker is running
curl http://localhost:8080/api/health/sse

# 3. Check notification delivery logs
SELECT * FROM notification_delivery WHERE status='FAILED' ORDER BY created_at DESC

# 4. Restart application (temporary fix)
systemctl restart bookstore
```

---

## 7. Performance Tuning

### Database Query Optimization

```sql
-- Add missing indices
CREATE NONCLUSTERED INDEX IX_Books_ApprovalStatus 
ON books(approval_status) INCLUDE (id, title, price)

CREATE NONCLUSTERED INDEX IX_Orders_BuyerId 
ON orders(buyer_id, created_at DESC)

-- Check index usage
SELECT * FROM sys.dm_db_index_usage_stats 
WHERE database_id = DB_ID() 
ORDER BY user_updates DESC
```

### Application Tuning

```properties
# Increase thread pool
spring.task.execution.pool.core-size=10
spring.task.execution.pool.max-size=20

# Increase connection pool
spring.datasource.hikari.maximum-pool-size=30

# Cache recommendations longer
recommendation.refresh-rate-ms=7200000  # 2 hours

# Batch notification processing
notification.batch-size=100
```

### JVM Tuning

```bash
# Increase heap size
java -Xmx2g -Xms1g -jar app.jar

# Enable GC logging
java -Xmx2g -Xlog:gc*:file=gc.log -jar app.jar
```

---

## 8. Backup & Disaster Recovery

### Database Backup Strategy

**Daily Backup (Automated)**:
```sql
-- SQL Agent job or cron
BACKUP DATABASE BookStoreDB 
TO DISK = '/backups/bookstore_daily.bak'
WITH FORMAT, INIT, STATS=10, NAME = 'Full Backup'

-- Keep 30 days
DELETE FROM backups WHERE backup_date < GETDATE()-30
```

**Backup Verification**:
```sql
-- Restore to test database monthly
RESTORE DATABASE BookStoreDB_Test 
FROM DISK = '/backups/bookstore_daily.bak'
WITH REPLACE
```

### Disaster Recovery Plan

| Scenario | RTO | RPO | Action |
|----------|-----|-----|--------|
| Single pod crash | 1 min | 0 min | K8s auto-restart |
| Node failure | 5 min | 0 min | K8s reschedule |
| Database corruption | 1 hour | 1 day | Restore from backup |
| Complete data loss | 2 hours | 1 day | Restore + reseed |
| Regional outage | 4 hours | 1 day | Failover to DR site |

---

## 9. Production Checklist (Final)

- [ ] Environment variables secured (not in code)
- [ ] JWT secret changed (not 'dev-secret-key')
- [ ] Database backups automated and tested
- [ ] Monitoring/alerts configured
- [ ] Logs centralized
- [ ] SSL/TLS certificates valid
- [ ] CORS properly restricted (not `*`)
- [ ] Rate limiting enabled
- [ ] DDoS protection in place
- [ ] Incident response plan documented

---

**Last Updated**: May 16, 2026  
**Next Review**: August 2026  
**Owner**: DevOps / Infrastructure Team

---

## Recent Merge: d769cfe (2026-05-17) - JWT Principal & Security Updates

Summary: The `Mar1cc` branch was merged to standardize how the application extracts the authenticated principal from JWT tokens and to update security-related controllers. Apply the following rollout checklist when deploying these changes.

Deployment Checklist (merge-specific):
- [ ] Ensure JWT tokens issued by auth services include `userId`, `roles`, and `sellerId` claims.
- [ ] Deploy `JwtAuthenticationFilter` and `SecurityConfig` changes together with application code to avoid mismatches.
- [ ] Run smoke tests for authentication and admin flows:
  - `GET /api/auth/me` should return `userId` and `roles`.
  - Admin endpoints (`/api/admin/**`) should still enforce `@PreAuthorize` controls.
  - Sample flow: login -> call admin approve endpoint -> expect 200 for admin token.
- [ ] Monitor for `401`/`403` spikes for 30 minutes after rollout.
- [ ] Run the security regression suite: `JwtTokenProviderTest`, `CustomPermissionEvaluatorTest`, controller auth tests.

Notes:
- If external clients use tokens without the new claims, prepare a compatibility plan (token exchange or phased rollout).
