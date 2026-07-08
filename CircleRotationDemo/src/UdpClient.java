import java.io.IOException;
import java.net.*;

public class UdpClient extends Network {

    private static final int SERVER_PORT = 5000;
    private static final String SERVER_ADDRESS = "localhost";

    private DatagramSocket socket;
    private InetAddress serverAddress;

    public static void main(String[] args) {
        UdpClient client = new UdpClient();
        client.connect();
        client.startReceiveThread();
        client.startConsoleInput();
    }

    @Override
    public void connect() {
        try {
            socket = new DatagramSocket();
            serverAddress = InetAddress.getByName(SERVER_ADDRESS);

            running = true;

            System.out.println("UDP Client started.");
            System.out.println("Sending to server at " + SERVER_ADDRESS + ":" + SERVER_PORT);

        } catch (IOException e) {
            System.out.println("Could not start UDP client: " + e.getMessage());
        }
    }

    @Override
    public void receiveMessage() {
        //Create empty space for incoming data.
        byte[] buffer = new byte[1024];

        while (running) {
            try {
                //Connect that empty byte array to a packet object.
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                //Wait for a UDP packet. Java fills packet and puts the received bytes in it.
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());

                System.out.println("Server: " + message);

                if (message.equalsIgnoreCase("exit")) {
                    System.out.println("Server left.");
                    disconnect();
                }

            } catch (IOException e) {
                if (running) {
                    System.out.println("Receive error: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void sendMessage(String message) {
        try {
            byte[] data = message.getBytes();

            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    serverAddress,
                    SERVER_PORT
            );

            socket.send(packet);

        } catch (IOException e) {
            System.out.println("Send error: " + e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        running = false;

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    @Override
    protected String getClosedMessage() {
        return "UDP Client closed.";
    }
}