package cs6650.assignment1.controller;

import cs6650.assignment1.model.ChatMessage;
import cs6650.assignment1.model.ChatResponse;
import cs6650.assignment1.model.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Controller
public class ChatController {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    @Autowired
    private Validator validator;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MessageMapping("/chat/{roomId}")
    public void handleChatMessage(@DestinationVariable String roomId, 
                                   @Payload String messagePayload,
                                   SimpMessageHeaderAccessor headerAccessor) {
        try {
            logger.info("Received message for room {}: {}", roomId, messagePayload);
            
            // Parse the incoming JSON message
            ChatMessage chatMessage = objectMapper.readValue(messagePayload, ChatMessage.class);
            
            // Validate the message
            Set<ConstraintViolation<ChatMessage>> violations = validator.validate(chatMessage);
            
            if (!violations.isEmpty()) {
                // Collect validation errors
                List<String> errors = new ArrayList<>();
                for (ConstraintViolation<ChatMessage> violation : violations) {
                    errors.add(violation.getMessage());
                }
                
                // Send error response back to the sender
                ErrorResponse errorResponse = new ErrorResponse(errors);
                String sessionId = headerAccessor.getSessionId();
                messagingTemplate.convertAndSendToUser(
                    sessionId, 
                    "/queue/errors", 
                    errorResponse
                );
                logger.warn("Validation failed for room {}: {}", roomId, errors);
                return;
            }
            
            // Create success response
            ChatResponse response = new ChatResponse(chatMessage, "success");
            
            // Echo back to sender
            String sessionId = headerAccessor.getSessionId();
            messagingTemplate.convertAndSendToUser(
                sessionId, 
                "/queue/reply", 
                response
            );
            
            logger.info("Successfully processed message for room {} from user {}", 
                       roomId, chatMessage.getUsername());
            
        } catch (Exception e) {
            logger.error("Error processing message for room {}: {}", roomId, e.getMessage(), e);
            
            // Send error response
            List<String> errors = new ArrayList<>();
            errors.add("Invalid message format: " + e.getMessage());
            ErrorResponse errorResponse = new ErrorResponse(errors);
            
            String sessionId = headerAccessor.getSessionId();
            messagingTemplate.convertAndSendToUser(
                sessionId, 
                "/queue/errors", 
                errorResponse
            );
        }
    }
}
