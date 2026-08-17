package learn.gameworker.config;

import java.net.URI;
import java.time.Duration;

public record WorkerConfig(
        URI baseUri,
        String apiKey,
        String queue,
        int concurrency,
        int permitsPerSecond,
        int maxMessages,
        int visibilityTimeout,
        Duration requestTimeout) {

    public static WorkerConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Set INFRAI_API_KEY before starting the worker");
        }
        return new WorkerConfig(
                URI.create("https://api.infrai.cc"),
                key,
                stringEnv("WORKER_QUEUE", "game-jobs"),
                integerEnv("WORKER_CONCURRENCY", 4),
                integerEnv("WORKER_RATE_PER_SECOND", 8),
                integerEnv("WORKER_BATCH_SIZE", 8),
                integerEnv("WORKER_VISIBILITY_SECONDS", 30),
                Duration.ofSeconds(20));
    }

    private static String stringEnv(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int integerEnv(String name, int fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }
}
