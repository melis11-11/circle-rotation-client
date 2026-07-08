import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.util.Arrays;

interface RotationCommandController {
    void startRotation();

    void stopRotation();

    void goToAngle(int angle);

    int getCurrentAngleX10();

    boolean isRotating();
}

public class CircleRotationDemo extends JFrame implements RotationCommandController {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            CircleRotationDemo app = new CircleRotationDemo();
            app.setVisible(true);
        });
    }

    private static final int SERVER_PORT = 50010;

    private final CirclePanel circlePanel;
    private final Timer rotationTimer;
    private final JSpinner angleSpinner;
    private final JLabel networkStatusLabel;

    private volatile double currentAngle = 0;
    private volatile boolean rotating = false;

    public CircleRotationDemo() {
        super("360 Degree Circle Rotation Demo - TCP/UDP Controlled");

        circlePanel = new CirclePanel();

        JButton startButton = new JButton("Start Rotation");
        JButton stopButton = new JButton("Stop Rotation");
        JButton goToButton = new JButton("Go To Angle");

        angleSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 359, 1));

        RotationCommandController commandController = this;

        startButton.addActionListener(e -> commandController.startRotation());
        stopButton.addActionListener(e -> commandController.stopRotation());

        goToButton.addActionListener(e -> {
            int angle = (int) angleSpinner.getValue();
            commandController.goToAngle(angle);
        });

        networkStatusLabel = new JLabel("TCP/UDP Server: starting...");
        networkStatusLabel.setForeground(new Color(80, 80, 80));

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        bottomPanel.add(startButton);
        bottomPanel.add(stopButton);
        bottomPanel.add(new JLabel("Angle:"));
        bottomPanel.add(angleSpinner);
        bottomPanel.add(goToButton);
        bottomPanel.add(networkStatusLabel);

        setLayout(new BorderLayout());
        add(circlePanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        rotationTimer = new Timer(40, e -> {
            if (rotating) {
                setAngleInternal(currentAngle + 1);
            }
        });

        rotationTimer.start();

        startProtocolServers();

        setSize(760, 780);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    private void startProtocolServers() {
        CircleProtocolParser parser = new CircleProtocolParser();
        CommandDispatcher dispatcher = new CommandDispatcher(this);

        Thread tcpThread = new Thread(new TcpProtocolServer(SERVER_PORT, parser, dispatcher), "circle-tcp-server");

        Thread udpThread = new Thread(new UdpProtocolServer(SERVER_PORT, parser, dispatcher), "circle-udp-server");

        tcpThread.setDaemon(true);
        udpThread.setDaemon(true);

        tcpThread.start();
        udpThread.start();

        networkStatusLabel.setText("TCP/UDP Server: port " + SERVER_PORT);
    }

    @Override
    public void startRotation() {
        runOnEdtAndWait(() -> {
            rotating = true;
            circlePanel.repaint();
        });
    }

    @Override
    public void stopRotation() {
        runOnEdtAndWait(() -> {
            rotating = false;
            circlePanel.repaint();
        });
    }

    @Override
    public void goToAngle(int angle) {
        runOnEdtAndWait(() -> {
            rotating = false;
            setAngleInternal(angle);
        });
    }

    @Override
    public int getCurrentAngleX10() {
        return (int) Math.round(normalizeAngle(currentAngle) * 10.0);
    }

    @Override
    public boolean isRotating() {
        return rotating;
    }

    private void runOnEdtAndWait(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
            return;
        }

        try {
            SwingUtilities.invokeAndWait(runnable);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException ex) {
            ex.printStackTrace();
        }
    }

    private void setAngleInternal(double angle) {
        currentAngle = normalizeAngle(angle);

        int spinnerAngle = (int) Math.round(currentAngle);
        if (spinnerAngle >= 360)
            spinnerAngle = 0;

        angleSpinner.setValue(spinnerAngle);
        circlePanel.repaint();
    }

    private double normalizeAngle(double angle) {
        double result = angle % 360;

        if (result < 0) {
            result += 360;
        }

        return result;
    }

    private class CirclePanel extends JPanel {

        public CirclePanel() {
            setBackground(new Color(18, 24, 38));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            int cx = width / 2;
            int cy = height / 2;

            int radius = Math.min(width, height) / 2 - 90;
            int labelRadius = radius + 32;

            drawTitle(g2, width);
            drawCircle(g2, cx, cy, radius);
            drawTicksAndLabels(g2, cx, cy, radius, labelRadius);
            drawArrow(g2, cx, cy, radius - 45);
            drawCenterInfo(g2, cx, cy);
            drawStatus(g2, width, height);

            g2.dispose();
        }

        private void drawTitle(Graphics2D g2, int width) {
            g2.setFont(new Font("Arial", Font.BOLD, 20));
            g2.setColor(new Color(232, 238, 247));

            String title = "360° Circle Angle Controller";
            FontMetrics fm = g2.getFontMetrics();

            g2.drawString(title, (width - fm.stringWidth(title)) / 2, 38);
        }

        private void drawCircle(Graphics2D g2, int cx, int cy, int radius) {
            g2.setStroke(new BasicStroke(3f));
            g2.setColor(new Color(56, 189, 248));

            Ellipse2D circle = new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2);

            g2.draw(circle);
        }

        private void drawTicksAndLabels(Graphics2D g2, int cx, int cy, int radius, int labelRadius) {
            for (int degree = 0; degree < 360; degree += 10) {
                double rad = toScreenRadians(degree);

                boolean major = degree % 30 == 0;

                int tickInnerRadius = major ? radius - 16 : radius - 8;

                int x1 = cx + (int) Math.round(Math.cos(rad) * tickInnerRadius);
                int y1 = cy + (int) Math.round(Math.sin(rad) * tickInnerRadius);

                int x2 = cx + (int) Math.round(Math.cos(rad) * radius);
                int y2 = cy + (int) Math.round(Math.sin(rad) * radius);

                g2.setStroke(new BasicStroke(major ? 2.4f : 1.2f));
                g2.setColor(major ? new Color(232, 238, 247) : new Color(130, 150, 175));

                g2.draw(new Line2D.Double(x1, y1, x2, y2));

                String text = String.valueOf(degree);

                g2.setFont(new Font("Arial", major ? Font.BOLD : Font.PLAIN, major ? 13 : 11));

                FontMetrics fm = g2.getFontMetrics();

                int tx = cx + (int) Math.round(Math.cos(rad) * labelRadius) - fm.stringWidth(text) / 2;

                int ty = cy + (int) Math.round(Math.sin(rad) * labelRadius) + fm.getAscent() / 2 - 2;

                g2.setColor(major ? new Color(248, 250, 252) : new Color(159, 176, 198));

                g2.drawString(text, tx, ty);
            }
        }

        private void drawArrow(Graphics2D g2, int cx, int cy, int arrowLength) {
            double rad = toScreenRadians(currentAngle);

            int endX = cx + (int) Math.round(Math.cos(rad) * arrowLength);
            int endY = cy + (int) Math.round(Math.sin(rad) * arrowLength);

            g2.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            g2.setColor(new Color(52, 211, 153));
            g2.drawLine(cx, cy, endX, endY);

            drawArrowHead(g2, endX, endY, rad);
        }

        private void drawArrowHead(Graphics2D g2, int endX, int endY, double rad) {
            int size = 18;

            double backAngle1 = rad + Math.toRadians(150);
            double backAngle2 = rad - Math.toRadians(150);

            int x1 = endX + (int) Math.round(Math.cos(backAngle1) * size);
            int y1 = endY + (int) Math.round(Math.sin(backAngle1) * size);

            int x2 = endX + (int) Math.round(Math.cos(backAngle2) * size);
            int y2 = endY + (int) Math.round(Math.sin(backAngle2) * size);

            Polygon arrowHead = new Polygon();
            arrowHead.addPoint(endX, endY);
            arrowHead.addPoint(x1, y1);
            arrowHead.addPoint(x2, y2);

            g2.setColor(new Color(52, 211, 153));
            g2.fillPolygon(arrowHead);
        }

        private void drawCenterInfo(Graphics2D g2, int cx, int cy) {
            g2.setColor(new Color(12, 19, 34));
            g2.fillOval(cx - 56, cy - 56, 112, 112);

            g2.setColor(new Color(56, 189, 248));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(cx - 56, cy - 56, 112, 112);

            g2.setFont(new Font("Arial", Font.BOLD, 22));
            g2.setColor(new Color(232, 238, 247));

            String angleText = String.format("%.0f°", currentAngle);
            FontMetrics fm = g2.getFontMetrics();

            g2.drawString(angleText, cx - fm.stringWidth(angleText) / 2, cy + 8);
        }

        private void drawStatus(Graphics2D g2, int width, int height) {
            String status = rotating ? "Status: ROTATING" : "Status: STOPPED";

            g2.setFont(new Font("Arial", Font.BOLD, 14));
            g2.setColor(rotating ? new Color(52, 211, 153) : new Color(248, 113, 113));

            FontMetrics fm = g2.getFontMetrics();

            g2.drawString(status, (width - fm.stringWidth(status)) / 2, height - 35);
        }

        private double toScreenRadians(double degree) {
            return Math.toRadians(degree - 90);
        }
    }

    static final class Protocol {
        static final int SYNC_1 = 0xA5;
        static final int SYNC_2 = 0x5A;

        static final int VERSION = 0x01;

        static final int MSG_TYPE_COMMAND = 0x01;
        static final int MSG_TYPE_ACK = 0x81;
        static final int MSG_TYPE_NACK = 0x82;
        static final int MSG_TYPE_STATUS = 0x83;

        static final int CMD_NONE = 0x00;
        static final int CMD_START_ROTATION = 0x01;
        static final int CMD_STOP_ROTATION = 0x02;
        static final int CMD_GO_TO_ANGLE = 0x03;

        static final int ERR_OK = 0x00;
        static final int ERR_INVALID_SYNC = 0x01;
        static final int ERR_INVALID_VERSION = 0x02;
        static final int ERR_INVALID_MSG_TYPE = 0x03;
        static final int ERR_INVALID_CMD = 0x04;
        static final int ERR_INVALID_LEN = 0x05;
        static final int ERR_INVALID_CRC = 0x06;
        static final int ERR_INVALID_ANGLE = 0x07;

        static final int MIN_FRAME_SIZE = 11;
    }

    static final class ParserResult {
        final boolean ok;
        final int errorCode;
        final String errorText;

        final int version;
        final int msgType;
        final int seq;
        final int cmdId;
        final int len;
        final byte[] payload;
        final int angleX10;

        private ParserResult(boolean ok, int errorCode, String errorText, int version, int msgType, int seq, int cmdId,
                             int len, byte[] payload, int angleX10) {
            this.ok = ok;
            this.errorCode = errorCode;
            this.errorText = errorText;
            this.version = version;
            this.msgType = msgType;
            this.seq = seq;
            this.cmdId = cmdId;
            this.len = len;
            this.payload = payload;
            this.angleX10 = angleX10;
        }

        static ParserResult ok(int version, int msgType, int seq, int cmdId, int len, byte[] payload, int angleX10) {
            return new ParserResult(true, Protocol.ERR_OK, "OK", version, msgType, seq, cmdId, len, payload, angleX10);
        }

        static ParserResult fail(int errorCode, String errorText, int version, int msgType, int seq, int cmdId) {
            return new ParserResult(false, errorCode, errorText, version, msgType, seq, cmdId, 0, new byte[0], 0);
        }
    }

    static final class CircleProtocolParser {

        ParserResult parse(byte[] frame) {
            if (frame == null || frame.length < Protocol.MIN_FRAME_SIZE) {
                return ParserResult.fail(Protocol.ERR_INVALID_LEN, "Frame too short", 0, 0, 0, Protocol.CMD_NONE);
            }

            if ((frame[0] & 0xFF) != Protocol.SYNC_1 || (frame[1] & 0xFF) != Protocol.SYNC_2) {
                return ParserResult.fail(Protocol.ERR_INVALID_SYNC, "Invalid sync", 0, 0, 0, Protocol.CMD_NONE);
            }

            int version = frame[2] & 0xFF;
            int msgType = frame[3] & 0xFF;
            int seq = readUInt16(frame, 4);
            int cmdId = frame[6] & 0xFF;
            int len = readUInt16(frame, 7);

            int expectedFrameLength = 9 + len + 2;

            if (frame.length != expectedFrameLength) {
                return ParserResult.fail(Protocol.ERR_INVALID_LEN,
                        "Invalid frame length. Expected=" + expectedFrameLength + ", actual=" + frame.length, version,
                        msgType, seq, cmdId);
            }

            if (version != Protocol.VERSION) {
                return ParserResult.fail(Protocol.ERR_INVALID_VERSION, "Invalid protocol version", version, msgType,
                        seq, cmdId);
            }

            if (msgType != Protocol.MSG_TYPE_COMMAND) {
                return ParserResult.fail(Protocol.ERR_INVALID_MSG_TYPE, "Only COMMAND message accepted", version,
                        msgType, seq, cmdId);
            }

            int receivedCrc = readUInt16(frame, 9 + len);
            int calculatedCrc = crc16CcittFalse(frame, 2, 7 + len);

            if (receivedCrc != calculatedCrc) {
                return ParserResult.fail(Protocol.ERR_INVALID_CRC,
                        String.format("Invalid CRC. received=0x%04X calculated=0x%04X", receivedCrc, calculatedCrc),
                        version, msgType, seq, cmdId);
            }

            byte[] payload = Arrays.copyOfRange(frame, 9, 9 + len);

            switch (cmdId) {
                case Protocol.CMD_START_ROTATION:
                    if (len != 0) {
                        return ParserResult.fail(Protocol.ERR_INVALID_LEN, "START_ROTATION len must be 0", version, msgType,
                                seq, cmdId);
                    }

                    return ParserResult.ok(version, msgType, seq, cmdId, len, payload, 0);

                case Protocol.CMD_STOP_ROTATION:
                    if (len != 0) {
                        return ParserResult.fail(Protocol.ERR_INVALID_LEN, "STOP_ROTATION len must be 0", version, msgType,
                                seq, cmdId);
                    }

                    return ParserResult.ok(version, msgType, seq, cmdId, len, payload, 0);

                case Protocol.CMD_GO_TO_ANGLE:
                    if (len != 2) {
                        return ParserResult.fail(Protocol.ERR_INVALID_LEN, "GO_TO_ANGLE len must be 2", version, msgType,
                                seq, cmdId);
                    }

                    int angleX10 = readUInt16(payload, 0);

                    if (angleX10 > 3599) {
                        return ParserResult.fail(Protocol.ERR_INVALID_ANGLE, "Angle must be 0..3599", version, msgType, seq,
                                cmdId);
                    }

                    return ParserResult.ok(version, msgType, seq, cmdId, len, payload, angleX10);

                default:
                    return ParserResult.fail(Protocol.ERR_INVALID_CMD, "Unknown command", version, msgType, seq, cmdId);
            }
        }
    }

    static final class CommandDispatcher {
        final RotationCommandController controller;

        CommandDispatcher(RotationCommandController controller) {
            this.controller = controller;
        }

        synchronized byte[] dispatch(ParserResult result) {
            if (!result.ok) {
                System.out.println("[NACK] " + result.errorText);

                return ProtocolFrameBuilder.buildNack(result.seq, result.cmdId, result.errorCode,
                        controller.getCurrentAngleX10(), controller.isRotating());
            }

            switch (result.cmdId) {
                case Protocol.CMD_START_ROTATION:
                    System.out.println("[COMMAND] START_ROTATION");
                    controller.startRotation();
                    break;

                case Protocol.CMD_STOP_ROTATION:
                    System.out.println("[COMMAND] STOP_ROTATION");
                    controller.stopRotation();
                    break;

                case Protocol.CMD_GO_TO_ANGLE:
                    int angle = result.angleX10 / 10;

                    System.out.println(
                            "[COMMAND] GO_TO_ANGLE angleX10=" + result.angleX10 + " angle=" + (result.angleX10 / 10.0));

                    controller.goToAngle(angle);
                    break;

                default:
                    return ProtocolFrameBuilder.buildNack(result.seq, result.cmdId, Protocol.ERR_INVALID_CMD,
                            controller.getCurrentAngleX10(), controller.isRotating());
            }

            return ProtocolFrameBuilder.buildAck(result.seq, result.cmdId, controller.getCurrentAngleX10(),
                    controller.isRotating());
        }
    }

    static final class ProtocolFrameBuilder {

        static byte[] buildAck(int seq, int cmdId, int currentAngleX10, boolean rotating) {
            byte[] payload = new byte[] { 0x00, (byte) ((currentAngleX10 >> 8) & 0xFF), (byte) (currentAngleX10 & 0xFF),
                    (byte) (rotating ? 1 : 0) };

            return buildFrame(Protocol.MSG_TYPE_ACK, seq, cmdId, payload);
        }

        static byte[] buildNack(int seq, int cmdId, int errorCode, int currentAngleX10, boolean rotating) {
            byte[] payload = new byte[] { (byte) (errorCode & 0xFF), (byte) ((currentAngleX10 >> 8) & 0xFF),
                    (byte) (currentAngleX10 & 0xFF), (byte) (rotating ? 1 : 0) };

            return buildFrame(Protocol.MSG_TYPE_NACK, seq, cmdId, payload);
        }

        static byte[] buildFrame(int msgType, int seq, int cmdId, byte[] payload) {
            if (payload == null) {
                payload = new byte[0];
            }

            int len = payload.length;

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            out.write(Protocol.SYNC_1);
            out.write(Protocol.SYNC_2);
            out.write(Protocol.VERSION);
            out.write(msgType & 0xFF);
            out.write((seq >> 8) & 0xFF);
            out.write(seq & 0xFF);
            out.write(cmdId & 0xFF);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);

            for (byte b : payload) {
                out.write(b & 0xFF);
            }

            byte[] withoutCrc = out.toByteArray();

            int crc = crc16CcittFalse(withoutCrc, 2, withoutCrc.length - 2);

            out.write((crc >> 8) & 0xFF);
            out.write(crc & 0xFF);

            return out.toByteArray();
        }

        static byte[] buildStatus(int currentAngleX10, boolean rotating) {
            byte[] payload = new byte[] {
                    (byte) ((currentAngleX10 >> 8) & 0xFF),
                    (byte) (currentAngleX10 & 0xFF),
                    (byte) (rotating ? 1 : 0)
            };

            return buildFrame(Protocol.MSG_TYPE_STATUS, 0, Protocol.CMD_NONE, payload);
        }
    }

    static final class TcpProtocolServer implements Runnable {
        private final int port;
        private final CircleProtocolParser parser;
        private final CommandDispatcher dispatcher;

        TcpProtocolServer(int port, CircleProtocolParser parser, CommandDispatcher dispatcher) {
            this.port = port;
            this.parser = parser;
            this.dispatcher = dispatcher;
        }

        @Override
        public void run() {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("[TCP] Listening on port " + port);

                while (true) {
                    Socket socket = serverSocket.accept();

                    Thread clientThread = new Thread(() -> handleClient(socket), "circle-tcp-client");

                    clientThread.setDaemon(true);
                    clientThread.start();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        private void handleClient(Socket socket) {
            System.out.println("[TCP] Client connected: " + socket.getRemoteSocketAddress());

            try (Socket s = socket; InputStream in = s.getInputStream(); OutputStream out = s.getOutputStream()) {

                Thread statusThread = new Thread(() -> {
                    try {
                        while (!s.isClosed()) {
                            byte[] statusFrame = ProtocolFrameBuilder.buildStatus(
                                    dispatcher.controller.getCurrentAngleX10(),
                                    dispatcher.controller.isRotating()
                            );

                            synchronized (out) {
                                out.write(statusFrame);
                                out.flush();
                            }

                            System.out.println("[TCP STATUS TX] " + toHex(statusFrame));

                            Thread.sleep(2000);
                        }
                    } catch (Exception e) {
                        System.out.println("[TCP STATUS] stopped");
                    }
                });

                statusThread.setDaemon(true);
                statusThread.start();

                while (true) {
                    byte[] frame = readTcpFrame(in);

                    System.out.println("[TCP RX] " + toHex(frame));

                    ParserResult result = parser.parse(frame);
                    byte[] response = dispatcher.dispatch(result);

                    System.out.println("[TCP TX] " + toHex(response));

                    synchronized (out) {
                        out.write(response);
                        out.flush();
                    }
                }
            } catch (EOFException eof) {
                System.out.println("[TCP] Client disconnected");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        private byte[] readTcpFrame(InputStream in) throws Exception {
            int b;

            while (true) {
                b = in.read();

                if (b < 0) {
                    throw new EOFException();
                }

                if (b == Protocol.SYNC_1) {
                    int b2 = in.read();

                    if (b2 < 0) {
                        throw new EOFException();
                    }

                    if (b2 == Protocol.SYNC_2) {
                        break;
                    }
                }
            }

            byte[] headerWithoutSync = readExactly(in, 7);

            int len = ((headerWithoutSync[5] & 0xFF) << 8) | (headerWithoutSync[6] & 0xFF);

            byte[] payloadAndCrc = readExactly(in, len + 2);

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            out.write(Protocol.SYNC_1);
            out.write(Protocol.SYNC_2);
            out.write(headerWithoutSync);
            out.write(payloadAndCrc);

            return out.toByteArray();
        }

        private byte[] readExactly(InputStream in, int length) throws Exception {
            byte[] data = new byte[length];
            int offset = 0;

            while (offset < length) {
                int read = in.read(data, offset, length - offset);

                if (read < 0) {
                    throw new EOFException();
                }

                offset += read;
            }

            return data;
        }
    }

    static final class UdpProtocolServer implements Runnable {
        private final int port;
        private final CircleProtocolParser parser;
        private final CommandDispatcher dispatcher;
        private InetAddress lastClientAddress;
        private int lastClientPort;

        UdpProtocolServer(int port, CircleProtocolParser parser, CommandDispatcher dispatcher) {
            this.port = port;
            this.parser = parser;
            this.dispatcher = dispatcher;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[2048];

            try (DatagramSocket socket = new DatagramSocket(port)) {

                Thread statusThread = new Thread(() -> {
                    try {
                        while (!socket.isClosed()) {
                            if (lastClientAddress != null && lastClientPort != 0) {
                                byte[] statusFrame = ProtocolFrameBuilder.buildStatus(
                                        dispatcher.controller.getCurrentAngleX10(),
                                        dispatcher.controller.isRotating()
                                );

                                DatagramPacket statusPacket = new DatagramPacket(
                                        statusFrame,
                                        statusFrame.length,
                                        lastClientAddress,
                                        lastClientPort
                                );

                                socket.send(statusPacket);

                                System.out.println("[UDP STATUS TX] " + toHex(statusFrame));
                            }

                            Thread.sleep(2000);
                        }
                    } catch (Exception e) {
                        System.out.println("[UDP STATUS] stopped");
                    }
                });

                statusThread.setDaemon(true);
                statusThread.start();

                System.out.println("[UDP] Listening on port " + port);

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    lastClientAddress = packet.getAddress();
                    lastClientPort = packet.getPort();

                    byte[] frame = Arrays.copyOf(packet.getData(), packet.getLength());

                    System.out.println("[UDP RX] from " + packet.getAddress().getHostAddress() + ":" + packet.getPort()
                            + " " + toHex(frame));

                    ParserResult result = parser.parse(frame);
                    byte[] response = dispatcher.dispatch(result);

                    System.out.println("[UDP TX] " + toHex(response));

                    DatagramPacket responsePacket = new DatagramPacket(response, response.length, packet.getAddress(),
                            packet.getPort());

                    socket.send(responsePacket);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    static int readUInt16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    static int crc16CcittFalse(byte[] data, int offset, int length) {
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

    static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder();

        for (byte b : data) {
            if (sb.length() > 0) {
                sb.append(' ');
            }

            sb.append(String.format("%02X", b & 0xFF));
        }

        return sb.toString();
    }
}