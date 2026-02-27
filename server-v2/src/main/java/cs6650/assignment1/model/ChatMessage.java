package cs6650.assignment1.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;

import java.time.Instant;

public class ChatMessage {
    
    @NotNull(message = "userId is required")
    @Min(value = 1, message = "userId must be between 1 and 100000")
    @Max(value = 100000, message = "userId must be between 1 and 100000")
    private Integer userId;
    
    @NotBlank(message = "username is required")
    @Size(min = 3, max = 20, message = "username must be 3-20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "username must be alphanumeric")
    private String username;
    
    @NotBlank(message = "message is required")
    @Size(min = 1, max = 500, message = "message must be 1-500 characters")
    private String message;
    
    @NotNull(message = "timestamp is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;
    
    @NotNull(message = "messageType is required")
    private MessageType messageType;
    
    public enum MessageType {
        TEXT, JOIN, LEAVE
    }
    
    // Constructors
    public ChatMessage() {
    }
    
    public ChatMessage(Integer userId, String username, String message, Instant timestamp, MessageType messageType) {
        this.userId = userId;
        this.username = username;
        this.message = message;
        this.timestamp = timestamp;
        this.messageType = messageType;
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
