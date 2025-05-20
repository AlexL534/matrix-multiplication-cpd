import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class Database {

    public static void save(
        Map<String, String> authUsers, ReentrantLock authUsersLock,
        Map<Integer, ChatService.ChatRoomInfo> chatRooms, ReentrantLock chatRoomsLock,
        Map<String, Integer> userRoom, ReentrantLock userRoomLock,
        Map<Integer, List<String>> roomsUsers, ReentrantLock roomsUsersLock,
        Map<Integer, List<String>> roomConversations, ReentrantLock conversationLock,
        String filename
    ) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {

            // ROOMS
            writer.println("[ROOMS]");
            chatRoomsLock.lock();
            try {
                for (Map.Entry<Integer, ChatService.ChatRoomInfo> entry : chatRooms.entrySet()) {
                    writer.println(entry.getKey() + ":" + entry.getValue().name + ":" + entry.getValue().isAIRoom  + ":" + 
                            entry.getValue().initialPrompt.replace("\n", "\\n"));
                }
            } finally { chatRoomsLock.unlock(); }

            // MESSAGES
            writer.println("\n[MESSAGES]");
            conversationLock.lock();
            try {
                for (Map.Entry<Integer, List<String>> entry : roomConversations.entrySet()) {
                    for (String msg : entry.getValue()) {
                        writer.println(entry.getKey() + ":" + msg);
                    }
                }
            } finally { conversationLock.unlock(); }

        } catch (IOException e) {
            System.err.println("Error saving database: " + e.getMessage());
        }
    }

    public static void load(
        Map<String, String> authUsers,
        Map<Integer, ChatService.ChatRoomInfo> chatRooms,
        Map<String, Integer> userRoom,
        Map<Integer, List<String>> roomsUsers,
        Map<Integer, List<String>> roomConversations,
        String filename
    ) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String section = "";
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("[")) {
                    section = line;
                    continue;
                }
                switch (section) {
                    case "[ROOMS]":
                        String[] roomParts = line.split(":", 4);
                        if (roomParts.length >= 3) {
                            int roomId = Integer.parseInt(roomParts[0]);
                            String roomName = roomParts[1];
                            boolean isAIRoom = Boolean.parseBoolean(roomParts[2]);
                            String initialPrompt = roomParts.length > 3 ? roomParts[3] : ""; // Get prompt if exists
                            
                            // Create ChatRoomInfo with all parameters including initialPrompt
                            chatRooms.put(roomId, new ChatService.ChatRoomInfo(
                                roomName, 
                                isAIRoom, 
                                initialPrompt.replace("\\n", "\n") // Unescape newlines
                            ));
                        }
                        break;
                    case "[MESSAGES]":
                        int idx = line.indexOf(":");
                        if (idx > 0) {
                            int roomId = Integer.parseInt(line.substring(0, idx));
                            String msg = line.substring(idx + 1);
                            roomConversations.computeIfAbsent(roomId, k -> new ArrayList<>()).add(msg);
                        }
                        break;
                }
            }
        } catch (IOException e) {
            System.err.println("No database file found, starting fresh.");
        }
    }
}