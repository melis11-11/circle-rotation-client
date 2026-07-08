import java.io.*;
import java.net.Socket;

public class TcpClient extends Network {

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;

    private final String serverAddress = "localhost";
    private final int port = 5050;

    public static void main(String[] args) {
        TcpClient client = new TcpClient();
        client.running = true;
        client.startConnectThread();
        client.startConsoleInput();
    }

    public void startConnectThread() {
        Thread connectThread = new Thread(() -> {
            while (running) {
                if (!connected) {
                    connect();
                }

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    System.out.println("Connect thread interrupted.");
                }
            }
        });

        connectThread.start();
    }

    @Override
    public void connect() {
        try {
            System.out.println("Trying to connect to server...");

            socket = new Socket(serverAddress, port);

            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            output = new PrintWriter(socket.getOutputStream(), true);

            connected = true;

            System.out.println("Connected to server.");

            startReceiveThread();

        } catch (IOException e) {
            System.out.println("Server not available. Will try again...");
            connected = false;
        }
    }

    @Override
    public void receiveMessage() {
        try {
            String message;

            while (connected && (message = input.readLine()) != null) {
                System.out.println("Server: " + message);
            }

            System.out.println("Server disconnected.");
            disconnect();

        } catch (IOException e) {
            if (connected)
            System.out.println("Connection lost.");

            disconnect();
        }
    }

    @Override
    public void sendMessage(String message) {
        if (connected && output != null) {
            output.println(message);
        } else {
            System.out.println("Not connected. Message not sent.");
        }
    }

    @Override
    public void disconnect() {
        connected = false;

        try {
            if (input != null) {
                input.close();
            }

            if (output != null) {
                output.close();
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

        } catch (IOException e) {
            System.out.println("Error while disconnecting.");
        }
    }

    @Override
    protected String getClosedMessage() {
        return "Client closed.";
    }
}
