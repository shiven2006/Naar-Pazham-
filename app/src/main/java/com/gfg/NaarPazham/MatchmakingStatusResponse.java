package com.gfg.NaarPazham;

public class MatchmakingStatusResponse {
    private String status;
    private int queueSize;

    public MatchmakingStatusResponse() {}

    public MatchmakingStatusResponse(String status, int queueSize) {
        this.status = status;
        this.queueSize = queueSize;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getQueueSize() { return queueSize; }
    public void setQueueSize(int queueSize) { this.queueSize = queueSize; }
}
