import java.net.*;
import java.util.*;

public class SensorServer {
    private static int port;
    private static Sensor[] sensors;
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java Server <port> <no_sensors>");
            return;
        }

        port = Integer.parseInt(args[0]);
        int noSensors = Integer.parseInt(args[1]);

        sensors = new Sensor[noSensors];

        for (int i = 0; i < noSensors; i++) {
            sensors[i] = new Sensor(i);
        }

        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("Server is running on port " + port);

            byte[] buffer = new byte[256];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                String response = processMessage(message);

                if (response != null) {
                    byte[] responseBytes = response.getBytes();
                    DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, packet.getAddress(), packet.getPort());
                    socket.send(responsePacket);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String processMessage(String message) {
        String[] parts = message.split(" ");
        String operation = parts[0];

        try {
            int sensorId = Integer.parseInt(parts[1]);

            if (sensorId < 0 || sensorId >= sensors.length) {
                return "Invalid sensor ID";
            }

            if (operation.equalsIgnoreCase("put")) {
                float value = Float.parseFloat(parts[2]);
                sensors[sensorId].addValue(value);
                return null;
            }
            
            else if (operation.equalsIgnoreCase("get")) {
                double average = sensors[sensorId].getAverage();
                return "Sensor " + sensorId + " average: " + average;
            }
            
            else {
                return "Invalid operation";
            }
        } catch (Exception e) {
            return "Error processing message";
        }
    }
}
