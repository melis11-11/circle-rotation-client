import java.io.ByteArrayOutputStream;
import java.net.*;
import java.util.Arrays;
import java.util.Scanner;

public class UdpCircleClient {

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 50010;

    private static final int SYNC_1 = 0xA5;
    private static final int SYNC_2 = 0x5A;
    private static final int VERSION = 0x01;

    private static final int MSG_TYPE_COMMAND = 0x01;
    private static final int MSG_TYPE_ACK = 0x81;
    private static final int MSG_TYPE_NACK = 0x82;
    private static final int MSG_TYPE_STATUS = 0x83;

    private static final int CMD_START_ROTATION = 0x01;
    private static final int CMD_STOP_ROTATION = 0x02;
    private static final int CMD_GO_TO_ANGLE = 0x03;

    private static int sequenceNumber = 1;

    public static void main(String[] args) {
        System.out.println("UDP Circle Rotation Client");
        System.out.println("Commands:");
        System.out.println("  start");
        System.out.println("  stop");
        System.out.println("  goto <angle>");
        System.out.println("  exit");

        try (
                DatagramSocket socket = new DatagramSocket();
                Scanner scanner = new Scanner(System.in)
        ) {
            InetAddress serverAddress = InetAddress.getByName(SERVER_ADDRESS);
            socket.setSoTimeout(3000);

            System.out.println("Ready to send UDP frames to " + SERVER_ADDRESS + ":" + SERVER_PORT);

            Thread receiveThread = new Thread(() -> {
                byte[] buffer = new byte[2048];

                while (!socket.isClosed()) {
                    try {
                        DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
                        socket.receive(responsePacket);

                        byte[] response = Arrays.copyOf(
                                responsePacket.getData(),
                                responsePacket.getLength()
                        );

                        System.out.println("\nRX: " + toHex(response));
                        decodeResponse(response);
                        System.out.print("> ");

                    } catch (SocketTimeoutException e) {
                        // No problem. UDP may not receive anything immediately.
                    } catch (Exception e) {
                        if (!socket.isClosed()) {
                            System.out.println("\nReceive error: " + e.getMessage());
                        }
                    }
                }
            });

            receiveThread.setDaemon(true);
            receiveThread.start();

            while (true) {
                System.out.print("> ");
                String command = scanner.nextLine().trim();

                if (command.equalsIgnoreCase("exit")) {
                    System.out.println("Client closed.");
                    break;
                }

                byte[] frame = buildCommandFrame(command);

                if (frame == null) {
                    System.out.println("Invalid command. Use: start, stop, goto <angle>, exit");
                    continue;
                }

                System.out.println("TX: " + toHex(frame));

                DatagramPacket packet = new DatagramPacket(
                        frame,
                        frame.length,
                        serverAddress,
                        SERVER_PORT
                );

                socket.send(packet);
            }

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static byte[] buildCommandFrame(String command) {
        if (command.equalsIgnoreCase("start")) {
            return buildFrame(CMD_START_ROTATION, new byte[0]);
        }

        if (command.equalsIgnoreCase("stop")) {
            return buildFrame(CMD_STOP_ROTATION, new byte[0]);
        }

        if (command.toLowerCase().startsWith("goto")) {
            String[] parts = command.split("\\s+");

            if (parts.length != 2) {
                return null;
            }

            try {
                double angle = Double.parseDouble(parts[1]);

                if (angle < 0 || angle > 359.9) {
                    System.out.println("Angle must be between 0 and 359.9");
                    return null;
                }

                int angleX10 = (int) Math.round(angle * 10);

                byte[] payload = new byte[]{
                        (byte) ((angleX10 >> 8) & 0xFF),
                        (byte) (angleX10 & 0xFF)
                };

                return buildFrame(CMD_GO_TO_ANGLE, payload);

            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    private static byte[] buildFrame(int cmdId, byte[] payload) {
        int len = payload.length;
        int seq = sequenceNumber++;

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        out.write(SYNC_1);
        out.write(SYNC_2);
        out.write(VERSION);
        out.write(MSG_TYPE_COMMAND);
        out.write((seq >> 8) & 0xFF);
        out.write(seq & 0xFF);
        out.write(cmdId);
        out.write((len >> 8) & 0xFF);
        out.write(len & 0xFF);

        for (byte b : payload) {
            out.write(b & 0xFF);
        }

        byte[] frameWithoutCrc = out.toByteArray();

        int crc = crc16CcittFalse(frameWithoutCrc, 2, frameWithoutCrc.length - 2);

        out.write((crc >> 8) & 0xFF);
        out.write(crc & 0xFF);

        return out.toByteArray();
    }

    private static void decodeResponse(byte[] frame) {
        if (frame.length < 11) {
            System.out.println("Invalid response: too short");
            return;
        }

        int msgType = frame[3] & 0xFF;
        int seq = readUInt16(frame, 4);
        int cmdId = frame[6] & 0xFF;
        int len = readUInt16(frame, 7);

        if (msgType == MSG_TYPE_STATUS) {
            if (len != 3) {
                System.out.println("Unexpected STATUS length: " + len);
                return;
            }

            int currentAngleX10 = readUInt16(frame, 9);
            int rotatingValue = frame[11] & 0xFF;

            double currentAngle = currentAngleX10 / 10.0;
            boolean rotating = rotatingValue == 1;

            System.out.println("STATUS update");
            System.out.println("  Current angle: " + currentAngle + "°");
            System.out.println("  Rotating: " + rotating);
            return;
        }

        if (msgType == MSG_TYPE_ACK || msgType == MSG_TYPE_NACK) {
            if (len != 4) {
                System.out.println("Unexpected ACK/NACK length: " + len);
                return;
            }

            int statusOrError = frame[9] & 0xFF;
            int currentAngleX10 = readUInt16(frame, 10);
            int rotatingValue = frame[12] & 0xFF;

            double currentAngle = currentAngleX10 / 10.0;
            boolean rotating = rotatingValue == 1;

            if (msgType == MSG_TYPE_ACK) {
                System.out.println("ACK received");
                System.out.println("  Sequence: " + seq);
                System.out.println("  Command: " + commandName(cmdId));
                System.out.println("  Status: OK");
                System.out.println("  Current angle: " + currentAngle + "°");
                System.out.println("  Rotating: " + rotating);
            } else {
                System.out.println("NACK received");
                System.out.println("  Sequence: " + seq);
                System.out.println("  Command: " + commandName(cmdId));
                System.out.println("  Error code: " + statusOrError);
                System.out.println("  Current angle: " + currentAngle + "°");
                System.out.println("  Rotating: " + rotating);
            }

            return;
        }

        System.out.println("Unknown response type: 0x" + Integer.toHexString(msgType));
    }

    private static String commandName(int cmdId) {
        switch (cmdId) {
            case CMD_START_ROTATION:
                return "START_ROTATION";
            case CMD_STOP_ROTATION:
                return "STOP_ROTATION";
            case CMD_GO_TO_ANGLE:
                return "GO_TO_ANGLE";
            default:
                return "UNKNOWN_COMMAND";
        }
    }

    private static int readUInt16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static int crc16CcittFalse(byte[] data, int offset, int length) {
        int crc = 0xFFFF;

        for (int i = offset; i < offset + length; i++) {
            crc ^= (data[i] & 0xFF) << 8;

            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x8000) != 0) {
                    crc = ((crc << 1) ^ 0x1021) & 0xFFFF;
                } else {
                    crc = (crc << 1) & 0xFFFF;
                }
            }
        }

        return crc & 0xFFFF;
    }

    private static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder();

        for (byte b : data) {
            if (sb.length() > 0) {
                sb.append(" ");
            }

            sb.append(String.format("%02X", b & 0xFF));
        }

        return sb.toString();
    }
}