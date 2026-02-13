package cs6650.assignment1.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ChatResponse {
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("serverTimestamp")
    private String serverTimestamp;
    
    public ChatResponse() {
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getServerTimestamp() {
        return serverTimestamp;
    }
    
    public void setServerTimestamp(String serverTimestamp) {
        this.serverTimestamp = serverTimestamp;
    }
}
