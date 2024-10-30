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


## System Requirements

- **Java**: JDK 8 or higher.
- **RabbitMQ**: Version 3.8 or higher.
- **Tomcat**: Version 9 or higher.

## Example Usage

- Run RabbitMQ.
- Start the server to accept incoming lift ride data.
- Start the consumer to process the messages in the queue.
- Run the client (`SkClient2`) to simulate 200,000 skier lift rides.

