# Server-Sent Events (SSE) Demo

A Spring Boot WebFlux application demonstrating real-time news broadcasting using Server-Sent Events (SSE). This application provides reactive streams for real-time news updates and subscriber count tracking.

## Features

- **Real-time News Broadcasting**: Stream news updates to multiple subscribers simultaneously
- **Subscriber Count Tracking**: Monitor the number of active subscribers in real-time
- **Combined Stream**: Single SSE connection for both news and subscriber count updates
- **Reactive Architecture**: Built with Spring WebFlux and Reactor for non-blocking, reactive streams
- **Thread-Safe Operations**: Synchronized collections and atomic counters for concurrent access

## Service Overview

### NewsService

The `NewsService` manages news items and provides reactive streams for real-time updates:

- **News Management**: Stores and broadcasts news items to all subscribers
- **Subscriber Tracking**: Maintains and broadcasts the count of active subscribers
- **Reactive Streams**: Uses Reactor's `Flux` and `Sinks` for efficient multicasting
- **Initial Data**: Pre-populated with default news items on startup

#### Key Methods:
- `getNewsStream()`: Returns a Flux that emits all existing news first, then streams new news
- `getSubscriberCountStream()`: Returns a Flux that emits subscriber count updates
- `getCombinedStream()`: Returns a combined Flux of both news and count updates
- `addNews()`: Adds new news and broadcasts it to all subscribers

## API Endpoints

### 1. Simple SSE Stream Example

**GET** `/stream-sse`

A simple demonstration endpoint that streams SSE events every second for 20 seconds.

**Response**: Server-Sent Events stream
- **Event Type**: `message`
- **Data**: Timestamp messages
- **Duration**: 20 events (1 per second)

**Example Usage**:
```bash
curl -N http://localhost:8081/stream-sse
```

### 2. Combined News Stream

**GET** `/news/stream`

Streams both news updates and subscriber count updates through a single SSE connection. This reduces the number of connections needed (from 2 per tab to 1 per tab), avoiding browser connection limits (typically 6 per domain).

**Response**: Server-Sent Events stream with two event types:
- **Event Type**: `news` - Contains news data
- **Event Type**: `count` - Contains subscriber count

**News Event Format**:
```json
{
  "id": 1,
  "title": "News Title",
  "content": "News content...",
  "publishedTime": "2024-01-01T12:00:00",
  "category": "Technology",
  "author": "Author Name"
}
```

**Count Event Format**:
```json
5
```

**Example Usage**:
```bash
curl -N http://localhost:8081/news/stream
```

**Client-Side Example (JavaScript)**:
```javascript
const eventSource = new EventSource('http://localhost:8081/news/stream');

eventSource.addEventListener('news', (event) => {
    const news = JSON.parse(event.data);
    console.log('New news:', news);
});

eventSource.addEventListener('count', (event) => {
    const count = parseInt(event.data);
    console.log('Subscriber count:', count);
});
```

### 3. Add News

**POST** `/news`

Adds a new news item and broadcasts it to all active subscribers.

**Request Body**:
```json
{
  "title": "News Title",
  "content": "News content...",
  "category": "Technology",
  "author": "Author Name"
}
```

**Response**: The created news object with generated ID and timestamp

**Example Usage**:
```bash
curl -X POST http://localhost:8081/news \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Breaking News",
    "content": "This is breaking news content...",
    "category": "Technology",
    "author": "John Doe"
  }'
```

## Configuration

The application runs on port **8081** by default (configurable in `application.properties`).

```properties
spring.application.name=server-sent-event
server.port=8081
```

## Technology Stack

- **Spring Boot**: Application framework
- **Spring WebFlux**: Reactive web framework
- **Project Reactor**: Reactive programming library
- **Lombok**: Reduces boilerplate code
- **Jackson**: JSON serialization/deserialization

## Running the Application

1. **Using Maven Wrapper**:
   ```bash
   ./mvnw spring-boot:run
   ```

2. **Using Maven**:
   ```bash
   mvn spring-boot:run
   ```

3. **Build and Run**:
   ```bash
   mvn clean package
   java -jar target/server-sent-events-*.jar
   ```

## Testing

### Test SSE Streams

1. **Open browser console** and navigate to `http://localhost:8081` (if HTML client is available)
2. **Use curl** to test endpoints:
   ```bash
   # Test simple SSE stream
   curl -N http://localhost:8081/stream-sse
   
   # Test news stream
   curl -N http://localhost:8081/news/stream
   ```

3. **Add news via API**:
   ```bash
   curl -X POST http://localhost:8081/news \
     -H "Content-Type: application/json" \
     -d '{"title":"Test News","content":"Test content","category":"Tech","author":"Test Author"}'
   ```

## Architecture Notes

- **Reactive Streams**: Uses Reactor's `Flux` for non-blocking, backpressure-aware streams
- **Multicasting**: Uses `Sinks.Many` with multicast configuration to efficiently broadcast to multiple subscribers
- **Thread Safety**: Uses synchronized collections and atomic counters for concurrent access
- **Connection Management**: Automatically tracks subscriber connections and disconnections
- **Error Handling**: Includes error handling and logging for stream errors

## Browser Compatibility

SSE is supported in all modern browsers. The EventSource API is used on the client side to receive SSE streams.
