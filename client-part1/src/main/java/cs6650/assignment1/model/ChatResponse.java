package cs6650.assignment1.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public class ChatResponse {
    private Integer userId;
    private String username;
    private String message;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant clientTimestamp;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant serverTimestamp;
    
    private ChatMessage.MessageType messageType;
    private String status;
    
    // Constructors
    public ChatResponse() {
    }
    
    public ChatResponse(Integer userId, String username, String message, Instant clientTimestamp, 
                       Instant serverTimestamp, ChatMessage.MessageType messageType, String status) {
        this.userId = userId;
        this.username = username;
        this.message = message;
        this.clientTimestamp = clientTimestamp;
        this.serverTimestamp = serverTimestamp;
        this.messageType = messageType;
        this.status = status;
    }
    
    // Getters and Setters
    public Integer getUserId() {
        return userId;
    }
    
    public void setUserId(Integer userId) {
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
    
    public Instant getClientTimestamp() {
        return clientTimestamp;
    }
    
    public void setClientTimestamp(Instant clientTimestamp) {
        this.clientTimestamp = clientTimestamp;
    }
    
    public Instant getServerTimestamp() {
        return serverTimestamp;
    }
    
    public void setServerTimestamp(Instant serverTimestamp) {
        this.serverTimestamp = serverTimestamp;
    }
    
    public ChatMessage.MessageType getMessageType() {
        return messageType;
    }
    
    public void setMessageType(ChatMessage.MessageType messageType) {
        this.messageType = messageType;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
}
