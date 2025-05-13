import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
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

    public static int getRoomIdByName(String name) throws Exception{
        int id = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader("chats.txt"))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] chatInfo = line.split(":");
                if(chatInfo.length != 2){
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

    public static Boolean createChat(String name) throws Exception{

        //verify if the chat exists
        int newID = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader("chats.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] chatInfo = line.split(":");
                if(chatInfo.length != 2){
                    throw new Error("Something wrong happened when parsing the chat file!");
               }

               if(chatInfo[1].equals(name)){
                //room already exists
                return false;
               }

               newID = Integer.parseInt(chatInfo[0]) + 1; //recalculate the new id for the chat room

            }
        } catch(Exception e){
            throw new Exception(e.getMessage());
        }

        try(PrintWriter writer = new PrintWriter(new FileWriter("chats.txt", true), true)){
            writer.println(newID + ":" + name);
        } catch (IOException e) {
            throw new Exception(e.getMessage()); 
        }


        return true;
    }
}
