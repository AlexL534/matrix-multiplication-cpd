import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public class ChatService {
    
    public static Map<Integer,String> getAvailableChats() throws Exception{
        Map<Integer, String> idToName = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("chats.txt"))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] chatInfo = line.split(":");
                if(chatInfo.length != 2){
                    throw new Error("Something wrong happened when parsing the Chat file!");
                }
                Integer id = Integer.parseInt(chatInfo[0]);
                String name = chatInfo[1];
                idToName.put(id, name);
            }
        }
        return idToName;
    }
}
