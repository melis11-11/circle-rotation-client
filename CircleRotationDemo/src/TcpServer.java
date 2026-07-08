import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpServer extends Network {

    private static final int PORT = 5050;

    private ServerSocket serverSocket;
    private Socket clientSocket;

    private BufferedReader input;
    private PrintWriter output;

    public static void main(String[] args) {
        TcpServer server = new TcpServer();
        server.start();
    }

    public void start() {
        System.out.println("TCP Server started.");
        running = true;
        startConnectThread();
        startConsoleInput();
    }

    public void startConnectThread() {
        Thread connectThread = new Thread(() -> connect());
        connectThread.start();
    }

    @Override
    public void connect() {
        try {
            System.out.println("Opening server on port " + PORT);

            serverSocket = new ServerSocket(PORT);

            while (running) {
                if (!connected) {
                    System.out.println("Waiting for client...");
                    clientSocket = serverSocket.accept();
                    System.out.println("Client connected!");

                    input = new BufferedReader( new InputStreamReader(clientSocket.getInputStream()) );

                    output = new PrintWriter(clientSocket.getOutputStream(), true);

                    connected = true;

                    startReceiveThread();
                }

                Thread.sleep(1000);
            }

        } catch (IOException e) {
            if (running)
            System.out.println("Connection error: " + e.getMessage());
        } catch (InterruptedException e) {
            System.out.println("Connect thread interrupted.");
        }
    }

    @Override
    public void receiveMessage() {
        try {
            String message;

            while (connected && (message = input.readLine()) != null) {
                System.out.println("Client: " + message);
            }

            System.out.println("Client disconnected.");
            disconnect();

        } catch (IOException e) {
            if (connected) {
                System.out.println("Receive error: " + e.getMessage());
            }
            disconnect();
        }
    }

    @Override
    public void sendMessage(String message) {
        if (connected && output != null) {
            output.println(message);
        } else {
            System.out.println("No client connected. Message not sent.");
        }
    }

    @Override
    public void disconnect() {
        System.out.println("Disconnecting...");
        connected = false;

        try {
            if (input != null) {
                input.close();
            }

            if (output != null) {
                output.close();
            }

            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }

            if (!running && serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            System.out.println("Disconnected.");

        } catch (IOException e) {
            System.out.println("Error while disconnecting: " + e.getMessage());
        }
    }

    @Override
    protected String getClosedMessage() {
        return "Server closed.";
    }
}
