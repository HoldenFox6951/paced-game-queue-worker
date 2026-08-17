package learn.gameworker.domain;

public record ModerationDecision(Action action, String lesson) {
    public enum Action { PUBLISH, REVIEW, REJECT }
}
