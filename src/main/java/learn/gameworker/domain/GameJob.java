package learn.gameworker.domain;

public record GameJob(String messageId, Kind kind, String playerId, String assetId, int riskScore) {
    public enum Kind { PLAYER_ASSET, LIVE_EVENT, MODERATION }
}
