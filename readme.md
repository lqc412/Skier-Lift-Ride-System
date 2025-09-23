# Skier Lift Ride System

This project simulates a skier lift ride system using a distributed architecture consisting of a server, client, and RabbitMQ consumer. The system handles multiple POST requests for skier lift rides and stores them asynchronously for further processing.

## Project Components

### 1. Server
- **Technology**: Java Servlet
- **Description**: The server (SkierServlet) provides endpoints to accept skier lift ride data. It uses RabbitMQ to publish incoming messages for further asynchronous processing.
- **Main Features**:
    - `/skiers/*` endpoint for receiving GET and POST requests.
    - Handles requests for skier data and stores lift ride information.
- **Start the Server**:
    - Deploy the servlet on an application server like Apache Tomcat.
    - Make sure RabbitMQ is installed and running.

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
    - Stores or processes the messages received.
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
    - Ensure it can connect to RabbitMQ to publish incoming requests.

3. **Consumer**:
    - Run `MultiThreadConsumer` to start processing the messages from RabbitMQ with 200 threads.

4. **Client**:
    - Run `SkClient2` from the command line or an IDE.
    - Ensure the server endpoint is reachable.


## Configuration

The clients (`Assignment1`) and the server/consumer module (`Server2`) now read their connection
settings from an `application.properties` file on the classpath. Any property can be overridden by
defining an environment variable whose name is the upper snake case representation of the property
key (for example, `client.api.baseUrl` can be overridden with `CLIENT_API_BASE_URL`).

### Assignment1 client settings

File: `Assignment1/src/main/resources/application.properties`

| Property key         | Environment override      | Description                             |
|----------------------|---------------------------|-----------------------------------------|
| `client.api.baseUrl` | `CLIENT_API_BASE_URL`     | Base URL of the skier service endpoint. |

**Local example**

```
client.api.baseUrl=http://localhost:8080/Server2_war
```

**Cloud example**

```
CLIENT_API_BASE_URL=https://your-cloud-endpoint.example.com/Server2_war
```

### Server2 settings

File: `Server2/src/main/resources/application.properties`

| Property key        | Environment override | Description                                      |
|---------------------|----------------------|--------------------------------------------------|
| `rabbitmq.host`     | `RABBITMQ_HOST`      | RabbitMQ server hostname.                        |
| `rabbitmq.port`     | `RABBITMQ_PORT`      | RabbitMQ server port.                            |
| `rabbitmq.username` | `RABBITMQ_USERNAME`  | RabbitMQ username.                               |
| `rabbitmq.password` | `RABBITMQ_PASSWORD`  | RabbitMQ password.                               |
| `queue.name`        | `QUEUE_NAME`         | RabbitMQ queue used by the servlet and consumer. |
| `redis.uri`         | `REDIS_URI`          | Redis connection URI (including credentials).    |

**Local example**

```
rabbitmq.host=localhost
rabbitmq.port=5672
rabbitmq.username=guest
rabbitmq.password=guest
queue.name=SkierServletPostQueue
redis.uri=redis://localhost:6379
```

**Cloud example**

```
export RABBITMQ_HOST=your-rabbitmq-host
export RABBITMQ_PORT=5672
export RABBITMQ_USERNAME=prod-user
export RABBITMQ_PASSWORD=prod-password
export QUEUE_NAME=SkierServletPostQueue
export REDIS_URI=redis://prod-user:prod-password@your-redis-host:6379
```


## System Requirements

- **Java**: JDK 8 or higher.
- **RabbitMQ**: Version 3.8 or higher.
- **Tomcat**: Version 9 or higher.

## Example Usage

- Run RabbitMQ.
- Start the server to accept incoming lift ride data.
- Start the consumer to process the messages in the queue.
- Run the client (`SkClient2`) to simulate 200,000 skier lift rides.

