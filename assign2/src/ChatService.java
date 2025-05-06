import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class ChatService {
    private static final Map<Integer, ChatRoomInfo> chatRooms = new HashMap<>();
    private static final ReentrantLock lock = new ReentrantLock();

    public static class ChatRoomInfo {
        public final String name;
        public final boolean isAIRoom;
        public final String prompt;

        public ChatRoomInfo(String name, boolean isAIRoom, String prompt) {
            this.name = name;
            this.isAIRoom = isAIRoom;
            this.prompt = prompt;
        }
    }
    
    public static Map<Integer, ChatRoomInfo> getAvailableChats() throws Exception{
        lock.lock();
        try {
            if (chatRooms.isEmpty()) {
                loadRoomsFromFile();
                //if file is empty, add some default rooms
                if (chatRooms.isEmpty()) {
                    chatRooms.put(1, new ChatRoomInfo("General", false, null));
                    chatRooms.put(2, new ChatRoomInfo("AI Help", true, "You are a helpful assistant. Answer questions concisely."));
                }
            }
            return new HashMap<>(chatRooms);
        } finally {
            lock.unlock();
        }
    }

    public static int createRoom(String name, boolean isAIRoom, String prompt) {
        lock.lock();
        try {
            int newId = chatRooms.isEmpty() ? 1 : Collections.max(chatRooms.keySet()) + 1;
            chatRooms.put(newId, new ChatRoomInfo(name, isAIRoom, prompt));
            saveRoomsToFile();
            return newId;
        } finally {
            lock.unlock();
        }
    }

    private static void loadRoomsFromFile() throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader("chats.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("::");
                if (parts.length >= 3) {
                    int id = Integer.parseInt(parts[0]);
                    String name = parts[1];
                    boolean isAI = Boolean.parseBoolean(parts[2]);
                    String prompt = parts.length > 3 ? parts[3] : null;
                    chatRooms.put(id, new ChatRoomInfo(name, isAI, prompt));
                }
            }
        }
    }

    private static void saveRoomsToFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter("chats.txt"))) {
            for (Map.Entry<Integer, ChatRoomInfo> entry : chatRooms.entrySet()) {
                writer.println(entry.getKey() + "::" + entry.getValue().name + "::" + 
                             entry.getValue().isAIRoom + "::" + entry.getValue().prompt);
            }
        } catch (IOException e) {
            System.err.println("Failed to save chat rooms: " + e.getMessage());
        }
    }
}
