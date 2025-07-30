# KinesisFlow: Real-Time Market Alerting Platform

![CI/CD Pipeline Status](https://github.com/OuailBou/kinesisFlow/actions/workflows/ci-cd.yml/badge.svg)

KinesisFlow is a high-performance, event-driven platform designed to ingest real-time market data, process it against user-defined alerts, and deliver instant notifications. The system is built on a modern, cloud-native architecture, demonstrating principles of scalability, resilience, and observability.

---

### 🎥 Live Demo

[]

*A brief video showcasing the system in action: creating an alert via the API, ingesting data with a load test, and receiving a real-time notification on a WebSocket client.*

---

### 🏛️ Architecture Overview

The system is designed as a modular, event-driven monolith, ready to be evolved into a full microservices architecture. It separates the **write-intensive path** (alert management) from the **read-intensive, real-time path** (event processing).

![KinesisFlow AWS Architecture Diagram](https://github.com/user-attachments/assets/dad66fdd-cbb5-4e0c-bbf9-5e43f448249c)


#### Real-Time Alerting Pipeline (The "Hot Path")
1.  The **Ingest Service** receives market data via a load-balanced REST API endpoint.
2.  Events are produced into an **Apache Kafka** topic, acting as a durable, scalable event bus.
3.  The **Alert Engine**, a Kafka consumer, processes events in real-time. It queries a low-latency **Redis (ElastiCache)** cache to find matching alert rules based on a "threshold crossing" algorithm.
4.  When an alert is triggered, a notification event is published to a **Redis Pub/Sub** channel.
5.  The **WebSocket Handler** service, subscribed to the Pub/Sub channel, receives the notification and pushes it to the appropriate user over a persistent WebSocket connection.

#### Alert Management Pipeline (The "Cold Path")
1.  A user (Actor) sends HTTP requests to an **Application Load Balancer**.
2.  The **Alert Service** handles the CRUD operations for alerts.
3.  **PostgreSQL (RDS)** is used as the persistent source of truth for all user and alert data.
4.  To ensure consistency between the database and the cache, the service publishes **Domain Events** after each successful database transaction.
5.  A transactional **Listener** consumes these events and asynchronously updates the **ElastiCache (Redis)** indexes, ensuring the cache is a consistent, high-performance projection of the primary database.

---

### 🛠️ Tech Stack & Key Concepts

| Category          | Technologies & Concepts                                                                                               |
| ----------------- | --------------------------------------------------------------------------------------------------------------------- |
| **Backend**       | Java 21, Spring Boot 3, Spring Security (JWT)                                                                         |
| **Messaging**     | Apache Kafka (Event Bus), Redis Pub/Sub (Real-time Notifications)                                                     |
| **Data Tier**     | PostgreSQL (Persistent Storage), Redis (High-Performance Caching & State)                                             |
| **Architecture**  | Modular Monolith, Event-Driven, Cloud-Native, REST API, WebSockets                                                    |
| **DevOps & Cloud**| Docker, **AWS** (ECS Fargate, EC2, RDS, ElastiCache, ALB), CI/CD with **GitHub Actions**,                             |
| **Testing**       | JUnit 5, Mockito, **Testcontainers** (Integration Testing), **k6** (Load Testing)                                     |
| **Resilience**    | Kafka DLQ                                                                                                             |
| **Observability** | **Micrometer**, **Prometheus**, **Grafana**                                                                           |

---

### 🚀 Performance & Scalability

Extensive load testing was performed using **k6** to validate the system's performance and horizontal scalability. The tests simulated a complex workload of concurrent data ingestion and user activity.

#### Single vs. Multi-Instance Benchmark

| Metric                     | Result (1 Fargate Instance) | Result (2 Fargate Instances) | Change     |
| -------------------------- | --------------------------- | ---------------------------- | ---------- |
| **Max Consumer Throughput**| ~222 events/sec             | **~530 events/sec**          | **+138%**  |
| **API Latency (p95)**        | ~95 ms                    | **~81 ms**                   | **-15%**   |
| **API Error Rate**         | 0.01%                       | **0.01%**                    | Negligible |

![Grafana Dashboard Screenshot](https://github.com/user-attachments/assets/a3454d94-8672-4c66-9765-e64468d82f34)
*Real-time performance dashboard in Grafana during a load test with 2 active instances.*

**Conclusions:**
*   **High Efficiency:** A single instance demonstrated a very high processing throughput with sub-10ms internal latency.
*   **Proven Horizontal Scalability:** Doubling the compute resources resulted in a **2.38x increase in processing capacity**, proving the effectiveness of the stateless, event-driven architecture.
*   **Bottleneck Analysis:** The tests identified the single Kafka broker as the next bottleneck, validating the production architecture roadmap which includes migrating to a managed cluster like Amazon MSK.

---

### 📖 API Documentation

The full REST API documentation is generated via OpenAPI 3 and is accessible through a live Swagger UI.

*   **Live Swagger UI:** ``

---

### ⚙️ Running Locally

**Prerequisites:**
*   Java 17+
*   Maven 3.8+
*   Docker & Docker Compose

**Steps:**
1.  Clone the repository:
    ```bash
    git clone https://github.com/your-username/kinesisflow.git
    cd kinesisflow
    ```
2.  Start the local infrastructure (Kafka, Redis, PostgreSQL):
    ```bash
    docker-compose up -d
    ```
3.  Run the Spring Boot application:
    ```bash
    mvn spring-boot:run
    ```
The application will be available at `http://localhost:8080`.
