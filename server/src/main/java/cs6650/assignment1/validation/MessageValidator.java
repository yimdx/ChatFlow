package cs6650.assignment1.validation;

import cs6650.assignment1.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class MessageValidator {
    
    public static List<String> validate(ChatMessage message) {
        List<String> errors = new ArrayList<>();
        
        // Validate userId
        if (message.getUserId() == null) {
            errors.add("userId is required");
        } else if (message.getUserId() < 1 || message.getUserId() > 100000) {
            errors.add("userId must be between 1 and 100000");
        }
        
        // Validate username
        if (message.getUsername() == null || message.getUsername().isBlank()) {
            errors.add("username is required");
        } else if (message.getUsername().length() < 3 || message.getUsername().length() > 20) {
            errors.add("username must be 3-20 characters");
        } else if (!message.getUsername().matches("^[a-zA-Z0-9]+$")) {
            errors.add("username must be alphanumeric");
        }
        
        // Validate message
        if (message.getMessage() == null || message.getMessage().isBlank()) {
            errors.add("message is required");
        } else if (message.getMessage().length() < 1 || message.getMessage().length() > 500) {
            errors.add("message must be 1-500 characters");
        }
        
        // Validate timestamp
        if (message.getTimestamp() == null) {
            errors.add("timestamp is required");
        }
        
        // Validate messageType
        if (message.getMessageType() == null) {
            errors.add("messageType is required");
        }
        
        return errors;
    }
}
