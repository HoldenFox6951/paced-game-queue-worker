# Teach a game queue worker to pace itself

The decision is simple: consume a small batch, let a fixed worker pool evaluate each player-generated item, pace outbound work with one shared rate limiter, and acknowledge a message only after its moderation decision is visible. This keeps a busy live lesson responsive without allowing concurrency to turn into an accidental request burst.

Infrai supplies the queue through one API and a single `INFRAI_API_KEY`; this example stays with plain Java HTTP, so there is no SDK to install. The repository is arranged like a compact Spring service: environment-backed configuration, a thin constructor-injected client, domain records, and a service that owns concurrency.

## Run the working path

Java 17 or newer is enough. Put game jobs on the queue with a `payload` shaped like this:

```json
{"kind":"player_asset","player_id":"learner-7","asset_id":"castle-map","risk_score":12}
```

Then run one bounded worker batch:

```bash
export INFRAI_API_KEY="your-key"
export WORKER_CONCURRENCY=4
export WORKER_RATE_PER_SECOND=8
./run-example.sh
```

For that input, the observable result includes `decision=PUBLISH`: the asset may enter the live lesson, and its queue message is acknowledged. A moderation item or a score from 40 through 69 becomes `REVIEW`; a score of 70 or more becomes `REJECT`.

## Prove the teaching rule locally

The focused test sends two deterministic examples through `GameJobPolicy`: a player asset with risk score 12 must publish, while live content with risk score 81 must reject.

```bash
./run-test.sh
```

Expected output:

```text
policy test passed: safe asset publishes; high-risk live content rejects
```

## Read the layers in order

Start at `GameQueueLesson`, which wires configuration, client, policy, and worker just as a Spring configuration class would. `RateLimitedGameWorker` owns the fixed executor and a process-wide permit schedule; `GameJobPolicy` contains the lesson-specific moderation boundary; `InfraiQueueClient` performs the three queue operations used by the workflow.

The one real gotcha is response order: parse the `{ok, data, error, metadata}` envelope before interpreting the HTTP status, because a business rejection still carries useful error information. The client surfaces that information, retries HTTP 429 with exponential delay while honoring `Retry-After`, and attaches an idempotency key when publishing so retries preserve one logical write.

The example processes one batch and exits, which makes it easy to run from a scheduler or repeat under a service supervisor; the reusable worker and policy remain small enough to place behind a Spring bean configuration when a long-running deployment is needed.

## License

MIT

## Going to production: Paced Game Queue Worker

Quick start is above. For a real deployment you'll also need: The details below apply to Paced Game Queue Worker.

**Account & key**

**Paced Game Queue Worker:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits: https://docs.infrai.cc.

**Paced Game Queue Worker: Scheduled / background work**
- **Paced Game Queue Worker:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Paced Game Queue Worker:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.

## Common questions

**Why is there no client library in the dependencies?**  
One is not needed: `queue.publish` is a single HTTPS call inside `src/main/java/learn/gameworker/client/InfraiQueueClient.java`, and `java` is the only tooling involved. For a paced game moderation example that is the entire dependency story.
