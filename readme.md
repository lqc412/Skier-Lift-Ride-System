# Skier Lift Ride System

This project simulates a skier lift ride system using a distributed architecture consisting of a server, client, and RabbitMQ consumer. The system handles multiple POST requests for skier lift rides and stores them asynchronously for further processing.

## Project Components

### 1. Server
- **Technology**: Java Servlet
- **Description**: The server (SkierServlet) provides endpoints to accept skier lift ride data. It uses RabbitMQ to publish incoming messages for further asynchronous processing and relies on Redis for GET lookups of aggregated data.
- **Main Features**:
    - `/skiers/*` endpoint for receiving GET and POST requests.
    - Handles requests for skier data, publishes lift ride events, and serves aggregated vertical statistics from Redis-backed totals populated by the consumer.
- **Start the Server**:
    - Deploy the servlet on an application server like Apache Tomcat.
    - Make sure RabbitMQ and Redis are installed and running.

### 2. Client
- **Technology**: Java, Apache HttpClient
- **Description**: The client application (`SkClient2`) generates and sends multiple requests to the server to simulate skier lift rides.
- **Main Features**:
    - Generates 200,000 lift ride events.
    - Uses multi-threading to send POST requests in multiple phases to achieve maximum throughput.
- **How to Run**:
    1. Compile and run `SkClient2`.
    2. It uses a `CachedThreadPool` to manage threads for optimal throughput.

### 3. Consumer - in `Server2`
- **Technology**: Java, RabbitMQ
- **Description**: The RabbitMQ consumer (`MultiThreadConsumer`) listens to the queue for incoming lift ride messages and processes them.
- **Main Features**:
    - Uses multiple threads to consume messages.
    - Stores the processed data in Redis, recording per-day totals, visited days, and resort visitors.
- **How to Run**:
    1. Ensure RabbitMQ is installed and configured correctly.
    2. Run `MultiThreadConsumer` to start consuming messages from the queue.

## Setup Instructions

1. **RabbitMQ**:
    - Install RabbitMQ on your server.
    - Enable the management plugin for easy monitoring (`sudo rabbitmq-plugins enable rabbitmq_management`).
    - Make sure the required ports (`5672` and `15672`) are open.

2. **Server**:
    - Deploy the `SkierServlet` on a Tomcat or similar server.
    - Ensure it can connect to RabbitMQ to publish incoming requests and to Redis for GET aggregation.

3. **Consumer**:
    - Run `MultiThreadConsumer` to start processing the messages from RabbitMQ with 200 threads.

4. **Client**:
    - Run `SkClient2` from the command line or an IDE.
    - Ensure the server endpoint is reachable.


## Configuration

Runtime credentials and target URLs are provided through a shared `config.AppConfig` helper that reads from environment variables, a properties file, or sensible defaults (in that order). You can point to a custom properties file by setting `APP_CONFIG_FILE`; otherwise the helper looks for `config/app.properties` relative to the working directory.

The following environment variables are recognized:

| Variable | Description | Default |
| --- | --- | --- |
| `CLIENT1_BASEURL` | Base URL used by the phase-one client. | `http://localhost:8080/Server_war_exploded` |
| `CLIENT2_BASEURL` | Base URL used by the high-throughput client. | `http://localhost:8080/Server2_war` |
| `CLIENT2_RATE_LIMIT` | Requests per second enforced by the rate limiter shared by client threads. | `5000` |
| `CLIENT2_FAILURE_THRESHOLD` | Consecutive failure count that opens the client circuit breaker. | `100` |
| `CLIENT2_CIRCUIT_BREAKER_TIMEOUT_MS` | Time in milliseconds before the circuit breaker attempts to close. | `10000` |
| `RABBITMQ_HOST` | Hostname of the RabbitMQ broker. | `localhost` |
| `RABBITMQ_PORT` | Broker port. | `5672` |
| `RABBITMQ_USERNAME` | RabbitMQ username. | `guest` |
| `RABBITMQ_PASSWORD` | RabbitMQ password. | `guest` |
| `REDIS_URI` | Redis connection URI, including credentials if required. | `redis://localhost:6379` |
| `QUEUE_NAME` | Name of the queue shared between the servlet and consumer. | `SkierServletPostQueue` |

For convenience a `.env.example` file shows how to configure the system for local, staging, and production environments. Copy it to `.env`, adjust the values, and export them into your shell (for example via `source .env`) before running any module. The `.env` file is ignored by Git to keep secrets out of version control.


## System Requirements

- **Java**: JDK 8 or higher.
- **RabbitMQ**: Version 3.8 or higher.
- **Tomcat**: Version 9 or higher.

## Example Usage

- Run RabbitMQ.
- Start the server to accept incoming lift ride data.
- Start the consumer to process the messages in the queue and populate Redis.
- Run the client (`SkClient2`) to simulate 200,000 skier lift rides.

## Data Flow

1. **POST ingestion** – `SkierServlet` validates incoming lift ride requests and publishes them to the RabbitMQ `SkierServletPostQueue`.
2. **Asynchronous processing** – `MultiThreadConsumer` pulls messages, calculates lift-derived vertical totals, and updates Redis keys for skier-day verticals, visited days, and resort visitor sets.
3. **Redis-backed reads** – Subsequent GET requests use Redis to retrieve the pre-computed information. Totals are aggregated into the `SkierVertical` DTO before being returned to clients.

## Sample Responses

### Total vertical for a skier

`GET /skiers/12345/vertical`

```json
{
  "skiervertical": [
    {
      "seasonID": "TOTAL",
      "totalVert": 43210
    }
  ]
}
```

### Daily vertical at a resort

`GET /skiers/17/seasons/2024/days/77/skiers/12345`

```json
{
  "skiervertical": [
    {
      "seasonID": "2024",
      "totalVert": 780
    }
  ]
}
```

If no matching visits exist in Redis, the API returns the same shape with a `totalVert` of `0`.

