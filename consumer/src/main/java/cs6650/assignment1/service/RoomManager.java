package cs6650.assignment1.service;

import cs6650.assignment1.model.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RoomManager {
    
    private static final Logger logger = LoggerFactory.getLogger(RoomManager.class);
    
    // Room ID -> Set of session IDs
    private final ConcurrentHashMap<String, Set<String>> roomSessions = new ConcurrentHashMap<>();
    
    // User ID -> UserInfo
    private final ConcurrentHashMap<String, UserInfo> activeUsers = new ConcurrentHashMap<>();
    
    // Metrics
    private final AtomicLong messagesProcessed = new AtomicLong(0);
    private final AtomicLong messagesFailed = new AtomicLong(0);
    
    public void addUserToRoom(String roomId, String userId, String username, String sessionId) {
        roomSessions.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(sessionId);
        
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setUsername(username);
        userInfo.setRoomId(roomId);
        userInfo.setSessionId(sessionId);
        userInfo.setLastSeen(System.currentTimeMillis());
        
        activeUsers.put(userId, userInfo);
        
        logger.info("Added user {} to room {}. Room now has {} users", 
                   userId, roomId, roomSessions.get(roomId).size());
    }
    
    public void removeUserFromRoom(String roomId, String userId) {
        UserInfo userInfo = activeUsers.remove(userId);
        if (userInfo != null) {
            Set<String> sessions = roomSessions.get(roomId);
            if (sessions != null) {
                sessions.remove(userInfo.getSessionId());
                if (sessions.isEmpty()) {
                    roomSessions.remove(roomId);
                }
            }
            logger.info("Removed user {} from room {}", userId, roomId);
        }
    }
    
    public Set<String> getSessionsInRoom(String roomId) {
        return roomSessions.getOrDefault(roomId, Set.of());
    }
    
    public int getRoomSize(String roomId) {
        Set<String> sessions = roomSessions.get(roomId);
        return sessions != null ? sessions.size() : 0;
    }
    
    public Map<String, Set<String>> getAllRooms() {
        return new ConcurrentHashMap<>(roomSessions);
    }
    
    public UserInfo getUserInfo(String userId) {
        return activeUsers.get(userId);
    }
    
    public void incrementMessagesProcessed() {
        messagesProcessed.incrementAndGet();
    }
    
    public void incrementMessagesFailed() {
        messagesFailed.incrementAndGet();
    }
    
    public long getMessagesProcessed() {
        return messagesProcessed.get();
    }
    
    public long getMessagesFailed() {
        return messagesFailed.get();
    }
    
    public int getTotalUsers() {
        return activeUsers.size();
    }
    
    public int getTotalRooms() {
        return roomSessions.size();
    }
}
