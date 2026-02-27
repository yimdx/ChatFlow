package cs6650.assignment1.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueueMessage {
    
    private String messageId;
    private String roomId;
    private String userId;
    private String username;
    private String message;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;
    
    private MessageType messageType;
    private String serverId;
    private String clientIp;
    
    public enum MessageType {
        TEXT, JOIN, LEAVE
    }
    
    public static QueueMessage fromChatMessage(ChatMessage chatMessage, String roomId, String serverId, String clientIp) {
        QueueMessage queueMessage = new QueueMessage();
        queueMessage.setMessageId(UUID.randomUUID().toString());
        queueMessage.setRoomId(roomId);
        queueMessage.setUserId(chatMessage.getUserId().toString());
        queueMessage.setUsername(chatMessage.getUsername());
        queueMessage.setMessage(chatMessage.getMessage());
        queueMessage.setTimestamp(chatMessage.getTimestamp());
        queueMessage.setMessageType(MessageType.valueOf(chatMessage.getMessageType().name()));
        queueMessage.setServerId(serverId);
        queueMessage.setClientIp(clientIp);
        return queueMessage;
    }
}
