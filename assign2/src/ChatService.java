import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class ChatService {
    private static final Map<Integer, ChatRoomInfo> chatRooms = new HashMap<>();
    private static final ReentrantLock lock = new ReentrantLock();

    public static class ChatRoomInfo {
        public final String name;
        public final boolean isAIRoom;
        public final String initialPrompt;

        public ChatRoomInfo(String name, boolean isAIRoom) {
            this(name, isAIRoom, "");
        }

            public ChatRoomInfo(String name, boolean isAIRoom, String initialPrompt) {
            this.name = name;
            this.isAIRoom = isAIRoom;
            this.initialPrompt = initialPrompt != null ? initialPrompt : "";
        }

        @Override
        public String toString() {
            return name + (isAIRoom ? " [AI]" : "");
        }

    }
    
    public static Map<Integer, ChatRoomInfo> getAvailableChats() throws Exception {
        lock.lock();
        try {
            if (chatRooms.isEmpty()) {
                loadRoomsFromFile();
            }

            Map<Integer, ChatRoomInfo> idToRoomInfo = new HashMap<>();
            for (Map.Entry<Integer, ChatRoomInfo> entry : chatRooms.entrySet()) {
                idToRoomInfo.put(entry.getKey(), entry.getValue());
            }

            return idToRoomInfo;

        } finally {
            lock.unlock();
        }
    }

    private static void loadRoomsFromFile() throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader("chats.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":", 4);
                if (parts.length < 3) {
                    throw new IllegalArgumentException("Invalid room format: " + line);
                }
                int id = Integer.parseInt(parts[0]);
                String name = parts[1];
                boolean isAI = Boolean.parseBoolean(parts[2]);
                String initialPrompt = parts.length > 3 ? parts[3] : "";
                chatRooms.put(id, new ChatRoomInfo(name, isAI, initialPrompt));
            }
        }
    }

    // not being used anywhere at the moment
    private static void saveRoomsToFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter("chats.txt"))) {
            for (Map.Entry<Integer, ChatRoomInfo> entry : chatRooms.entrySet()) {
                
                writer.println(entry.getKey() + ":" + entry.getValue().name + ":" + entry.getValue().isAIRoom + ":" + 
                entry.getValue().initialPrompt);
            }
        } catch (IOException e) {
            System.err.println("Failed to save chat rooms: " + e.getMessage());
        }
    }

    public static int getRoomIdByName(String name) throws Exception{
        int id = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader("chats.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] chatInfo = line.split(":");
                if(chatInfo.length != 4){
                    throw new Error("Something wrong happened when parsing the Chat file!");
                }
                int idFile = Integer.parseInt(chatInfo[0]);
                String nameFile = chatInfo[1];
                if(nameFile.equals(name)){
                    id = idFile;
                }
            }
        }
        if( id == 0){
            throw new Exception("Chat name not found!");
        }
        return id;
    }

    public static Boolean createChat(String name, boolean isAIRoom, String initialPrompt) throws Exception{
        int newID = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader("chats.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] chatInfo = line.split(":", 4);
                if(chatInfo.length < 3){
                    throw new Error("Something wrong happened when parsing the chat file!");
                }
                if(chatInfo[1].equals(name)){
                    //room already exists
                    return false;
                }
                int idFile = Integer.parseInt(chatInfo[0]);
                if (idFile >= newID) {
                    newID = idFile + 1;
                }
            }
        } catch(Exception e){
            throw new Exception(e.getMessage());
        }

        try(PrintWriter writer = new PrintWriter(new FileWriter("chats.txt", true), true)){
            writer.println(newID + ":" + name + ":" + isAIRoom  + ":" + 
            (initialPrompt != null ? initialPrompt : ""));
        } catch (IOException e) {
            throw new Exception(e.getMessage()); 
        }

        chatRooms.put(newID, new ChatRoomInfo(name, isAIRoom, initialPrompt));

        return true;
    }
}
