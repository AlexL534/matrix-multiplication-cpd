import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class Client {

    private int port;
    private String address;
    private String authToken; //Similar to jwt token. The client service doesn't need to know the actual user information
    private Socket clientSocket;

    public Client(int port, String address){
        this.port = port;
        this.address = address;
    }

    public void start() throws UnknownHostException, IOException{
        this.clientSocket = new Socket(address, port);
        System.out.println("Client started socket in address " + address + " and port " + port);
    }

    private String readResponse(BufferedReader in) throws IOException{
        return in.readLine();
    }

    private void sendMessage(String message, PrintWriter out) throws Exception{
        if(message.length() > 1024){
            throw new Exception("Message is to large!");
        }
        out.println(message);
    }

    private boolean handleAuthentication(BufferedReader in, BufferedReader userInput, PrintWriter out) throws Exception{
        //handles the authentication phase
        while(true){
            System.out.println(readResponse(in)); //username :
            String username = userInput.readLine();
            this.sendMessage(username, out);

            System.out.println(readResponse(in)); //password :
            String password = userInput.readLine();
            this.sendMessage(password, out);

            String message = readResponse(in);
            String[] tokenInfo = message.split(":");

            if (tokenInfo.length == 2){
                this.authToken = tokenInfo[1];
                System.out.println("Authentication successful!");
                return true;
            }

            //System.out.println(tokenInfo[0]);
            
            System.out.print("Try again? (yes/no): ");
            if (!userInput.readLine().equalsIgnoreCase("yes")) {
                return false; // User chose to quit
            }
        }
    }

    public void run() throws Exception{
        BufferedReader in = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(this.clientSocket.getOutputStream(), true);
        BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));

        //Welcome message
        System.out.println(readResponse(in));

        //Authentication
        if (!handleAuthentication(in, userInput, out)) {
            clientSocket.close();
            return;
        }

        //Message Loop
        while (true) {
            try {
                System.out.print("Enter message (or 'exit' to quit): ");
                String message = userInput.readLine();
    
                if(message.equals("exit")){
                    break;
                }
    
                sendMessage(authToken + ":" + message, out);
    
                String response = readResponse(in);
    
                if (response == null) {
                    System.out.println("\nServer disconnected unexpectedly.");
                    break;
                }

                if (response.equals("SESSION_EXPIRED")){
                    System.out.println("Session expired. Please reauthenticate.");
                    sendMessage("REAUTH", out);
                    if (!handleAuthentication(in, userInput, out)) {
                        break;
                    }
                    continue;
                }
    
                System.out.println("Server: " + response);
            }
            catch (IOException e) {
                System.out.println("\nConnection error: " + e.getMessage());
                break;
            }
        }

        clientSocket.close();
        System.out.println("Disconnected from server");
    }

    public static void main(String args[]) throws Exception{
        System.out.println("Starting Client...");

        if(args.length != 2){
            System.out.println("Incorrect usage!");
            System.out.println("Usage:");
            System.out.println("java Client <port> <address>");
        }

        //parse the arguments
        int port = Integer.parseInt(args[0]);
        String address = args[1];

        Client client = new Client(port, address);
        client.start();
        client.run();

    }
    
}
