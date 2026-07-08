import java.io.IOException;
import java.net.*;

public class UdpServer extends Network {

    private static final int PORT = 5000;

    private DatagramSocket socket;

    private InetAddress clientAddress;
    private int clientPort;

    public static void main(String[] args) {
        UdpServer server = new UdpServer();
        server.connect();
        server.startReceiveThread();
        server.startConsoleInput();
    }

    @Override
    public void connect() {
        try {
            socket = new DatagramSocket(PORT);

            running = true;

            System.out.println("UDP Server started on port " + PORT);
            System.out.println("Waiting for messages...");

        } catch (SocketException e) {
            System.out.println("Could not open UDP port: " + e.getMessage());
        }
    }

    @Override
    public void receiveMessage() {
        byte[] buffer = new byte[1024];

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());

                clientAddress = packet.getAddress();
                clientPort = packet.getPort();

                System.out.println("Client: " + message);

                if (message.equalsIgnoreCase("exit")) {
                    System.out.println("Client left.");
                    clientAddress = null;
                    clientPort = 0;
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
        if (clientAddress == null || clientPort == 0) {
            System.out.println("No client known yet. Wait until client sends first message.");
            return;
        }

        try {
            byte[] data = message.getBytes();

            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    clientAddress,
                    clientPort
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
        return "UDP Server closed.";
    }
}