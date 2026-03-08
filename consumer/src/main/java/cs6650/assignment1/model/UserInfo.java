package cs6650.assignment1.model;

public class UserInfo {
    private String userId;
    private String username;
    private String roomId;
    private String sessionId;
    private long lastSeen;

    public UserInfo() {
    }

    public UserInfo(String userId, String username, String roomId, String sessionId, long lastSeen) {
        this.userId = userId;
        this.username = username;
        this.roomId = roomId;
        this.sessionId = sessionId;
        this.lastSeen = lastSeen;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }
}
