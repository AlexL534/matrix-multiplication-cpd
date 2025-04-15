import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class Server {

    private int port;
    private Map<Integer, String> chatRooms; //key = chat ID, value = chat name
    private Map<String, String> authUsers; //key = user token, value = username
    private ServerSocket serverSocket;

    //constructor
    public Server(int port){
        this.port = port;
        //TODO: parse chat rooms included in a file
        //initialize an empty auth user map. It will be filled when clients start to connect to the server
        authUsers = new HashMap<>();
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
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }  
            });
        }
    }

    private void sendMessage(String message, PrintWriter out) throws Exception{
        if(message.length() > 1024){
            throw new Exception("Message is to large!");
        }
        System.out.println(message);
        out.println(message);
    }

    private void handleClients(Socket clientSocket) throws Exception{
        System.out.println("Connected Client");

        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

        //TODO: Authentication
        this.sendMessage("Welcome to the CPD Chat server", out);
        //TODO: Connection to chat room

        clientSocket.close();

    }

    public static void main(String args[]){
        System.out.println("Starting server .....");

        if(args.length != 1){
            System.out.println("Incorrect usage!");
            System.out.println("Usage:");
            System.out.println("java Server <port>");
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
