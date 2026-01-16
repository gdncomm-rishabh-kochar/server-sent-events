package com.example.server_sent_event.controller;

import com.example.server_sent_event.model.News;
import com.example.server_sent_event.service.NewsService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@Slf4j
public class EventController {

    private final NewsService newsService;

    public EventController(NewsService newsService) {
        this.newsService = newsService;
    }

    /**
     * Simple SSE stream example
     */
    @GetMapping(value = "/stream-sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamSseEvents() {
        return Flux.interval(Duration.ofSeconds(1))
                .take(20)
                .map(sequence -> ServerSentEvent.<String>builder()
                        .id(String.valueOf(sequence))
                        .event("message")
                        .data("SSE WebFlux - " + System.currentTimeMillis())
                        .build());
    }

    /**
     * Combined SSE endpoint that streams both news and subscriber count updates
     * This reduces connections from 2 per tab to 1 per tab, avoiding browser connection limits (6 per domain)
     */
    @GetMapping(value = "/news/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> subscribeToCombinedStream() {
        return newsService.getCombinedStream()
                .map(update -> {
                    if ("news".equals(update.getType())) {
                        News news = (News) update.getData();
                        return ServerSentEvent.<Object>builder()
                                .id(String.valueOf(news.getId()))
                                .event("news")
                                .data(news)
                                .build();
                    } else {
                        Integer count = (Integer) update.getData();
                        return ServerSentEvent.<Object>builder()
                                .id(String.valueOf(count))
                                .event("count")
                                .data(count)
                                .build();
                    }
                })
                .doOnSubscribe(subscription -> log.info("New subscriber connected to combined stream"))
                .doOnCancel(() -> log.info("Subscriber disconnected from combined stream"))
                .doOnError(error -> log.error("Error in combined stream: {}", error.getMessage(), error))
                .onErrorResume(error -> {
                    log.error("Fatal error in combined stream: {}", error.getMessage(), error);
                    return Flux.empty();
                });
    }

    /**
     * Add new news
     */
    @PostMapping("/news")
    public Mono<News> addNews(@RequestBody NewsRequest request) {
        News news = newsService.addNews(
                request.getTitle(),
                request.getContent(),
                request.getCategory(),
                request.getAuthor()
        );
        return Mono.just(news);
    }

    /**
     * Request DTO for adding news
     */
    @Getter
    @Setter
    public static class NewsRequest {
        private String title;
        private String content;
        private String category;
        private String author;
    }
}
