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
            // USERS
            writer.println("[USERS]");
            authUsersLock.lock();
            try {
                for (Map.Entry<String, String> entry : authUsers.entrySet()) {
                    writer.println(entry.getKey() + ":" + entry.getValue());
                }
            } finally { authUsersLock.unlock(); }

            // ROOMS
            writer.println("\n[ROOMS]");
            chatRoomsLock.lock();
            try {
                for (Map.Entry<Integer, ChatService.ChatRoomInfo> entry : chatRooms.entrySet()) {
                    writer.println(entry.getKey() + ":" + entry.getValue().roomName + ":" + entry.getValue().isAIRoom);
                }
            } finally { chatRoomsLock.unlock(); }

            // USERROOM
            writer.println("\n[USERROOM]");
            userRoomLock.lock();
            try {
                for (Map.Entry<String, Integer> entry : userRoom.entrySet()) {
                    writer.println(entry.getKey() + ":" + entry.getValue());
                }
            } finally { userRoomLock.unlock(); }

            // ROOMUSERS
            writer.println("\n[ROOMUSERS]");
            roomsUsersLock.lock();
            try {
                for (Map.Entry<Integer, List<String>> entry : roomsUsers.entrySet()) {
                    writer.println(entry.getKey() + ":" + String.join(",", entry.getValue()));
                }
            } finally { roomsUsersLock.unlock(); }

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
                    case "[USERS]":
                        String[] userParts = line.split(":", 2);
                        if (userParts.length == 2) authUsers.put(userParts[0], userParts[1]);
                        break;
                    case "[ROOMS]":
                        String[] roomParts = line.split(":", 3);
                        if (roomParts.length == 3)
                            chatRooms.put(Integer.parseInt(roomParts[0]), new ChatService.ChatRoomInfo(roomParts[1], Boolean.parseBoolean(roomParts[2])));
                        break;
                    case "[USERROOM]":
                        String[] urParts = line.split(":", 2);
                        if (urParts.length == 2) userRoom.put(urParts[0], Integer.parseInt(urParts[1]));
                        break;
                    case "[ROOMUSERS]":
                        String[] ruParts = line.split(":", 2);
                        if (ruParts.length == 2)
                            roomsUsers.put(Integer.parseInt(ruParts[0]), new ArrayList<>(Arrays.asList(ruParts[1].split(","))));
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