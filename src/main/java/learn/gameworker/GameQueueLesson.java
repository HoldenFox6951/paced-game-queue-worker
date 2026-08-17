package learn.gameworker;

import learn.gameworker.client.InfraiQueueClient;
import learn.gameworker.config.WorkerConfig;
import learn.gameworker.service.GameJobPolicy;
import learn.gameworker.service.RateLimitedGameWorker;

public final class GameQueueLesson {
    private GameQueueLesson() { }

    public static void main(String[] args) {
        WorkerConfig config = WorkerConfig.fromEnvironment();
        InfraiQueueClient queue = new InfraiQueueClient(config);
        try (RateLimitedGameWorker worker = new RateLimitedGameWorker(
                queue, new GameJobPolicy(), config.concurrency(), config.permitsPerSecond())) {
            int count = worker.runOneBatch();
            System.out.println("lesson batch accepted " + count + " game jobs");
        } catch (InfraiQueueClient.InfraiException error) {
            System.err.printf("queue request was declined: status=%d detail=%s%n", error.status(), error.getMessage());
            System.exit(error.status() >= 400 && error.status() < 500 ? 2 : 1);
        }
    }
}
