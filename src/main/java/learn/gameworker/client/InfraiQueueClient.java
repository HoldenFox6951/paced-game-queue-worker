package learn.gameworker.client;

import learn.gameworker.config.WorkerConfig;
import learn.gameworker.domain.GameJob;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiQueueClient {
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern ERROR_CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern MESSAGE = Pattern.compile(
            "\\{[^{}]*\\\"message_id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[^{}]*\\\"payload\\\"\\s*:\\s*\\{([^{}]*)}[^{}]*}");
    private static final Pattern FIELD = Pattern.compile("\\\"([a-z_]+)\\\"\\s*:\\s*(?:\\\"([^\\\"]*)\\\"|(\\d+))");

    private final WorkerConfig config;
    private final HttpClient http;

    public InfraiQueueClient(WorkerConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build();
    }

    public List<GameJob> consume() {
        String body = "{\"queue\":\"" + escape(config.queue())
                + "\",\"max_messages\":" + config.maxMessages()
                + ",\"visibility_timeout\":" + config.visibilityTimeout() + "}";
        return parseMessages(call("POST", "/v1/queue/consume", body, null));
    }

    public void ack(String messageId) {
        call("POST", "/v1/queue/ack", "{\"queue\":\"" + escape(config.queue())
                + "\",\"message_id\":\"" + escape(messageId) + "\"}", null);
    }

    public void publish(GameJob job) {
        String payload = "{\"kind\":\"" + job.kind().name().toLowerCase(Locale.ROOT)
                + "\",\"player_id\":\"" + escape(job.playerId())
                + "\",\"asset_id\":\"" + escape(job.assetId())
                + "\",\"risk_score\":" + job.riskScore() + "}";
        call("POST", "/v1/queue/publish", "{\"queue\":\"" + escape(config.queue())
                + "\",\"payload\":" + payload + "}", UUID.randomUUID().toString());
    }

    private String call(String method, String path, String json, String idempotencyKey) {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(config.baseUri().resolve(path))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json));
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
            try {
                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                Envelope envelope = decodeEnvelope(response.body(), response.statusCode());
                if (response.statusCode() == 429 && attempt < 3) {
                    pause(retryDelay(response, attempt));
                    continue;
                }
                if (!envelope.ok()) throw new InfraiException(envelope.errorCode(), response.statusCode());
                if (response.statusCode() >= 500) throw new InfraiException("transport response", response.statusCode());
                return envelope.dataJson();
            } catch (IOException e) {
                throw new InfraiException("transport error: " + e.getMessage(), 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InfraiException("worker interrupted", 0);
            }
        }
        throw new InfraiException("rate limit retry budget exhausted", 429);
    }

    private static Envelope decodeEnvelope(String body, int status) {
        Matcher ok = OK.matcher(body);
        if (!ok.find()) throw new InfraiException("response envelope was not JSON", status);
        boolean accepted = Boolean.parseBoolean(ok.group(1));
        Matcher code = ERROR_CODE.matcher(body);
        return new Envelope(accepted, accepted ? "" : (code.find() ? code.group(1) : "request rejected"), body);
    }

    private static List<GameJob> parseMessages(String dataJson) {
        List<GameJob> jobs = new ArrayList<>();
        Matcher messages = MESSAGE.matcher(dataJson);
        while (messages.find()) {
            String kind = "";
            String player = "";
            String asset = "";
            int risk = 0;
            Matcher fields = FIELD.matcher(messages.group(2));
            while (fields.find()) {
                String value = fields.group(2) != null ? fields.group(2) : fields.group(3);
                switch (fields.group(1)) {
                    case "kind" -> kind = value;
                    case "player_id" -> player = value;
                    case "asset_id" -> asset = value;
                    case "risk_score" -> risk = Integer.parseInt(value);
                    default -> { }
                }
            }
            jobs.add(new GameJob(messages.group(1), GameJob.Kind.valueOf(kind.toUpperCase(Locale.ROOT)), player, asset, risk));
        }
        return jobs;
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> Duration.ofSeconds(Long.parseLong(value)))
                .orElse(Duration.ofMillis(250L * (1L << attempt)));
    }

    private static void pause(Duration duration) throws InterruptedException {
        Thread.sleep(duration.toMillis());
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Envelope(boolean ok, String errorCode, String dataJson) { }

    public static final class InfraiException extends RuntimeException {
        private final int status;
        public InfraiException(String message, int status) { super(message); this.status = status; }
        public int status() { return status; }
    }
}
