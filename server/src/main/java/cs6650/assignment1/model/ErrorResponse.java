package cs6650.assignment1.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

public class ErrorResponse {
    private String status;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;
    
    private List<String> errors;
    
    // Constructors
    public ErrorResponse() {
    }
    
    public ErrorResponse(String status, Instant timestamp, List<String> errors) {
        this.status = status;
        this.timestamp = timestamp;
        this.errors = errors;
    }
    
    public ErrorResponse(List<String> errors) {
        this.status = "error";
        this.timestamp = Instant.now();
        this.errors = errors;
    }
    
    // Getters and Setters
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
