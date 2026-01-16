package com.example.server_sent_event.service;

import com.example.server_sent_event.model.News;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Mono;
import java.time.Duration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class NewsService {

    // Use synchronized list to ensure thread-safe access
    private final List<News> newsList = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong idGenerator = new AtomicLong(1);
    private final AtomicInteger subscriberCount = new AtomicInteger(0);

    // Use Sinks.Many for multicasting news to all subscribers
    // Increased buffer size to handle more concurrent connections
    private final Sinks.Many<News> newsSink = Sinks.many().multicast().onBackpressureBuffer(1000);

    // Sink for broadcasting subscriber count updates
    // Increased buffer size to handle more concurrent connections
    private final Sinks.Many<Integer> countSink = Sinks.many().multicast().onBackpressureBuffer(1000);

    // Initialize with default news items
    {
        newsList.add(new News(idGenerator.getAndIncrement(),
                "Welcome to News Broadcasting",
                "This is a Server-Sent Events (SSE) based news broadcasting system. Subscribe to receive real-time news updates!",
                LocalDateTime.now().minusHours(2),
                "Technology",
                "System Admin"));

        newsList.add(new News(idGenerator.getAndIncrement(),
                "Spring Boot WebFlux SSE Implementation",
                "This application demonstrates real-time news broadcasting using Spring Boot WebFlux and Server-Sent Events. New subscribers will receive all existing news immediately upon connection.",
                LocalDateTime.now().minusHours(1),
                "Technology",
                "Development Team"));

        log.info("Initialized with {} default news items", newsList.size());
    }

    /**
     * Initialize heartbeat mechanism to detect dead connections
     */
    @PostConstruct
    public void init() {
        log.info("NewsService initialized with WebFlux reactive streams");
    }

    /**
     * Cleanup on shutdown
     */
    @PreDestroy
    public void cleanup() {
        newsSink.tryEmitComplete();
        countSink.tryEmitComplete();
        log.info("NewsService cleanup completed");
    }

    /**
     * Get a Flux of news that emits all existing news first, then streams new news
     * Each subscriber gets a fresh stream with all existing news, then new news
     * The new news stream is shared among all subscribers for efficiency
     */
    public Flux<News> getNewsStream() {
        // Create a snapshot of the list to avoid concurrent modification issues
        // This ensures thread-safe, non-blocking emission
        Flux<News> existingNewsFlux = Flux.defer(() -> {
            List<News> snapshot;
            synchronized (newsList) {
                snapshot = new ArrayList<>(newsList);
            }
            return Flux.fromIterable(snapshot);
        });
        
        // Share the new news stream so all subscribers share the same subscription
        Flux<News> newNewsFlux = newsSink.asFlux().share();

        return Flux.defer(() -> {
                    // Increment count when subscription starts
                    int count = subscriberCount.incrementAndGet();
                    log.info("New subscriber connected. Total subscribers: {}", count);
                    // Emit count update immediately
                    countSink.tryEmitNext(count);
                    return existingNewsFlux;
                })
                .concatWith(newNewsFlux)
                .doOnError(error -> {
                    log.error("Error in news stream: {}", error.getMessage());
                })
                .doFinally(signalType -> {
                    // Decrement count once when subscription ends (guaranteed to fire only once)
                    int count = subscriberCount.decrementAndGet();
                    log.info("Subscriber disconnected ({}). Remaining subscribers: {}", signalType, count);
                    countSink.tryEmitNext(count);
                });
    }

    /**
     * Add new news and notify all subscribers
     */
    public News addNews(String title, String content, String category, String author) {
        News news = new News(idGenerator.getAndIncrement(), title, content, LocalDateTime.now(), category, author);
        newsList.add(news);

        log.info("New news added: {}", news.getTitle());

        // Emit the new news to all subscribers via the sink
        Sinks.EmitResult result = newsSink.tryEmitNext(news);
        if (result.isFailure()) {
            log.warn("Failed to emit news to subscribers: {}", result);
        }

        return news;
    }

    /**
     * Get a Flux of subscriber count updates
     * Emits the current count immediately, then streams updates whenever the count changes
     * Supports multiple subscribers - all subscribers receive the same count updates via multicast sink
     * The stream is shared among all subscribers for efficiency
     */
    public Flux<Integer> getSubscriberCountStream() {
        // Get the multicast flux from the sink and share it among subscribers
        Flux<Integer> countUpdates = countSink.asFlux()
                .distinctUntilChanged() // Only emit when count actually changes
                .share(); // Share the subscription among all subscribers

        // For each new subscriber, emit the current count with a small delay
        // This ensures the count is read after any concurrent news stream subscriptions have incremented it
        // The delay allows time for the news stream subscription to complete and increment the count
        // Then stream all future updates from the shared sink
        return Mono.delay(Duration.ofMillis(150))
                .map(delay -> subscriberCount.get())
                .flux()
                .concatWith(countUpdates)
                .distinctUntilChanged() // Avoid duplicate emissions
                .doOnSubscribe(subscription -> log.debug("New subscriber connected to count stream"))
                .doOnCancel(() -> log.debug("Subscriber disconnected from count stream"));
    }

    /**
     * Combined stream that emits both news and count updates
     * This reduces connections from 2 per tab to 1 per tab, avoiding browser connection limits
     */
    @Getter
    public static class CombinedUpdate {
        private final String type; // "news" or "count"
        private final Object data;

        public CombinedUpdate(String type, Object data) {
            this.type = type;
            this.data = data;
        }

    }

    /**
     * Get a combined stream that emits both news and subscriber count updates
     * This allows a single SSE connection to handle both, avoiding browser connection limits
     * The stream is shared among all subscribers for efficiency
     */
    public Flux<CombinedUpdate> getCombinedStream() {
        Flux<News> newsStream = getNewsStream();
        Flux<Integer> countStream = getSubscriberCountStream();

        // Merge both streams, mapping them to CombinedUpdate objects
        Flux<CombinedUpdate> newsUpdates = newsStream.map(news -> new CombinedUpdate("news", news));
        Flux<CombinedUpdate> countUpdates = countStream.map(count -> new CombinedUpdate("count", count));

        // Merge both streams together and share among subscribers
        // The stream stays open as long as there are subscribers (SSE connection is active)
        return Flux.merge(newsUpdates, countUpdates)
                .share();
    }
}
