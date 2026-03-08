package cs6650.assignment1.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * RoomManager maintains state for active rooms and their participants.
 * Thread-safe implementation using concurrent data structures.
 */
public class RoomManager {
    
    private static final Logger logger = LoggerFactory.getLogger(RoomManager.class);
    
    // Room ID -> Set of session IDs (users in that room)
    private final Map<String, Set<String>> roomSessions;
    
    // Session ID -> User info (userId, username)
    private final Map<String, UserInfo> activeUsers;
    
    // Statistics
    private long messagesProcessed = 0;
    
    public RoomManager() {
        this.roomSessions = new ConcurrentHashMap<>();
        this.activeUsers = new ConcurrentHashMap<>();
    }
    
    public void addUserToRoom(String roomId, String sessionId, String userId, String username) {
        roomSessions.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(sessionId);
        activeUsers.put(sessionId, new UserInfo(userId, username, roomId));
        logger.debug("User {} added to room {}", username, roomId);
    }
    
    public void removeUserFromRoom(String roomId, String sessionId) {
        Set<String> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                roomSessions.remove(roomId);
            }
        }
        UserInfo user = activeUsers.remove(sessionId);
        if (user != null) {
            logger.debug("User {} removed from room {}", user.username, roomId);
        }
    }
    
    public Set<String> getSessionsInRoom(String roomId) {
        return roomSessions.getOrDefault(roomId, Set.of());
    }
    
    public UserInfo getUserInfo(String sessionId) {
        return activeUsers.get(sessionId);
    }
    
    public int getActiveRoomCount() {
        return roomSessions.size();
    }
    
    public int getTotalActiveUsers() {
        return activeUsers.size();
    }
    
    public synchronized void incrementMessagesProcessed() {
        messagesProcessed++;
    }
    
    public long getMessagesProcessed() {
        return messagesProcessed;
    }
    
    public static class UserInfo {
        public final String userId;
        public final String username;
        public final String roomId;
        
        public UserInfo(String userId, String username, String roomId) {
            this.userId = userId;
            this.username = username;
            this.roomId = roomId;
        }
    }
}
