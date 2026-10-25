# URL Shortener

Production-oriented URL shortening platform built using Spring Boot and Redis, designed for low-latency redirects, efficient short URL generation, and abuse protection.
## Live Demo

Application: https://url-shortener-spring-proj.onrender.com/

> **Note:** Hosted on Render's free tier. The initial request may experience a delay of up to 2–4 minutes due to cold starts.

### Demo User:
Email: siva@example.com

Password: secret

Users can register and test the application directly.
## Features

- Session-based authentication and authorization using Spring Security
- Create and manage shortened URLs
- Public and private URL support
- URL expiration handling
- Base62 encoded short URLs
- Redis cache-aside pattern for faster redirects
- Redis-backed rate limiting
- Collision-free short URL generation using range-based ID allocation
- Centralized exception handling and validation
- Flyway database migrations
- Docker-based local development setup
- ## System Architecture

### Authentication & Security Flow

<p align="center">
  <img src="public/auth-architecture.png" width="300">
</p>

This diagram illustrates the authentication flow, request processing pipeline, and security mechanisms implemented using Spring Security.

### URL Processing Flow

<p align="center">
  <img src="public/Flow-Diagram.png" width="300">
</p>

This flow diagram describes the end-to-end lifecycle of a shortened URL, including generation, caching, database access, validation, and redirection.

---

## Application Screenshots

### Login Page

<p align="center">
  <img src="public/Loginpage.png" width="500">
</p>

### Dashboard

<p align="center">
  <img src="public/dashboard.png" width="500">
</p>

### My URLs

<p align="center">
  <img src="public/my-urls.png" width="500">
</p>
- ## Short URL Generation

Short URLs use application-generated Snowflake IDs, so the two containers can create records in the same PostgreSQL database without coordinating through a database sequence.

| Field | Bits | Description |
| --- | ---: | --- |
| Timestamp | 41 | Milliseconds since `2024-01-01T00:00:00Z` (about 69 years) |
| Worker ID | 10 | Instance number from 0 to 1023 |
| Sequence | 12 | 0 to 4095 IDs produced during the same millisecond |

The ID remains a positive 63-bit `BIGINT`. Each ID is deterministically Base62-encoded for `short_key`; the database permits up to 11 Base62 characters. The generator is synchronized within an application instance, waits for a new millisecond after sequence rollover, and fails fast if the system clock moves backwards to avoid duplicates.

`compose.yaml` assigns `SNOWFLAKE_WORKER_ID=1` to `app-1` and
`SNOWFLAKE_WORKER_ID=2` to `app-2`. Worker IDs are explicit configuration,
not derived from container IP addresses or host ports. Any additional live
instance must be configured with a different value in the range 0–1023;
two running instances must never share a worker ID. A container restart must
stop the old instance before starting the replacement.

The Flyway migrations remove the `BIGSERIAL` default and its sequence, making the application the sole owner of `short_urls.id` generation.
## Redirect Flow

When a user accesses a short URL:

1. Redis is checked for the original URL.
2. If found, the user is redirected immediately.
3. Otherwise, PostgreSQL is queried.
4. The URL is validated (expiration/access checks).
5. Redis is updated.
6. The user is redirected.
7. ## Rate Limiting

Redis-backed rate limiting protects authentication and URL creation endpoints.

Requests exceeding configured thresholds receive HTTP 429 (Too Many Requests), helping prevent abuse and accidental misuse.
## API Endpoints

### Authentication
- `POST /register` – Register a new user account.
- `POST /login` – Authenticate an existing user.
- `POST /logout` – End the current user session.

### URL Management
- `POST /short-urls` – Create a new shortened URL.
- `GET /my-urls` – Retrieve all URLs created by the authenticated user.
- `POST /delete-urls` – Delete an existing shortened URL.

### Redirect
- `GET /s/{shortKey}` – Redirect users to the original URL associated with the short key.

---

## Running Locally

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-username/url-shortener.git
   cd url-shortener
   ```

2. **Start the required services using Docker**
   ```bash
   docker compose up -d --build
   ```

3. **Run the Spring Boot application**
   ```bash
   ./mvnw spring-boot:run
   ```

4. **Access the application**
   - Open your browser and navigate to:
     ```
     http://localhost:8080
     ```

## Verifying distributed generation

Run the Snowflake unit tests:

```bash
./mvnw test -Dtest=SnowflakeIdGeneratorTest,Base62EncoderTest
```

Then start both application instances and inspect the shared database after
creating URLs through both direct instance ports:

```bash
docker compose up -d --build
docker compose ps
```

Create URLs through `http://localhost:8081` and `http://localhost:8082`, then
inspect the shared database:

```bash
docker compose exec postgres psql -U postgres -d postgres -c "SELECT id, short_key FROM short_urls ORDER BY created_at DESC;"
```

`id` and `short_key` should both be unique. To verify concurrent generation,
create multiple URLs through both ports and run:

```bash
docker compose exec postgres psql -U postgres -d postgres -c "SELECT COUNT(*) AS rows, COUNT(DISTINCT id) AS distinct_ids, COUNT(DISTINCT short_key) AS distinct_short_keys FROM short_urls;"
```

The three counts should be equal. Restart one application container and repeat
the test:

```bash
docker compose restart app-1
docker compose logs -f app-1 app-2
```

The included unit tests cover single-instance uniqueness, concurrent
generation, sequence rollover, distinct worker IDs, and clock rollback.
     
## Tech Stack

| Category | Technologies |
|----------|--------------|
| Backend | Spring Boot |
| Security | Spring Security |
| Database | PostgreSQL |
| Caching | Redis |
| Migrations | Flyway |
| ORM | JPA / Hibernate |
| Containerization | Docker |
| Build Tool | Maven |
## Future Improvements

- Analytics dashboard
- Distributed ID allocation across multiple instances
- Token bucket rate limiting
- Monitoring and observability
- CI/CD pipeline integration
