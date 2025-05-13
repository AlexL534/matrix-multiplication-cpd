import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class Server {

    private int port;
    private Map<Integer, String> chatRooms; //key = chat ID, value = chat name
    private Map<String, String> authUsers; //key = user token, value = username
    private Map<String, PrintWriter> authSocket; //key = user token, value =  socket to connect with the authenticated user
    private Map<Integer, List<String>> roomsUsers;  //key= room id, value = list of user(token) in the room
    private Map<String, Integer> userRoom; //key = user token, value = room id. Indicates the room where the user is now
    private ServerSocket serverSocket;
    private final String FLAG = "::";

    //Locks
    ReentrantLock authLock;         //Used when accessing the authentication service
    ReentrantLock authUserslock;    //Used when accessing the authUser map
    ReentrantLock chatRoomsLock;    //Used when accessing the chatRoom map
    ReentrantLock userRoomLock;    //Used when accessing the userRoom map
    ReentrantLock roomsUsersLock;
    ReentrantLock authSocketLock;

    private Connection connection;

    //constructor
    public Server(int port) throws Exception{
        this.port = port;
        //parse chat rooms included in a file
        chatRooms = ChatService.getAvailableChats();
        roomsUsers = new HashMap<>();
        for(Integer id : chatRooms.keySet()){
            roomsUsers.put(id, new ArrayList<>());
        }
        //initialize an empty auth user map. It will be filled when clients start to connect to the server
        authUsers = new HashMap<>();
        userRoom = new HashMap<>();
        authSocket = new HashMap<>();

        //initialize the locks
        authLock = new ReentrantLock();
        authUserslock = new ReentrantLock();
        chatRoomsLock = new ReentrantLock();
        userRoomLock = new ReentrantLock();
        roomsUsersLock = new ReentrantLock();
        authSocketLock = new ReentrantLock();

        connection = new Connection();
    }

    public void start() throws IOException{
        this.serverSocket = new ServerSocket(this.port);
        System.out.println("The Server is now listening to the port " + this.port); 
    }

    public void run() throws IOException{
        while (true){
            Socket clientSocket = this.serverSocket.accept();
            Thread.ofVirtual().start(() -> {
                try {
                    this.handleClients(clientSocket);
                } catch (Exception e) {
                    e.printStackTrace();
                }  
            });
        }
    }


    private String authentication(BufferedReader in, PrintWriter out) throws Exception{

            String token = null;
            while(true){
                connection.sendMessage("Username: ", out);
                String username = connection.readResponse(in);
                if (username == null) throw new Exception("No Username.");
                else username = username.trim();
                connection.sendMessage("Password: ", out);
                String password = connection.readResponse(in);
                if (password == null) throw new Exception("No Password.");
                else password = password.trim();

                //lock the authentication code to avoid concurrent access to the auth files
                authLock.lock();
                token = AuthService.signin(username, password);
                authLock.unlock();

                if(token != null){
                    //insert the user into the current online(authenticated) users
                    //lock the access to the auth users map to avoid concurrent access to the same sata structure
                    authUserslock.lock();
                    this.authUsers.put(token, username);
                    authUserslock.unlock();

                    //insert the mapping between the user token and its output stream used to send messages to him
                    authSocketLock.lock();
                    authSocket.put(token, out);
                    authSocketLock.unlock();
                    break;
                }
                
                connection.sendMessage("Bad credentials. Try Again", out);
            }
            connection.sendMessage("Token:" + token, out);
            out.flush();

            return token;
        
    }

    private void sendRoomSelection(PrintWriter out) throws Exception{
        //add a flag for a multiline message
        connection.sendMessage(FLAG, out);
        connection.sendMessage("Available Rooms:", out);
        for(Integer id : chatRooms.keySet()){
            connection.sendMessage(id.toString() +  ". " + chatRooms.get(id), out);
        }
        connection.sendMessage("Enter room id to enter", out);
        connection.sendMessage(FLAG, out);
    }

    private void handleRoomSelection(PrintWriter out, String token, String selected ) throws Exception{

        Integer selectedInteger = -1;
        try{
            selectedInteger = Integer.parseInt(selected);
        }
        catch(NumberFormatException e){
            connection.sendMessage("Please send a number!", out);
            return;
        }

        if(selectedInteger < 0 || selectedInteger > chatRooms.size()){
            connection.sendMessage("Invalid ID: " + selectedInteger.toString() , out);
            return;
        }

        connection.sendMessage("Room: " + chatRooms.get(selectedInteger), out);

        userRoomLock.lock();
        try{
            userRoom.put(token, selectedInteger);
        } catch(Exception e){
            throw new Exception(e.toString());
        }
        finally{
            userRoomLock.unlock();
        }

        roomsUsersLock.lock();
        try{
            List<String> users = roomsUsers.get(selectedInteger);
            if(users.add(token)){
                roomsUsers.replace(selectedInteger, users);
            }
            else{
                throw new Exception("Error inserting user into the room");
            }
        } catch(Exception e){
            throw new Exception(e.toString());
        }
        finally{
            roomsUsersLock.unlock();
        }

        sendMessageToChat(" joined the Room", selectedInteger, token, true);
    }

    private void sendMessageToChat(String message, Integer roomId, String token, boolean isInfoMessage){

        //username of the user that is sending the message
        authUserslock.lock();
        String username = authUsers.get(token);
        authUserslock.unlock();

        //send message to all the users in the chat
        for(String userToken : roomsUsers.get(roomId)){
            authSocketLock.lock();
            PrintWriter out = authSocket.get(userToken);
            authSocketLock.unlock();
            if(isInfoMessage){
                //user is conneted/disconnected message
                out.println(username + message);
            }else{
                out.println("[" + username +"]: " + message);
            }
           
        }
    }

    private void handleClients(Socket clientSocket) throws Exception{
        System.out.println("Connected Client");

        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

        connection.sendMessage("Welcome to the CPD Chat server", out);

        String token = null;
        boolean isReconnected = false;
        try {
            //start the authentication process
            connection.sendMessage("Options: ", out);
            connection.sendMessage("1. Authenticate", out);
            connection.sendMessage("2. Reconnect", out);
            connection.sendMessage("3. Exit", out);
            connection.sendMessage("Select an option: ", out); 

            String option = connection.readResponse(in);
            if (option.equals("3")) {
                connection.sendMessage("Bye!", out);
                clientSocket.close();
                in.close();
                out.close();
                return;
            }
            else if(option.equals("2")){
                connection.sendMessage("Token: ", out);
                token = connection.readResponse(in);
                for (String userToken : authUsers.keySet()){
                    if(userToken.equals(token)){
                        break;
                    }
                }
                connection.sendMessage("Reconnected: " + authUsers.get(token), out);
                isReconnected = true;
            }
            else if(option.equals("1")){
                token = this.authentication(in, out);
                System.out.println("Token auth: " + token);
            }
            else{
                connection.sendMessage("Invalid option", out);
                clientSocket.close();
                in.close();
                out.close();
                return;
            }
        }
        catch (Exception e){
            System.out.println(e.getMessage() + "\n" + "Client Disconnected.");
            clientSocket.close();
            in.close();
            out.close();
            return;
        } 
        //TODO: Connection to chat room AI

        String inputLine;
        Boolean isSendRooms = isReconnected ? false : true;
        
        while ((inputLine = connection.readResponse(in)) != null) {
            String[] parts = inputLine.split(":");
            if (parts.length < 2) {
                if (inputLine.equals("REAUTH")) {
                    try { // restart auth flow
                      this.authentication(in, out);
                    }
                    catch (Exception e){
                      System.out.println(e.getMessage());
                      break;
                    } 
                    continue;
                }
                connection.sendMessage("ERROR: Invalid format", out);
                continue;
            }

            token = parts[0];
            String message = parts[1];

            // check if token is valid
            authUserslock.lock();
            try {
                if (!AuthService.isTokenValid(token) || !authUsers.containsKey(token)) {
                    connection.sendMessage("SESSION_EXPIRED", out);
                    authUserslock.lock();
                    try {
                        authUsers.remove(token);
                    } finally {
                        authUserslock.unlock();
                    }
                    continue;
                }
            }
            catch(Exception e){
                System.err.println(e.getMessage());
                connection.sendMessage("Internal Server Error!", out);
                break;
            }
            finally {
                authUserslock.unlock();
            }

            //If the user is in a room, send the message to the other users. Else, send the rooms available to connect
            if(userRoom.containsKey(token)){
                //send the message to the other
                Integer roomID = -1;
                userRoomLock.lock();
                try{
                    roomID = userRoom.get(token);
                }
                catch(Exception e){
                    throw e;
                } finally{
                    userRoomLock.unlock();
                }
                sendMessageToChat(message, roomID, token, false);
            }
            else{
                //show the available rooms
                try{
                    if(isSendRooms){
                        sendRoomSelection(out);
                        isSendRooms = false;
                    }
                    else{
                        handleRoomSelection(out, token, message);
                    }
                } catch(Exception e){
                    System.err.println(e.getMessage());
                    connection.sendMessage("Internal Server Error!", out);
                    break;
                }
            }


            authLock.lock();
            try{
                AuthService.refreshToken(token);
            } catch(Exception e){
                System.err.println(e.getMessage());
                connection.sendMessage("Internal Server Error!", out);
                break;
            } finally{
                authLock.unlock();
            }
            
            
        }
        
        //remove user from server before closing connection
        if(token != null){

            //first send the message to all the room users warn about the disconnection
            Integer roomID = -1;
            userRoomLock.lock();
            try{
                if(userRoom.containsKey(token)){
                    roomID = userRoom.get(token);
                }
                    
            }
            catch(Exception e){
                throw e;
            } finally{
                userRoomLock.unlock();
            }

            if(!roomID.equals(-1)){
                sendMessageToChat(" left the room", roomID, token, true);
            }


            disconnectUser(token); //disconnects the user from all the server's datastructures
        }

        clientSocket.close();
        in.close();
        out.close();

        System.out.println("Client Disconnected!");

    }

    private void disconnectUser(String token) throws Exception{
        Integer roomId = null;

            //remove the user from the user rooms map
            userRoomLock.lock();
            try{
                if(userRoom.containsKey(token)){
                    roomId = userRoom.get(token);
                    userRoom.remove(token);
                }
            } catch(Exception e){
                throw new Exception(e.toString());
            }
            finally{
                userRoomLock.unlock();
            }

            //update the room where the user was
            if(roomId != null){
                roomsUsersLock.lock();
                try{
                    List<String> users = roomsUsers.get(roomId);
                    users.remove(token);
                    roomsUsers.replace(roomId, users);
                } catch(Exception e){
                    throw new Exception(e.toString());
                }
                finally{
                    roomsUsersLock.unlock();
                }
            }

            //remove the user from the socket mapping
            authSocketLock.lock();
            try{
                if(authSocket.containsKey(token)){
                    authSocket.remove(token);
                }
            }
            catch(Exception e){
                throw new Exception(e.toString());
            }finally{
                authSocketLock.unlock();
            }

            //remove the user from the authenticated users
            authUserslock.lock();
            try{
            authUsers.remove(token);
            } catch(Exception e){
                throw new Exception(e.toString());
            }
            finally{
                authUserslock.unlock();
            }
    }

    public static void main(String args[]){
        System.out.println("Starting server .....");

        if(args.length != 1){
            System.out.println("Incorrect usage!");
            System.out.println("Usage:");
            System.out.println("java Server <port>");
            return;
        }

        //parse the arguments
        int port = Integer.parseInt(args[0]);

        try{
            Server server = new Server(port);
            server.start();
            server.run();
        } catch(Exception e){
            System.out.println("Server catched an exception: " + e.toString());
        }

    }
    
}
