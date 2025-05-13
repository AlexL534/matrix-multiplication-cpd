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
    private String authToken; //Similar to jwt token. The client service doesn't need to know the real user information
    private Socket clientSocket;
    private Connection connection;

    public Client(int port, String address){
        this.port = port;
        this.address = address;
        connection = new Connection();
    }

    public void start() throws UnknownHostException, IOException{
        this.clientSocket = new Socket(address, port);
        System.out.println("Client started socket in address " + address + " and port " + port);
    }

    
    private boolean waitForUserInput(BufferedReader reader, StringBuilder target, long timeoutMillis) throws IOException {
        final ReentrantLock lock = new ReentrantLock();
        final Condition inputAvailable = lock.newCondition();

        final ReentrantLock lockDone = new ReentrantLock();
        final ReentrantLock lockIsResponsible = new ReentrantLock();
        final boolean[] done = new boolean[]{false};
        final boolean[] isResponseReady = new boolean[]{false};

        Thread t = Thread.ofVirtual().start(() -> {
            try {
                String line = reader.readLine();
                lock.lock();
                try {
                    if (line != null) {
                        target.setLength(0);

                        //filters the : character as it is used as a flag and separator in the message protocol
                        if(line.contains(":")){
                            line = line.replaceAll(":", "");
                            System.out.println("The character \":\" was removed from your input.");
                        }
                        
                        target.append(line);

                        lockIsResponsible.lock();
                        isResponseReady[0] = true; // update the shared flag
                        lockIsResponsible.unlock();
                    }

                    lockDone.lock();
                    done[0] = true;
                    lockDone.unlock();

                    inputAvailable.signal(); // Notify the main thread
                } finally {
                    lock.unlock();
                    if(lockIsResponsible.isHeldByCurrentThread()){
                        lockIsResponsible.unlock();
                    }
                    if(lockDone.isHeldByCurrentThread()){
                        lockDone.unlock();
                    }
                }
            } catch (IOException ignored) {}
        });

        lock.lock();
        try {
            while (!isResponseReady[0]) {
                
                boolean gotInput = inputAvailable.await(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
                
                lockIsResponsible.lock();
                if (!gotInput && !isResponseReady[0]) {
                    t.interrupt();
                    throw new IOException("Timed out waiting for user input.");
                }
                lockIsResponsible.unlock();
            }

            lockDone.lock();
            boolean isDone = done[0];
            lockDone.unlock();

            return isDone;
            
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while waiting for user input.");
        } finally {
            lock.unlock();
            if(lockIsResponsible.isHeldByCurrentThread()){
                lockIsResponsible.unlock();
            }
            if(lockDone.isHeldByCurrentThread()){
                lockDone.unlock();
            }
        }
    }

    private boolean handleAuthentication(BufferedReader in, BufferedReader userInput, PrintWriter out) throws Exception {
        while (true) {
            System.out.println(connection.readResponseWithTimeout(in, timeoutServer));

            StringBuilder username = new StringBuilder();
            if (!waitForUserInput(userInput, username, timeoutAfk)) {
                System.out.println("Disconnected: Timed out waiting for username.");
                return false;
            }
            connection.sendMessage(username.toString(), out);

            System.out.println(connection.readResponseWithTimeout(in, timeoutServer));

            StringBuilder password = new StringBuilder();
            if (!waitForUserInput(userInput, password, timeoutAfk)) {
                System.out.println("Disconnected: Timed out waiting for password.");
                return false;
            }
            connection.sendMessage(password.toString(), out);

            String message = connection.readResponseWithTimeout(in, timeoutServer);
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


    public void run() throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(this.clientSocket.getOutputStream(), true);
        BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));

        //locks to help prevent concurrency when accessing the variables that help the two threads to syncronize
        final ReentrantLock lockRunnig = new ReentrantLock();
        final ReentrantLock lockReauth = new ReentrantLock();

        final boolean[] running = new boolean[]{true}; //used to control the execution of the message rection thread
        final boolean[] isReauth = new boolean[]{false}; //used to control the execution of the message rection thread

        try{
            //Welcome message
            System.out.println(connection.readResponseWithTimeout(in, timeoutServer));

            //Choices message
            System.out.println(connection.readResponseWithTimeout(in, timeoutServer));
            System.out.println(connection.readResponseWithTimeout(in, timeoutServer));
            System.out.println(connection.readResponseWithTimeout(in, timeoutServer));
            System.out.println(connection.readResponseWithTimeout(in, timeoutServer));
            System.out.println(connection.readResponseWithTimeout(in, timeoutServer));

            StringBuilder choice = new StringBuilder();
            if (!waitForUserInput(userInput, choice, timeoutAfk)) {
                System.out.println("Disconnected: Timed out waiting for username.");
                return;
            }

            connection.sendMessage(choice.toString(), out);

            if (choice.toString().equals("1")) {
                //authentication
                if (!handleAuthentication(in, userInput, out)) {
                    clientSocket.close();
                    return;
                }
            } else if (choice.toString().equals("2")) {
                System.out.println(connection.readResponseWithTimeout(in, timeoutServer));
                StringBuilder token = new StringBuilder();
                if (!waitForUserInput(userInput, token, timeoutAfk)) {
                    System.out.println("Disconnected: Timed out waiting for token.");
                    return;
                }
                connection.sendMessage(token.toString(), out);
                this.authToken = token.toString();
                //still need the reconnect of the room
                
            } else {
                clientSocket.close();
                System.out.println("Disconnecting...");
                return;
            }

            //usage instructions that appear before the room options
            System.out.println("\nUsage instructions:");
            System.out.println("    - Enter a message to interact with the server;");
            System.out.println("    - Enter 'exit' at any time to quit the server;");
            System.out.println("    - When some special input is asked, please use the provided instructions.");
            System.out.println("Press Enter to continue (if needed).\n");

            //thread that handles message reception
            Thread.ofVirtual().start(() -> {
                while (true) {
                    lockRunnig.lock();
                        if(!running[0]){
                            //thread does not need to run again
                            
                            lockRunnig.unlock();
                            break;
                        }
                    lockRunnig.unlock();
                    
                    try {
                        //Check if is reauth. Wait until the auth is finnished
                        lockReauth.lock();
                        if(isReauth[0]){
                            lockReauth.unlock();
                            continue;
                        }
                        lockReauth.unlock();

                        //read the server response. Supports multiline messages (start and end with a flag)
                        String response = connection.readMultilineMessage(in);

                        //session expired. Needs to reauth
                        lockReauth.lock();
                        if (response.toString().equals("SESSION_EXPIRED")) {
                            isReauth[0] = true; //activate the flag. The authentication will procede in the main thread  
                            continue;
                        }
                        lockReauth.unlock();

                        System.out.println("\nServer: " + response);

                    } catch (Exception e) {
                        lockRunnig.lock();
                        running[0] = false;
                        lockRunnig.unlock();
                        e.printStackTrace();
                        break;
                    }finally{
                        // always release the locks when a loop is completed to avoid blocked threads
                        if(lockRunnig.isHeldByCurrentThread())
                            lockRunnig.unlock();
                        if(lockReauth.isHeldByCurrentThread())
                            lockReauth.unlock();
                    }
                }
            });

            //main thread sends the messages
            while (true) {

                try{

                    lockRunnig.lock();
                        if(!running[0]){
                            //econdary thread is not running. Can exit the loop in the main thread
                            lockRunnig.unlock();
                            break;
                        }
                    lockRunnig.unlock();

                    lockReauth.lock();
                    if(isReauth[0]){
                        System.out.println("Session expired. Please reauthenticate.");
                        connection.sendMessage("REAUTH", out);
                        if (!handleAuthentication(in, userInput, out)) {
                            isReauth[0] = false;
                            lockReauth.unlock();

                            lockRunnig.lock();
                            running[0] = false; 
                            lockRunnig.unlock();

                            break;
                        }
                        isReauth[0] = false;
                    }
                    lockReauth.unlock();

                    StringBuilder message = new StringBuilder();

                    
                    //waits for the user input. Checks if the user is afk
                    if (!waitForUserInput(userInput, message, timeoutAfk)) {
                        System.out.println("Disconnected for being AFK.");

                        lockRunnig.lock();
                        running[0] = false; 
                        lockRunnig.unlock();

                        break;
                    }
                    //check if the message is to exit the server
                    if (message.toString().equalsIgnoreCase("exit")) {
                        lockRunnig.lock();
                        running[0] = false;            
                        lockRunnig.unlock();       
                        break;
                    }

                    if(message.isEmpty()){
                        message.append("next"); // Empty message. Send a dummy next message to procede (this usually happens when the user clicks enter)
                    }

                    connection.sendMessage(authToken + ":" + message.toString(), out);
                } catch(Exception e){
                    lockRunnig.lock();
                    running[0] = false; 
                    lockRunnig.unlock();

                    lockReauth.lock();
                    isReauth[0] = false;
                    lockReauth.unlock();


                    throw new Exception(e.getMessage());
                } finally{
                    // always release the locks when a loop is completed to avoid blocked threads
                    if(lockRunnig.isHeldByCurrentThread())
                        lockRunnig.unlock();
                    if(lockReauth.isHeldByCurrentThread())
                        lockReauth.unlock();
                }
            }
        }
        catch(Exception e){
            throw new Exception(e.getMessage());
        }finally{
            lockRunnig.lock();
            running[0] = false; 
            lockRunnig.unlock();

            lockReauth.lock();
            isReauth[0] = false;
            lockReauth.unlock();

            clientSocket.close();
            System.out.println("Disconnected from server.");
        }

        
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
