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
    ReentrantLock roomsUsersLock;  //Used when accessing the rooms user map
    ReentrantLock authSocketLock;  //Used when accessing the auth socket map
    ReentrantLock chatServiceLock; //Used when accessing the chat service to create and fetch chats rooms

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
        chatServiceLock = new ReentrantLock();

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
        //special selections
        connection.sendMessage("a. Create a new room", out);

        connection.sendMessage("Enter room id to enter or letter to select a special option", out);
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

    private void handleRoomCreation(BufferedReader in, PrintWriter out, String token) throws Exception{
        while(true){
            connection.sendMessage("Please enter the chat name:", out);
            String response = connection.readResponse(in);
            String[] responseInfo = response.split(":");

            if(!verifyToken(out, token)){
                //invalid token. Needs reauth
                return;
            }

            boolean isCreated = false;
            int  id = 0;

            //create the chat and get it's id
            chatServiceLock.lock();
            try{
                isCreated =  ChatService.createChat(responseInfo[1]);
                id = ChatService.getRoomIdByName(responseInfo[1]);
            } catch(Exception e){
                throw new Exception(e);
            }
            finally{
                chatServiceLock.unlock();
            }

            //insert the new room into the chat rooms list
            chatRoomsLock.lock();
            try{
                chatRooms.put(id, responseInfo[1]);
            } catch(Exception e){
                throw new Exception(e);
            }finally{
                chatRoomsLock.unlock();
            }

            //insert the new room into the room users map
            roomsUsersLock.lock();
            try{
                roomsUsers.put(id, new ArrayList<>());
            } catch(Exception e){
                throw new Exception(e);
            }finally{
                roomsUsersLock.unlock();
            }

            if(isCreated){
                connection.sendMessage("New Chat room created successfully. Press Enter to continue", out);
                break;
            }

            connection.sendMessage("Name is already used. Press Enter to continue", out);
            response = connection.readResponse(in);
        }
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

    private boolean verifyToken(PrintWriter out, String token) throws Exception{
         // check if token is valid
            authUserslock.lock();
            try {
                if (!AuthService.isTokenValid(token) || !authUsers.containsKey(token)) {
                    connection.sendMessage("SESSION_EXPIRED", out);
                    authUserslock.lock();
                    try {
                        authUsers.remove(token);
                        return false;
                    } finally {
                        authUserslock.unlock();
                    }
                }
            }
            catch(Exception e){
                System.err.println(e.getMessage());
                connection.sendMessage("Internal Server Error!", out);
                return false;
            }
            finally {
                authUserslock.unlock();
            }
        return true;
    }

    private void refreshToken(PrintWriter out, String token) throws Exception{
        authLock.lock();
            try{
                AuthService.refreshToken(token);
            } catch(Exception e){
                System.err.println(e.getMessage());
                connection.sendMessage("Internal Server Error!", out);
            } finally{
                authLock.unlock();
            }
    }

    private void handleClients(Socket clientSocket) throws Exception{
        ClientState state = ClientState.RECONECT_MENU;//state of the client. Starts with the reconect menu


        System.out.println("Connected Client");

        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

        connection.sendMessage("Welcome to the CPD Chat server", out);

        String token = null;
        boolean isInRoom = false;
        try {
            //TODO: Reconect logic should be in another function. It should verify if the current state is reconect_menu
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
            else if (option.equals("2")) {
                connection.sendMessage("Token: ", out);
                token = connection.readResponse(in);

                boolean isValid = false;

                // Check if the token is valid
                authUserslock.lock();
                try {
                    if (authUsers.containsKey(token)) {
                        isValid = true;
                    }
                } finally {
                    authUserslock.unlock();
                }

                if (!isValid) {
                    connection.sendMessage("Token not found", out);
                    clientSocket.close();
                    in.close();
                    out.close();
                    return;
                }

                connection.sendMessage("Reconnected: " + authUsers.get(token), out);
                state = ClientState.CHATS_MENU;
            }
            else if(option.equals("1")){
                state = ClientState.AUTHENTICATE;
                token = this.authentication(in, out);
                state = ClientState.CHATS_MENU;
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
        Boolean isSendRooms = isInRoom ? false : true;
        
        while ((inputLine = connection.readResponse(in)) != null && state != ClientState.EXIT) {
            String[] parts = inputLine.split(":");
            if (parts.length < 2) {
                if (inputLine.equals("REAUTH")) {
                    try { // restart auth flow
                        state = ClientState.AUTHENTICATE;
                        this.authentication(in, out);
                        state = ClientState.CHATS_MENU;
                    }
                    catch (Exception e){
                      System.out.println(e.getMessage());
                      state = ClientState.EXIT;
                      break;
                    } 
                    continue;
                }
                connection.sendMessage("ERROR: Invalid format", out);
                continue;
            }

            token = parts[0];
            String message = parts[1];

            if(!verifyToken(out, token)){
                //the token is not valid. Continue to read the next message (possibly reauth) 
                continue;
            }

            //If the user is in a room, send the message to the other users. Else, send the rooms available to connect
            if(state == ClientState.CHAT){

                //send the message to the other
                Integer roomID = -1;
                userRoomLock.lock();
                try{
                    roomID = userRoom.get(token);
                }
                catch(Exception e){
                    state = ClientState.EXIT;
                    throw e;
                } finally{
                    userRoomLock.unlock();
                }

                if(message.equals("exitRoom")){
                    //the user wants to exit the room. Send disconnect message
                    sendDisconnectMessage(token);
                    disconnectUserFromRoom(token);
                    state = ClientState.CHATS_MENU;
                    isSendRooms = true;
                    connection.sendMessage("You exited the room successfully. Press enter to continue", out);
                    continue;
                }else{
                    //normal message
                    sendMessageToChat(message, roomID, token, false);
                }
            }
            else if(state == ClientState.CHATS_MENU){
                //show the available rooms
                try{
                    if(isSendRooms){
                        sendRoomSelection(out);
                        isSendRooms = false;
                    }
                    else{
                        //message could be a special option (are identified by a letter)
                        if(message.matches("[a-zA-Z]")){
                            
                            if(message.equals("a")){
                                //user selected the create room option
                                state = ClientState.CREATE_CHAT;
                                handleRoomCreation(in, out, token);
                                state = ClientState.CHATS_MENU;
                                isSendRooms = true; //need to send the rooms again
                            }

                        }
                        else{
                            //no special option is possible. Handle normal room selection
                            handleRoomSelection(out, token, message);
                            state = ClientState.CHAT;
                        }
                        
                        
                    }
                } catch(Exception e){
                    System.err.println(e.getMessage());
                    connection.sendMessage("Internal Server Error!", out);
                    state = ClientState.EXIT;
                    break;
                }
            }

            //the user sent a response so the token can be refreshed
            refreshToken(out, token);
            
        }

        //remove user from server before closing connection
        if(token != null){

            //first send the message to all the room users warn about the disconnection
            sendDisconnectMessage(token);

            disconnectUser(token); //disconnects the user from all the server's datastructures
        }

        clientSocket.close();
        in.close();
        out.close();

        System.out.println("Client Disconnected!");

    }

    private void sendDisconnectMessage(String token){
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
    }

    private void disconnectUserFromRoom(String token) throws Exception{
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
    }

    private void disconnectUser(String token) throws Exception{
        
            disconnectUserFromRoom(token);

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
