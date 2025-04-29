import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

public class Client {

    private long timeoutAfk = 60 * 1000; // Afk Timeout to avoid slow clients
    private long timeoutServer = 60 * 1000; // Max await time for server response
    private int port;
    private String address;
    private String authToken; //Similar to jwt token. The client service doesn't need to know the actual user information
    private Socket clientSocket;
    private final String FLAG = "::";

    public Client(int port, String address){
        this.port = port;
        this.address = address;
    }

    public void start() throws UnknownHostException, IOException{
        this.clientSocket = new Socket(address, port);
        System.out.println("Client started socket in address " + address + " and port " + port);
    }

    private String readResponseWithTimeout(BufferedReader in, long timeoutMillis) throws IOException {
        final ReentrantLock lock = new ReentrantLock();
        final Condition responseReceived = lock.newCondition();
        final StringBuilder result = new StringBuilder();
        Thread t = Thread.ofVirtual().start(() -> {
            
            try {
                String line = in.readLine();
                lock.lock();
                if (line != null) {
                    result.append(line);
                    responseReceived.signal(); // Notify the main thread
                }
                lock.unlock();
                
            } catch (IOException ignored) {}
            finally {
                
            }
        });

        lock.lock();
        try {
            boolean received = responseReceived.await(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!received) {
                throw new IOException("Timed out waiting for server response.");
            }
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while waiting for server response.");
        } finally {
            lock.unlock();
        }
        return result.toString();
    }

    private void sendMessage(String message, PrintWriter out) throws Exception{
        if(message.length() > 1024){
            throw new Exception("Message is too large!");
        }
        out.println(message);
    }

    private boolean waitForUserInput(BufferedReader reader, StringBuilder target, long timeoutMillis) throws IOException {
        final ReentrantLock lock = new ReentrantLock();
        final Condition inputAvailable = lock.newCondition();
        final boolean[] done = new boolean[]{false};

        Thread t = Thread.ofVirtual().start(() -> {
            try {
                String line = reader.readLine();
                lock.lock();
                try {
                    if (line != null) {
                        target.setLength(0);
                        target.append(line);
                    }
                    done[0] = true;
                    inputAvailable.signal(); // Notify the main thread
                } finally {
                    lock.unlock();
                }
            } catch (IOException ignored) {}
        });

        lock.lock();
        try {
            boolean gotInput = inputAvailable.await(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
            return done[0];
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while waiting for user input.");
        } finally {
            lock.unlock();
        }
    }

    private boolean handleAuthentication(BufferedReader in, BufferedReader userInput, PrintWriter out) throws Exception {
        while (true) {
            System.out.println(readResponseWithTimeout(in, timeoutServer));

            StringBuilder username = new StringBuilder();
            if (!waitForUserInput(userInput, username, timeoutAfk)) {
                System.out.println("Disconnected: Timed out waiting for username.");
                return false;
            }
            sendMessage(username.toString(), out);

            System.out.println(readResponseWithTimeout(in, timeoutServer));

            StringBuilder password = new StringBuilder();
            if (!waitForUserInput(userInput, password, timeoutAfk)) {
                System.out.println("Disconnected: Timed out waiting for password.");
                return false;
            }
            sendMessage(password.toString(), out);

            String message = readResponseWithTimeout(in, timeoutServer);
            String[] tokenInfo = message.split(":");
            if (tokenInfo.length == 2) {
                this.authToken = tokenInfo[1];
                System.out.println("Authentication successful!");
                return true;
            }

            System.out.print("Try again? (yes/no): ");
            StringBuilder retry = new StringBuilder();
            if (!waitForUserInput(userInput, retry, timeoutAfk)) {
                System.out.println("Disconnected: Timed out waiting for retry decision.");
                return false;
            }

            if (!retry.toString().equalsIgnoreCase("yes")) {
                return false;
            }
        }
    }

    private String readMultilineMessage(BufferedReader in) throws Exception {
        StringBuilder response = new StringBuilder();
            String line = "";
            try{
                line = readResponseWithTimeout(in, timeoutServer);
            } catch (Exception e){
                System.err.println(e.getMessage());
                clientSocket.close();
                throw new Exception("Could not connect to the server");
            }
            System.out.println(line);

            if(!line.equals(FLAG)){
                //is not a multiline
                
                return line;
            }

            //get the rest of the message if it
            while(true){
                try{
                    line = readResponseWithTimeout(in, timeoutServer);
                    System.out.println(line);
                } catch (Exception e){
                    System.err.println(e.getMessage());
                    clientSocket.close();
                    throw new Exception("Could not connect to the server");
                }
                if(line.equals(FLAG)){
                    break;
                }
                response.append(line).append('\n');
                
            }
        System.out.println(response.toString());
        return response.toString();
    }

    public void run() throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(this.clientSocket.getOutputStream(), true);
        BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));

        System.out.println(readResponseWithTimeout(in, timeoutServer));

        if (!handleAuthentication(in, userInput, out)) {
            clientSocket.close();
            return;
        }

        while (true) {
            System.out.print("Enter message (or 'exit' to quit): ");
            StringBuilder message = new StringBuilder();
            if (!waitForUserInput(userInput, message, timeoutAfk)) {
                System.out.println("Disconnected for being AFK.");
                break;
            }

            if (message.toString().equalsIgnoreCase("exit")) {
                break;
            }

            if(message.isEmpty()){
                message.append("next"); // 
            }

            sendMessage(authToken + ":" + message.toString(), out);

            String response = readMultilineMessage(in);

            if (response.toString().equals("SESSION_EXPIRED")) {
                System.out.println("Session expired. Please reauthenticate.");
                sendMessage("REAUTH", out);
                if (!handleAuthentication(in, userInput, out)) {
                    break;
                }
                continue;
            }

            System.out.println("Server: " + response);
        }

        clientSocket.close();
        System.out.println("Disconnected from server.");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Client...");

        if (args.length != 2) {
            System.out.println("Incorrect usage!");
            System.out.println("Usage:");
            System.out.println("java Client <port> <address>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String address = args[1];

        Client client = new Client(port, address);
        client.start();
        client.run();
    }
}
