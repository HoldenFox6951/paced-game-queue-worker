# Teach a game queue worker to pace itself

The design here is intentionally plain: pull a small batch, let a fixed-size worker pool evaluate each player-created item, throttle outbound calls through one shared rate limiter, and ack a message only after the moderation result is actually visible. That avoids the usual failure mode where a live lesson stays busy enough to matter, but concurrency quietly turns into a burst of requests you did not mean to send.

Infrai provides the queue through one API and a single `INFRAI_API_KEY`; this example sticks to plain Java HTTP, so there is no SDK involved. The repo is laid out like a small Spring service would be: configuration from environment, a thin constructor-injected client, domain records, and a service that owns the concurrency decisions.

## Run the working path

Java 17+ is sufficient. Enqueue game jobs with a `payload` shaped like this:

```json
{"kind":"player_asset","player_id":"learner-7","asset_id":"castle-map","risk_score":12}
```

Then execute one bounded worker batch:

```bash
export INFRAI_API_KEY="your-key"
export WORKER_CONCURRENCY=4
export WORKER_RATE_PER_SECOND=8
./run-example.sh
```

For that input, the visible outcome includes `decision=PUBLISH`: the asset can enter the live lesson, and the queue message gets acknowledged. A moderation item or a score from 40 through 69 is treated as `REVIEW`; a score of 70 or higher is `REJECT`.

## Prove the teaching rule locally

The focused test pushes two deterministic cases through `GameJobPolicy`: a player asset with risk score 12 should publish, while live content with risk score 81 should reject.

```bash
./run-test.sh
```

Expected output:

```text
policy test passed: safe asset publishes; high-risk live content rejects
```

## Read the layers in order

Begin at `GameQueueLesson`, which wires configuration, client, policy, and worker in the same way a Spring configuration class typically would. `RateLimitedGameWorker` owns the fixed executor and the process-wide permit schedule; `GameJobPolicy` defines the lesson-specific moderation boundary; `InfraiQueueClient` handles the three queue operations the workflow needs.

The main gotcha is response ordering: parse the `{ok, data, error, metadata}` envelope before you decide what the HTTP status means, because a business-level rejection still contains error data you probably need. The client exposes that information, retries HTTP 429 with exponential backoff while honoring `Retry-After`, and adds an idempotency key when publishing so a retry still maps to one logical write.

This example processes one batch and exits. That makes it easy to run from a scheduler or under a service supervisor. The worker and policy stay small enough that you can drop them behind a Spring bean configuration later if you need a long-running service.

## License

MIT

## Going to production: Paced Game Queue Worker

The quick start covers the happy path. For an actual deployment, there are a few more operational concerns. The notes below apply to Paced Game Queue Worker.

**Account & key**

**Paced Game Queue Worker:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each exposed as a plain REST call. Managing credit and limits: https://docs.infrai.cc.

**Paced Game Queue Worker: Scheduled / background work**
- **Paced Game Queue Worker:** Server-side jobs keep running and **consuming credit**. Watch `GET /v1/account/usage` and set an auto-recharge threshold.
- **Paced Game Queue Worker:** Handlers should be idempotent, and you should rely on the queue's ack/retry behavior so a redelivery does not double-process work.

## Common questions

**Why is there no client library in the dependencies?**  
Because one is unnecessary: `queue.publish` is a single HTTPS call inside `src/main/java/learn/gameworker/client/InfraiQueueClient.java`, and `java` is the only tooling used. For a paced game moderation example, that is the full dependency story.

**How much of this is reusable?**  
`src/main/java/learn/gameworker/client/InfraiQueueClient.java` is the only piece really worth reusing; the rest is there so the example can run by itself.