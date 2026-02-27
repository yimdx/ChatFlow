package cs6650.assignment1.controller;

import cs6650.assignment1.service.RoomManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {
    
    @Autowired
    private RoomManager roomManager;
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "message-consumer");
        response.put("messagesProcessed", roomManager.getMessagesProcessed());
        response.put("messagesFailed", roomManager.getMessagesFailed());
        response.put("activeRooms", roomManager.getTotalRooms());
        response.put("totalUsers", roomManager.getTotalUsers());
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        Map<String, Object> response = new HashMap<>();
        response.put("messagesProcessed", roomManager.getMessagesProcessed());
        response.put("messagesFailed", roomManager.getMessagesFailed());
        response.put("activeRooms", roomManager.getTotalRooms());
        response.put("totalUsers", roomManager.getTotalUsers());
        response.put("rooms", roomManager.getAllRooms().keySet());
        return ResponseEntity.ok(response);
    }
}
