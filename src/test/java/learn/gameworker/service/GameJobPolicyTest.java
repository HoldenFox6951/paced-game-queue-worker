package learn.gameworker.service;

import learn.gameworker.domain.GameJob;
import learn.gameworker.domain.ModerationDecision;

public final class GameJobPolicyTest {
    public static void main(String[] args) {
        GameJobPolicy policy = new GameJobPolicy();
        GameJob safeLessonAsset = new GameJob("m-1", GameJob.Kind.PLAYER_ASSET, "learner-7", "castle-map", 12);
        GameJob riskyChat = new GameJob("m-2", GameJob.Kind.LIVE_EVENT, "learner-8", "live-chat", 81);

        expect(ModerationDecision.Action.PUBLISH, policy.decide(safeLessonAsset).action());
        expect(ModerationDecision.Action.REJECT, policy.decide(riskyChat).action());
        System.out.println("policy test passed: safe asset publishes; high-risk live content rejects");
    }

    private static void expect(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + " but got " + actual);
    }
}
