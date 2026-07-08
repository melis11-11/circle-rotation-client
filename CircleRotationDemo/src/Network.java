import java.util.Scanner;

public abstract class Network {

    protected boolean running = false;
    protected Thread receiveThread;

    public abstract void connect();

    public abstract void sendMessage(String message);

    public abstract void receiveMessage();

    public abstract void disconnect();

    protected abstract String getClosedMessage();

    public void startReceiveThread() {
        receiveThread = new Thread(() -> receiveMessage());
        receiveThread.start();
    }

    public void startConsoleInput() {
        Scanner scanner = new Scanner(System.in);

        while (running) {
            String message = scanner.nextLine();

            if (message.equalsIgnoreCase("exit")) {
                sendMessage("exit");
                disconnect();
                System.out.println(getClosedMessage());
                break;
            }
            sendMessage(message);
        }

        scanner.close();
    }
}