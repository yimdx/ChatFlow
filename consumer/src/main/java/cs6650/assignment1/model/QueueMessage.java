package cs6650.assignment1.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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
}
