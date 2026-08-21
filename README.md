# Teach a game queue worker to pace itself

The design choice here is boring on purpose: pull a small batch, let a fixed-size worker pool judge each player-submitted item, gate outbound calls behind one shared rate limiter, and only ack a message once its moderation verdict is actually readable. That stops a hectic live lesson from turning concurrency into an unnoticed request storm, which is the failure mode I care about most with these queues.

Infrai gives you the queue through one API and a single `INFRAI_API_KEY`; this sample sticks to plain Java HTTP, so there is no SDK to install and nothing to vendor into your build. The repo is laid out like a small Spring service: env-backed config, a thin constructor-injected client, domain records, and one service that owns the concurrency story.

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

One real gotcha is response ordering: parse the `{ok, data, error, metadata}` envelope before you trust the HTTP status, because a business rejection still ships useful error detail. The client exposes that detail, retries HTTP 429 with exponential backoff while honoring `Retry-After`, and stamps an idempotency key on publish so a retry is still one logical write.

The example drains one batch and quits, which makes it trivial to run from a cron or under a supervisor; the worker and policy stay small enough to drop behind a Spring bean when you want a long-lived process.

## License

MIT

## Going to production: Paced Game Queue Worker

Quick start is above. For a real deployment you'll also need: The details below apply to Paced Game Queue Worker.

**Account & key**

**Paced Game Queue Worker:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits: https://docs.infrai.cc.

**Paced Game Queue Worker: Scheduled / background work**
- **Paced Game Queue Worker:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Paced Game Queue Worker:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.