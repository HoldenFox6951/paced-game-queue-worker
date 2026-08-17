package learn.gameworker.service;

import learn.gameworker.domain.GameJob;
import learn.gameworker.domain.ModerationDecision;

public final class GameJobPolicy {
    public ModerationDecision decide(GameJob job) {
        if (job.riskScore() >= 70) {
            return new ModerationDecision(ModerationDecision.Action.REJECT,
                    "high-risk player content stays out of the learning session");
        }
        if (job.kind() == GameJob.Kind.MODERATION || job.riskScore() >= 40) {
            return new ModerationDecision(ModerationDecision.Action.REVIEW,
                    "a teacher or moderator checks the content before class");
        }
        return new ModerationDecision(ModerationDecision.Action.PUBLISH,
                "the asset can enter the live lesson");
    }
}
