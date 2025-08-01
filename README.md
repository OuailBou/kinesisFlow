# KinesisFlow: Real-Time Market Alerting Platform

![CI/CD Pipeline Status](https://github.com/OuailBou/kinesisFlow/actions/workflows/ci-cd.yml/badge.svg)

KinesisFlow is a high-performance, event-driven platform designed to ingest real-time market data, process it against user-defined alerts, and deliver instant notifications. The system is built on a modern, cloud-native architecture, demonstrating principles of scalability, resilience, and observability.

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
| **Testing**       | JUnit 5, Mockito, **Testcontainers** (Integration Testing), **k6** (Load Testing), SonarQube, Postman                 |
| **Resilience**    | Kafka DLQ                                                                                                             |
| **Observability** | **Micrometer**, **Prometheus**, **Grafana**                                                                           |

---

### 🚀 Performance & Scalability

Extensive load testing was performed using **k6** to validate the system's performance and horizontal scalability. The tests simulated a complex workload of concurrent data ingestion and user activity.

#### Single vs. Multi-Instance Benchmark

| Metric                     | Result (1 Fargate Instance) | Result (2 Fargate Instances) | Change     |
| -------------------------- | --------------------------- | ---------------------------- | ---------- |
| **Max Consumer Throughput**| ~222 events/sec             | **~530 events/sec**          | **+138%**  |
| **API Latency (p95)**      | ~95 ms                      | **~81 ms**                   | **-15%**   |
| **API Error Rate**         | 0.01%                       | **0.01%**                    | Negligible |

![Grafana Dashboard Screenshot](https://github.com/user-attachments/assets/a3454d94-8672-4c66-9765-e64468d82f34)  
*Real-time performance dashboard in Grafana during a load test with 2 active instances.*

**Conclusions:**
*   **High Efficiency:** Even with one instance, the system handled a high volume of events with internal latency under 10ms.
*   **Proven Horizontal Scalability:** Doubling the compute resources resulted in a **2.38x increase in processing capacity** (with double the load), proving the effectiveness of the stateless, event-driven architecture.

---

### 🧪 Code Quality & Testing Strategy

This project emphasizes a high standard of code quality and reliability, validated through a comprehensive testing strategy. The goal is not just to write code that works, but to write code that is robust, maintainable, and well-understood.

#### ✅ Test Coverage Report (JaCoCo)

![Test Coverage Report](https://github.com/user-attachments/assets/2c50159b-565a-4961-bdaa-993c88029d90)


| Metric            | Overall Coverage | Key Takeaways & Improvement Areas                                                                 |
|-------------------|------------------|----------------------------------------------------------------------------------------------------|
| **Line Coverage**   | 83%              | Demonstrates a strong foundation of testing across all major components, meeting industry standards. |
| **Branch Coverage** | 56%              | Key focus for future improvement. Current suite covers the "happy path" well; aim to increase edge-case coverage. |

#### 🧠 Testing Philosophy

- **Unit & Integration Tests**:  
  Includes fast unit tests (using **Mockito**) for business logic and full integration tests (using **Testcontainers**) to validate flows through **Kafka**, **Redis**, and **PostgreSQL**.

- **Continuous Integration**:  
  Every push to a pull request triggers a **GitHub Actions** workflow running the entire test suite, ensuring no regressions enter the main branch.

- **Static Analysis**:  
  The codebase is continuously monitored with **SonarQube** for code smells, bugs, and security vulnerabilities, enforcing high-quality standards.

#### 🔧 Next Steps for Improvement

- Increase **branch coverage** in the **service** and **websocket** layers by testing failure scenarios.
- Add dedicated **unit tests** for model entities to ensure their internal business logic is verified.

---

### 📖 API Documentation

The full REST API documentation is generated via OpenAPI 3 and is accessible through a live Swagger UI.

*   **Live Swagger UI:** `http://localhost:8080/swagger-ui/index.html#/`

---

### ⚙️ Running Locally

**Prerequisites:**
*   Docker & Docker Compose

**Steps:**
1.  Clone the repository:
    ```bash
    git clone https://github.com/OuailBou/kinesisflow.git
    cd kinesisflow/infrastructure
    ```
2.  Start the local infrastructure (Kafka, Redis, PostgreSQL):
    ```bash
    docker-compose up -d
    ```

The application will be available at `http://localhost:8080`.
