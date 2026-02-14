package cs6650.assignment1.client;

import cs6650.assignment1.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

public class MessageGenerator implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageGenerator.class);
    private static final int TOTAL_MESSAGES = 500_000;
    private static final String[] PREDEFINED_MESSAGES = {
        "Hello everyone!",
        "How are you doing today?",
        "Great to be here!",
        "Anyone working on the assignment?",
        "This is an amazing chat system!",
        "Looking forward to connecting with everyone.",
        "What's the weather like today?",
        "Happy coding!",
        "Let's collaborate on this project.",
        "Does anyone have questions?",
        "I'm excited about this course!",
        "Good morning team!",
        "See you all later!",
        "Thanks for the help!",
        "This is really helpful.",
        "Can we schedule a meeting?",
        "I agree with that point.",
        "That's a great idea!",
        "Let me think about it.",
        "I'll get back to you soon.",
        "Perfect timing!",
        "Absolutely!",
        "Sounds good to me.",
        "I appreciate your input.",
        "Well done everyone!",
        "Keep up the good work!",
        "This is interesting.",
        "I'm learning a lot here.",
        "Thanks for sharing!",
        "Have a wonderful day!",
        "Let's stay connected.",
        "I'm here if you need help.",
        "Great question!",
        "That makes sense.",
        "I understand now.",
        "Could you clarify?",
        "I'm curious about that.",
        "Let's discuss this further.",
        "I'm on it!",
        "No problem at all.",
        "You're welcome!",
        "My pleasure!",
        "Glad I could help.",
        "Anytime!",
        "Feel free to ask.",
        "I'm available.",
        "Let me know if you need anything.",
        "Happy to assist!",
        "Cheers!",
        "Best regards!"
    };
    
    private final BlockingQueue<ChatMessage> messageQueue;
    private final Random random;
    
    public MessageGenerator(BlockingQueue<ChatMessage> messageQueue) {
        this.messageQueue = messageQueue;
        this.random = new Random();
    }
    
    @Override
    public void run() {
        logger.info("Message generator started");
        
        try {
            for (int i = 0; i < TOTAL_MESSAGES; i++) {
                ChatMessage message = generateRandomMessage();
                messageQueue.put(message);
                
                if ((i + 1) % 50000 == 0) {
                    logger.info("Generated {} messages", i + 1);
                }
            }
            logger.info("Message generation completed. Total: {} messages", TOTAL_MESSAGES);
        } catch (InterruptedException e) {
            logger.error("Message generator interrupted", e);
            Thread.currentThread().interrupt();
        }
    }
    
    private ChatMessage generateRandomMessage() {
        int userId = random.nextInt(100000) + 1;
        String username = "user" + userId;
        String message = PREDEFINED_MESSAGES[random.nextInt(PREDEFINED_MESSAGES.length)];
        Instant timestamp = Instant.now();
        
        // 90% TEXT, 5% JOIN, 5% LEAVE
        ChatMessage.MessageType messageType;
        int typeRandom = random.nextInt(100);
        if (typeRandom < 90) {
            messageType = ChatMessage.MessageType.TEXT;
        } else if (typeRandom < 95) {
            messageType = ChatMessage.MessageType.JOIN;
            message = username + " joined the chat";
        } else {
            messageType = ChatMessage.MessageType.LEAVE;
            message = username + " left the chat";
        }
        
        return new ChatMessage(userId, username, message, timestamp, messageType);
    }
}
