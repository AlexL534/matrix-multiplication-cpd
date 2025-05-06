import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Connection {
    private final String FLAG = "::";
    private long timeoutServer = 60 * 1000; // Max await time for server response


    public String readResponse(BufferedReader in) throws IOException{
        return in.readLine();
    }

    public void sendMessage(String message, PrintWriter out) throws Exception{
        if(message.length() > 1024){
            throw new Exception("Message is to large!");
        }
        out.println(message);
    }

    public String readResponseWithTimeout(BufferedReader in, long timeoutMillis) throws IOException {
        final ReentrantLock lock = new ReentrantLock();
        final Condition responseReceived = lock.newCondition();
        final StringBuilder result = new StringBuilder();

        final ReentrantLock lockIsResponsible = new ReentrantLock();
        final boolean[] isResponseReady = new boolean[]{false};

        Thread.ofVirtual().start(() -> {
            try {
                String line = in.readLine();
                lock.lock();
                try {
                    if (line != null) {
                        result.append(line);

                        lockIsResponsible.lock();
                        isResponseReady[0] = true; // update the shared flag
                        lockIsResponsible.unlock();

                        responseReceived.signal(); // notify the main thread
                    }
                } finally {
                    lock.unlock();
                    if(lockIsResponsible.isHeldByCurrentThread()){
                        lockIsResponsible.unlock();
                    }
                }
            } catch (IOException ignored) {}
        });

        lock.lock();
        try {
            while (!isResponseReady[0]) {
                boolean received = responseReceived.await(timeoutMillis, TimeUnit.MILLISECONDS);

                lockIsResponsible.lock();
                if (!received && !isResponseReady[0]) {
                    throw new IOException("Timed out waiting for server response.");
                }
                lockIsResponsible.unlock();

            }
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while waiting for server response.", e);
        } finally {
            lock.unlock();
            if(lockIsResponsible.isHeldByCurrentThread()){
                lockIsResponsible.unlock();
            }
        }
        return result.toString();
    }

    public String readMultilineMessage(BufferedReader in) throws Exception {
        StringBuilder response = new StringBuilder();
            String line = "";
            try{
                line = readResponseWithTimeout(in, timeoutServer);
            } catch (Exception e){
                System.err.println(e.getMessage());
                throw new Exception("Could not connect to the server");
            }

            if(!line.equals(FLAG)){
                //is not a multiline
                return line;
            }

            //get the rest of the message if it exists
            while(true){
                try{
                    line = readResponseWithTimeout(in, timeoutServer);
                } catch (Exception e){
                    System.err.println(e.getMessage());
                    throw new Exception("Could not connect to the server");
                }
                if(line.equals(FLAG)){
                    break;
                }
                response.append(line).append('\n');
                
            }
        return response.toString();
    }
}
