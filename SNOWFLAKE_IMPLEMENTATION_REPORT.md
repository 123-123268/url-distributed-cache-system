# Snowflake ID Implementation Report

## 1. Project

**Project:** URL Distributed Cache System  
**Repository:** https://github.com/123-123268/url-distributed-cache-system  
**Implementation commit:** [`c9cf171`](https://github.com/123-123268/url-distributed-cache-system/commit/c9cf171)  
**Runtime:** Java 17, Spring Boot 3.5, PostgreSQL 17, Docker Compose

## 2. Objective

The project originally used database-backed or random short-code generation. The
implementation changed URL creation to use application-generated Snowflake IDs,
allowing multiple application containers to generate unique identifiers against
the same PostgreSQL database without relying on a shared PostgreSQL sequence.

The existing multi-container Docker architecture was preserved:

- One PostgreSQL container with a persistent volume
- Two Spring Boot application containers
- NGINX gateway and load balancing
- Direct access to application instances on ports 8081 and 8082

## 3. Snowflake Design

Each ID is a positive 63-bit signed `BIGINT` composed of:

| Component | Bits | Range / meaning |
| --- | ---: | --- |
| Timestamp | 41 | Milliseconds since `2024-01-01T00:00:00Z` |
| Worker ID | 10 | Values 0 through 1023 |
| Sequence | 12 | Values 0 through 4095 per millisecond |

The layout uses all 63 non-sign bits:

```text
timestamp << 22 | workerId << 12 | sequence
```

The 41-bit timestamp provides approximately 69 years of capacity from the
custom epoch. The 12-bit sequence supports up to 4096 IDs per worker in one
millisecond. When the sequence is exhausted, the generator waits for the next
millisecond.

## 4. Worker-ID Strategy

Worker IDs are explicitly assigned through Docker Compose. They are not derived
from container IP addresses or host ports.

```yaml
app-1:
  environment:
    SNOWFLAKE_WORKER_ID: 1

app-2:
  environment:
    SNOWFLAKE_WORKER_ID: 2
```

Spring maps the environment variable through:

```properties
app.snowflake.worker-id=${SNOWFLAKE_WORKER_ID:0}
```

The worker ID is validated to remain within 0–1023. Every live application
instance must have a unique worker ID. A replacement container should only
reuse a worker ID after the previous instance has stopped.

The host ports `8081` and `8082` are networking ports only; they are not used
as worker IDs.

## 5. Implementation Details

### ID generator

[`SnowflakeIdGenerator.java`](src/main/java/com/kavya/url_shortner/domain/services/SnowflakeIdGenerator.java)

- Uses a synchronized `nextId()` method for thread safety.
- Tracks the last generated timestamp and sequence.
- Rejects timestamps before the custom epoch.
- Rejects timestamps outside the 41-bit range.
- Detects clock rollback and fails explicitly instead of risking duplicates.
- Waits for the next millisecond after sequence rollover.
- Uses UTC-based epoch calculations.

### Worker properties

[`SnowflakeProperties.java`](src/main/java/com/kavya/url_shortner/config/SnowflakeProperties.java)

- Binds `app.snowflake.worker-id`.
- Validates the worker ID using minimum and maximum constraints.
- Is discovered by Spring through configuration-properties scanning.

### Base62 encoding

[`Base62Encoder.java`](src/main/java/com/kavya/url_shortner/domain/services/Base62Encoder.java)

Snowflake IDs are deterministically encoded into URL-safe Base62 strings. The
same ID always produces the same short key, and no random collision-retry loop
is required.

### URL creation

[`ShortUrlService.java`](src/main/java/com/kavya/url_shortner/domain/services/ShortUrlService.java)

URL creation now:

1. Generates a Snowflake ID.
2. Assigns the ID to `short_urls.id`.
3. Encodes the same ID as `short_urls.short_key`.
4. Persists both values in the same entity.

### Persistence mapping

[`ShortUrl.java`](src/main/java/com/kavya/url_shortner/domain/entities/ShortUrl.java)

- Uses application-assigned `Long` IDs.
- Does not use JPA identity or sequence generation.
- Allows up to 11 Base62 characters for the short key.

## 6. Database Migration

The Flyway migrations make the application the sole owner of ID generation:

- [`V1__create_tables.sql`](src/main/resources/db/migration/V1__create_tables.sql)
  contains the original schema.
- [`V2__use_application_generated_snowflake_ids.sql`](src/main/resources/db/migration/V2__use_application_generated_snowflake_ids.sql)
  removes the `BIGSERIAL` default, removes the legacy sequence, and expands
  `short_key` to `VARCHAR(11)`.
- [`V3__drop_legacy_short_url_sequence.sql`](src/main/resources/db/migration/V3__drop_legacy_short_url_sequence.sql)
  safely removes the old sequence for databases that may still contain it.

## 7. Docker Configuration

[`compose.yaml`](compose.yaml) retains the existing topology:

| Service | Purpose | Host access |
| --- | --- | --- |
| `postgres` | Shared PostgreSQL database | `localhost:5433` |
| `app-1` | First Spring Boot instance, worker 1 | `localhost:8081` |
| `app-2` | Second Spring Boot instance, worker 2 | `localhost:8082` |
| `gateway` | NGINX load balancer | `localhost:8080` |

Both application containers connect to PostgreSQL using the Docker service name
`postgres`, ensuring that they share the same database.

## 8. Test Coverage

The implementation includes focused tests for:

- Unique IDs within one application instance
- Concurrent generation from multiple threads
- Sequence rollover and waiting for the next millisecond
- Distinct IDs for different worker IDs
- Clock rollback detection
- Deterministic Base62 encoding

Test files:

- [`SnowflakeIdGeneratorTest.java`](src/test/java/com/kavya/url_shortner/domain/services/SnowflakeIdGeneratorTest.java)
- [`Base62EncoderTest.java`](src/test/java/com/kavya/url_shortner/domain/services/Base62EncoderTest.java)

## 9. Validation Completed

The following validations passed:

```text
.\mvnw.cmd test
Tests run: 9
Failures: 0
Errors: 0
BUILD SUCCESS
```

Docker Compose syntax and interpolation were also validated successfully:

```text
docker compose config
```

The resolved configuration confirmed:

- `SNOWFLAKE_WORKER_ID=1` for `app-1`
- `SNOWFLAKE_WORKER_ID=2` for `app-2`
- Ports 8081 and 8082 remain application access ports
- Both application services use the shared `postgres` service

## 10. Recommended Demonstration Procedure

Run from the repository root:

```bash
docker compose up -d --build
docker compose ps
```

Create URLs through both:

- http://localhost:8081
- http://localhost:8082

Then verify uniqueness in the shared database:

```bash
docker compose exec postgres psql -U postgres -d postgres -c "SELECT id, short_key, original_url FROM short_urls ORDER BY id DESC;"
docker compose exec postgres psql -U postgres -d postgres -c "SELECT COUNT(*) AS rows, COUNT(DISTINCT id) AS distinct_ids, COUNT(DISTINCT short_key) AS distinct_short_keys FROM short_urls;"
```

The three counts in the second query should be equal. For restart validation:

```bash
docker compose restart app-1
docker compose logs -f app-1 app-2
```

Create additional URLs after the restart and repeat the uniqueness query.

Stop the demonstration with:

```bash
docker compose down
```

## 11. Deployment Status

The implementation was committed and pushed to the repository on the `main`
branch in commit `c9cf171`. This report documents the completed code changes,
automated validation, Docker configuration validation, and the remaining
operator-run multi-container demonstration procedure.
