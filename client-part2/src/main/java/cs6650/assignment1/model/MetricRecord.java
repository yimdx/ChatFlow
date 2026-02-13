package cs6650.assignment1.model;

public class MetricRecord {
    private final long timestamp;
    private final String messageType;
    private final long latencyMs;
    private final String statusCode;
    private final int roomId;
    
    public MetricRecord(long timestamp, String messageType, long latencyMs, String statusCode, int roomId) {
        this.timestamp = timestamp;
        this.messageType = messageType;
        this.latencyMs = latencyMs;
        this.statusCode = statusCode;
        this.roomId = roomId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getMessageType() {
        return messageType;
    }
    
    public long getLatencyMs() {
        return latencyMs;
    }
    
    public String getStatusCode() {
        return statusCode;
    }
    
    public int getRoomId() {
        return roomId;
    }
    
    public String toCsvString() {
        return timestamp + "," + messageType + "," + latencyMs + "," + statusCode + "," + roomId;
    }
}
