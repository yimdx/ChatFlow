package cs6650.assignment1.controller;

import cs6650.assignment1.model.ChatMessage;
import cs6650.assignment1.model.ChatResponse;
import cs6650.assignment1.model.ErrorResponse;
import cs6650.assignment1.model.QueueMessage;
import cs6650.assignment1.service.MessagePublisherService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
    
    @Autowired
    private MessagePublisherService messagePublisher;
    
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
            
            // Get client IP address
            String clientIp = getClientIpAddress(headerAccessor);
            
            // Create queue message
            QueueMessage queueMessage = QueueMessage.fromChatMessage(
                chatMessage, 
                roomId, 
                messagePublisher.getServerId(),
                clientIp
            );
            
            // Publish message to RabbitMQ
            messagePublisher.publishMessage(queueMessage);
            
            // Send success acknowledgment back to sender
            ChatResponse response = new ChatResponse(chatMessage, "success");
            String sessionId = headerAccessor.getSessionId();
            messagingTemplate.convertAndSendToUser(
                sessionId, 
                "/queue/reply", 
                response
            );
            
            logger.info("Successfully published message {} for room {} from user {}", 
                       queueMessage.getMessageId(), roomId, chatMessage.getUsername());
            
        } catch (Exception e) {
            logger.error("Error processing message for room {}: {}", roomId, e.getMessage(), e);
            
            // Send error response
            List<String> errors = new ArrayList<>();
            errors.add("Failed to process message: " + e.getMessage());
            ErrorResponse errorResponse = new ErrorResponse(errors);
            
            String sessionId = headerAccessor.getSessionId();
            messagingTemplate.convertAndSendToUser(
                sessionId, 
                "/queue/errors", 
                errorResponse
            );
        }
    }
    
    private String getClientIpAddress(SimpMessageHeaderAccessor headerAccessor) {
        // Try to get IP from WebSocket session attributes
        Object ipAddress = headerAccessor.getSessionAttributes().get("clientIp");
        if (ipAddress != null) {
            return ipAddress.toString();
        }
        
        // Fallback to request context if available
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty()) {
                ip = request.getRemoteAddr();
            }
            return ip;
        }
        
        return "unknown";
    }
}
