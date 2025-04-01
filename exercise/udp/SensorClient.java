import java.net.*;
import java.sql.Time;
import java.util.concurrent.TimeUnit;

public class SensorClient {
    public static void main(String[] args) {
        if (args.length < 4 || args.length > 5) {
            System.err.println("Usage: java SensorClient <addr> <port> <op> <id> [<val>]");
            return;
        }

        try {
            String addr = args[0];
            int port = Integer.parseInt(args[1]);
            String operation = args[2];
            int id = Integer.parseInt(args[3]);

            InetAddress serverAddress = InetAddress.getByName(addr);

            try(DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(3000); // Timeout of 3 seconds

                if (operation.equalsIgnoreCase("put") && args.length == 5) {
                    float value = Float.parseFloat(args[4]);

                    String message = "put " + id + " " + value;
                    sendMessage(socket, serverAddress, port, message);

                    System.out.println("Sent: " + message);
                    TimeUnit.SECONDS.sleep(1); // Sleep for 1 second
                }
                else if (operation.equalsIgnoreCase("get")) {
                    String message = "get " + id;
                    sendMessage(socket, serverAddress, port, message);

                    byte[] buffer = new byte[256];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                    try {
                        socket.receive(packet);
                        String response = new String(packet.getData(), 0, packet.getLength());
                        System.out.println("Received: " + response);
                    } 
                    catch (SocketTimeoutException e) {
                        System.out.println("No response from server (timeout).");
                    }
                }
                else {
                    System.err.println("Invalid operation. Use 'put' or 'get'.");
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendMessage(DatagramSocket socket, InetAddress serverAddress, int port, String message) throws Exception {
        byte[] buffer = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, port);
        socket.send(packet);
    }
}
