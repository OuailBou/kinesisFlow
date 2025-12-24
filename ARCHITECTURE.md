# KinesisFlow: Architectural Decision Records (ADRs)

This document summarizes the key architectural decisions I made while developing **KinesisFlow**. My goal here is to explain the reasoning behind the technologies and patterns I chose, and the context in which those decisions were made.

---

## ADR-001: Choice of Event Bus – Apache Kafka

**Context:**  
I needed a way for the data ingestion layer to communicate asynchronously with the real-time processing engine. This was crucial to decouple services, handle back-pressure, and avoid data loss. I considered using something like RabbitMQ or even basic REST calls, but they didn’t really fit the kind of stream processing I had in mind.

**Decision:**  
I went with **Apache Kafka** as the main event bus for the real-time pipeline.

**Justification:**
- **Event-first mindset:** KinesisFlow is built around events, not tasks. Kafka treats data as a stream of immutable, replayable events — exactly what I needed.
- **Multiple consumers:** Kafka topics can be consumed by multiple independent consumers at the same time. This means I can use the same market data for different things — for example, running ML models, updating real-time dashboards, or writing to long-term storage — without interfering with the core alerting flow. That flexibility is super useful.

- **Durability & fault-tolerance:** Kafka writes to disk, so if something like the `Alert Engine` goes down, it won’t lose any events. They’re still sitting in Kafka, ready to be processed.
- **Scalability:** Kafka is designed to handle large-scale data. I used key-based partitioning (by asset symbol), which lets me scale horizontally while keeping event order per asset.
- **Ecosystem & future-proofing:** Kafka is a widely adopted standard. If I ever need to upgrade the stream processing side, tools like Kafka Streams or Apache Flink are ready to plug in.

---

## ADR-002: Caching Strategy – Redis for Low-Latency Alert Checking

**Context:**  
The `Alert Engine` has to check every market event against potentially millions of user alerts. Querying the main PostgreSQL database on every event would’ve been way too slow and expensive.

**Decision:**  
I implemented a **Cache-Aside** pattern using **Redis (via AWS ElastiCache)** to store active alert rules in memory.

**Justification:**
- **Speed:** Redis reads are incredibly fast (sub-millisecond), which is perfect for real-time workflows.
- **Smart data structures:** I used **Sorted Sets** in Redis to store alerts by price threshold (as the score). This lets me fetch all alerts in a price range using `ZRANGEBYSCORE`, which is efficient.
- **Reduced DB load:** Redis takes care of most reads, so PostgreSQL is only used for writes and critical operations. This keeps the database happy and responsive.

---

## ADR-003: DB-Cache Consistency – Domain Events Pattern

**Context:**  
Keeping the cache in sync with the database is always tricky. I didn’t want to rely on writing to both the DB and Redis at the same time, since that can easily get out of sync if one write fails.

**Decision:**  
I used a **Domain Events** pattern. After a successful DB transaction, I publish an event, and a separate listener updates Redis.

**Justification:**
- **Consistency:** I used Spring’s `@TransactionalEventListener(phase = AFTER_COMMIT)` so the cache is only updated **after** the DB write is confirmed.
- **Separation of concerns:** The `AlertService` handles DB logic only. A dedicated `CacheSyncListener` handles Redis updates. Cleaner and easier to maintain.
- **Resilience:** The listener runs asynchronously (`@Async`) and retries on failure (`@Retryable`). If Redis is down, it doesn’t block the main app, and the cache update is retried later.

---

## ADR-004: Deployment Strategy – Scalable Monolith & Cloud Services

**Context:**  
I needed a deployment setup that works for a Minimum Viable Product (MVP), but could also scale if needed. I didn’t want to over-engineer the infrastructure at this stage.

**Decision:**  
I used a **hybrid cloud setup** on **AWS**. The core app is a **modular monolith** running in **Fargate**, and all stateful components (like DB and cache) use managed services.

**Justification:**
- **Monolith with scaling in mind:** Starting with a stateless monolith was faster for development. Since it's modular and talks via Kafka and Domain Events, I can scale it horizontally. Load testing showed a 2.38x throughput boost going from 1 to 2 instances.
- **Managed services for state:** PostgreSQL runs on **RDS**, and Redis on **ElastiCache**. This lets AWS handle availability, backups, and failover.
- **Kafka: aware of limitations:** For now, Kafka is on a single EC2 instance (yes, it’s a **Single Point of Failure**). But I’m aware of the trade-off. In a real production setup, I’d move to **Amazon MSK** with a replication factor of 3. I made this choice consciously to stay within the project’s budget and scope.

---

📌 *Note: These ADRs reflect the current MVP version. I plan to revisit some of these decisions (especially around Kafka deployment) before moving to production.*

