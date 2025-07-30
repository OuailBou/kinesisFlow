# KinesisFlow: Architectural Decision Records (ADRs)

This document records the key architectural decisions made during the development of the KinesisFlow project. The goal is to provide context and justification for the chosen design patterns and technologies.

---

### ADR-001: Choice of Event Bus - Apache Kafka

**Context:**
The system requires a mechanism for asynchronous communication between the data ingestion layer and the real-time processing engine. This is critical for decoupling services, handling back-pressure, and ensuring the durability of incoming market data. Alternatives considered were a traditional message broker like RabbitMQ or direct REST API calls.

**Decision:**
We chose **Apache Kafka** as the central event bus for the real-time data pipeline.

**Justification:**
1.  **Event-Driven Paradigm:** KinesisFlow is fundamentally an event streaming platform, not a task-based system. Kafka's log-centric model, where events are treated as an immutable, replayable stream of facts, is a perfect fit for this paradigm.
2.  **Durability and Resilience:** Kafka's persistence to disk provides a strong durability guarantee. If a downstream consumer (like the `Alert Engine`) fails, the market data is safely stored in Kafka and can be processed upon recovery, preventing data loss.
3.  **High Throughput & Scalability:** Kafka is designed for high-volume data streams. The use of **key-based partitioning** (using the asset symbol as the key) allows the system to process events for different assets in parallel across multiple consumer instances, while still guaranteeing in-order processing for any single asset. This is the foundation of the system's horizontal scalability.
4.  **Ecosystem:** Kafka is the industry standard for event streaming and provides the foundation for future integration with advanced stream processing frameworks like Kafka Streams or Apache Flink.

---

### ADR-002: Caching Strategy - Redis for Low-Latency Alert Checking

**Context:**
The `Alert Engine` needs to check each incoming market event against potentially millions of user-defined alert rules. Querying the primary PostgreSQL database for every event would introduce significant latency and create a massive performance bottleneck.

**Decision:**
We implemented a **Cache-Aside** pattern using **Redis (Amazon ElastiCache)** as a low-latency, in-memory cache for active alert rules.

**Justification:**
1.  **Performance:** Redis, as an in-memory datastore, provides sub-millisecond latency for read operations, which is critical for the real-time "hot path" of the `Alerting Engine`.
2.  **Optimized Data Structures:** We leveraged Redis's advanced data structures for maximum efficiency. **Sorted Sets** are used to index alert rules by their price threshold (`score`). This allows the engine to query for all triggered alerts within a price range in a single, highly efficient `ZRANGEBYSCORE` operation, with a time complexity of O(log(N)+M).
3.  **Database Protection:** The cache acts as a shield for the primary PostgreSQL database, absorbing the vast majority of the read load and reserving the database for its primary purpose: transactional writes and ensuring data integrity.

---

### ADR-003: Database-Cache Consistency Strategy - Domain Events

**Context:**
Using a cache introduces the classic problem of keeping the cache synchronized with the source of truth (PostgreSQL). A simple "dual write" approach (writing to both PostgreSQL and Redis from the service layer) is prone to race conditions and inconsistencies if one of the writes fails.

**Decision:**
We implemented a **Domain Events** pattern to ensure eventual consistency. The `AlertService` publishes events after a successful database transaction, and a separate, decoupled listener is responsible for updating the Redis cache.

**Justification:**
1.  **Guaranteed Consistency:** By using Spring's `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`, we ensure that the cache update logic is **only triggered if the primary database transaction succeeds**. This eliminates the risk of having data in the cache that was never successfully committed to the source of truth.
2.  **Decoupling and Separation of Concerns:** The `AlertService` is now only responsible for business logic and database persistence. It has no knowledge of the caching implementation. The `CacheSyncListener` is solely responsible for the cache. This makes the system cleaner, more modular, and easier to maintain and extend.
3.  **Resilience:** The listener is configured with an asynchronous, retryable policy (`@Async`, `@Retryable`). If Redis is temporarily unavailable, the system will re-attempt to synchronize the cache without blocking the main application thread or failing the user's original request.

---

### ADR-004: Deployment Strategy - Scalable Monolith & Cloud-Native Services

**Context:**
The project needs a deployment strategy that is both pragmatic for an MVP and demonstrates readiness for a production environment. The architecture must be scalable and highly available.

**Decision:**
We adopted a **hybrid cloud deployment model** on AWS. The core application is a **scalable, modular monolith** deployed on **AWS Fargate**, while stateful dependencies leverage managed services.

**Justification:**
1.  **Scalable Monolith:** Starting with a well-structured monolith allows for rapid development. By designing it to be **stateless** and organizing code into logical modules that communicate asynchronously (via Kafka and Domain Events), we ensure it can be **scaled horizontally** from day one. Load testing confirmed a 2.38x throughput increase when scaling from 1 to 2 instances.
2.  **Managed Services for State:** We delegate the complexity of high availability for stateful components to AWS. **PostgreSQL runs on RDS** and **Redis on ElastiCache**, with a clear roadmap to enable **Multi-AZ** configurations for production-grade fault tolerance.
3.  **Pragmatic Infrastructure for Kafka (SPoF Acknowledgment):** For this project's scope and cost constraints, Kafka is deployed on a single EC2 instance. We explicitly acknowledge this as a **Single Point of Failure**. The production architecture would migrate this to a managed, multi-broker cluster like **Amazon MSK** with a replication factor of 3 to ensure high availability. This decision demonstrates an understanding of production requirements and pragmatic trade-offs.
