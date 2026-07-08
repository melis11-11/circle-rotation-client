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
        client.startConnectThread();
        client.startConsoleInput();
    }

    public void startConnectThread() {
        Thread connectThread = new Thread(() -> {
            while (true) {
                if (!running) {
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

            running = true;

            System.out.println("Connected to server.");

            startReceiveThread();

        } catch (IOException e) {
            System.out.println("Server not available. Will try again...");
            running = false;
        }
    }

    @Override
    public void receiveMessage() {
        try {
            String message;

            while (running && (message = input.readLine()) != null) {
                System.out.println("Server: " + message);
            }

            System.out.println("Server disconnected.");
            disconnect();

        } catch (IOException e) {
            System.out.println("Connection lost.");
            disconnect();
        }
    }

    @Override
    public void sendMessage(String message) {
        if (running && output != null) {
            output.println(message);
        } else {
            System.out.println("Not connected. Message not sent.");
        }
    }

    @Override
    public void disconnect() {
        running = false;

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
