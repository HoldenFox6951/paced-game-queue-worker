package learn.gameworker.service;

import learn.gameworker.client.InfraiQueueClient;
import learn.gameworker.domain.GameJob;
import learn.gameworker.domain.ModerationDecision;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class RateLimitedGameWorker implements AutoCloseable {
    private final InfraiQueueClient queue;
    private final GameJobPolicy policy;
    private final ExecutorService workers;
    private final long intervalNanos;
    private final AtomicLong nextPermit = new AtomicLong();

    public RateLimitedGameWorker(InfraiQueueClient queue, GameJobPolicy policy, int concurrency, int permitsPerSecond) {
        this.queue = queue;
        this.policy = policy;
        this.workers = Executors.newFixedThreadPool(concurrency);
        this.intervalNanos = TimeUnit.SECONDS.toNanos(1) / permitsPerSecond;
    }

    public int runOneBatch() {
        List<GameJob> jobs = queue.consume();
        jobs.forEach(job -> workers.submit(() -> process(job)));
        return jobs.size();
    }

    private void process(GameJob job) {
        awaitPermit();
        ModerationDecision decision = policy.decide(job);
        System.out.printf("job=%s asset=%s decision=%s lesson=%s%n",
                job.messageId(), job.assetId(), decision.action(), decision.lesson());
        queue.ack(job.messageId());
    }

    private void awaitPermit() {
        long now = System.nanoTime();
        long slot = nextPermit.getAndUpdate(previous -> Math.max(previous, now) + intervalNanos);
        long delay = slot - now;
        if (delay > 0) {
            try {
                TimeUnit.NANOSECONDS.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("worker interrupted", e);
            }
        }
    }

    @Override
    public void close() {
        workers.shutdown();
        try {
            workers.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
