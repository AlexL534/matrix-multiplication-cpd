import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class Server {

    private int port;
    private Map<Integer, String> chatRooms; //key = chat ID, value = chat name
    private Map<String, String> authUsers; //key = user token, value = username
    private ServerSocket serverSocket;

    //Locks
    ReentrantLock authLock;         //Used when accessing the authentication service
    ReentrantLock authUserslock;    //Used when accessing the authUser map

    //constructor
    public Server(int port){
        this.port = port;
        //TODO: parse chat rooms included in a file
        //initialize an empty auth user map. It will be filled when clients start to connect to the server
        authUsers = new HashMap<>();

        //initialize the locks
        authLock = new ReentrantLock();
        authUserslock = new ReentrantLock();
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

    private void sendMessage(String message, PrintWriter out) throws Exception{
        if(message.length() > 1024){
            throw new Exception("Message is to large!");
        }
        //System.out.println(message);
        out.println(message);
    }
    private String readResponse(BufferedReader in) throws IOException{
        return in.readLine();
    }

    private void authentication(BufferedReader in, PrintWriter out) throws Exception{

            String token = null;
            while(true){
                this.sendMessage("Username: ", out);
                String username = readResponse(in);
                if (username == null) throw new Exception("No Username.");
                else username = username.trim();
                this.sendMessage("Password: ", out);
                String password = readResponse(in);
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
                    break;
                }
                
                this.sendMessage("Bad credentials. Try Again", out);
            }
            this.sendMessage("Token:" + token, out);
            out.flush();
        
    }

    private void handleClients(Socket clientSocket) throws Exception{
        System.out.println("Connected Client");

        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

        this.sendMessage("Welcome to the CPD Chat server", out);
        try { 
            this.authentication(in, out);
        }
        catch (Exception e){
            System.out.println(e.getMessage() + "\n" + "Client Disconnected.");
            clientSocket.close();
        } 
        //TODO: Connection to chat room

        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            String[] parts = inputLine.split(":");
            if (parts.length < 2) {
                if (inputLine.equals("REAUTH")) {
                    try { // restart auth flow
                      this.authentication(in, out);
                    }
                    catch (Exception e){
                      System.out.println(e.getMessage() + "\n" + "Client Disconnected.");
                      clientSocket.close();
                    } 
                    continue;
                }
                sendMessage("ERROR: Invalid format", out);
                continue;
            }

            String token = parts[0];
            String message = parts[1];

            // check if token is valid
            authUserslock.lock();
            try {
                if (!AuthService.isTokenValid(token) || !authUsers.containsKey(token)) {
                    sendMessage("SESSION_EXPIRED", out);
                    authUserslock.lock();
                    try {
                        authUsers.remove(token);
                    } finally {
                        authUserslock.unlock();
                    }
                    continue;
                }

                // if token is valid, process the message
                String username = authUsers.get(token);
                sendMessage("[" + username + "]: " + message, out);
                AuthService.refreshToken(token);
            }
            finally {
                authUserslock.unlock();
            }
        }

        clientSocket.close();

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

        Server server = new Server(port);
        try{
            server.start();
            server.run();
        } catch(IOException e){
            System.out.println("Server catched an exception: " + e.toString());
        }


    }
    
}
