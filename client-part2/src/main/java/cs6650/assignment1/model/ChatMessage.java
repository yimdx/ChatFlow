package cs6650.assignment1.model;

import com.fasterxml.jackson.annotation.JsonProperty;import com.fasterxml.jackson.annotation.JsonIgnore;import java.time.Instant;

public class ChatMessage {
    
    public enum MessageType {
        TEXT, JOIN, LEAVE
    }
    
    @JsonProperty("userId")
    private int userId;
    
    @JsonProperty("username")
    private String username;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    @JsonProperty("messageType")
    private MessageType messageType;
    
    public ChatMessage() {
    }
    
    public ChatMessage(Integer userId, String username, String message, Instant timestamp, MessageType messageType) {
        this.userId = userId;
        this.username = username;
        this.message = message;
        this.timestamp = timestamp;
        this.messageType = messageType;
    }
    
    public int getUserId() {
        return userId;
    }
    
    public void setUserId(int userId) {
        this.userId = userId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public MessageType getMessageType() {
        return messageType;
    }
    
    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }
}
