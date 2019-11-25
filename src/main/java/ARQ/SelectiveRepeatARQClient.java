package ARQ;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static java.lang.Thread.sleep;

public class SelectiveRepeatARQClient implements ARQ {
    private static final int CLIENT_SOCKET_PORT = 8009;
    private static final int TIMER_RESEND_VALUE = 5000;

    private ARQClientState state;
    private Long base;
    private InetSocketAddress serverAddr;
    private SocketAddress routerAddr;
    private Date date;

    public SelectiveRepeatARQClient(InetSocketAddress serverAddr, SocketAddress routerAddr) {
        state = ARQClientState.LISTEN;
        this.base = 0L;
        this.serverAddr = serverAddr;
        this.routerAddr = routerAddr;
        this.date = new Date();
    }

    private static final Logger logger = LoggerFactory.getLogger(SelectiveRepeatARQClient.class);

    public ARQSocket accept() throws IOException {
        try (DatagramChannel channel = DatagramChannel.open()) {
            for (; ; ) {
                // The packet to respond with
                Packet responsePacket;
                if (state == ARQClientState.LISTEN) {
                    // Change state to SYN_RCVD
                    state = ARQClientState.SYN_SENT;
                    // Create SYN_SENT packet
                    responsePacket = new Packet
                            .Builder()
                            .setType(Packet.Type.SYN.ordinal())
                            .setPortNumber(serverAddr.getPort())
                            .setPeerAddress(serverAddr.getAddress())
                            .setSequenceNumber(base++)
                            .setPayload("SYN".getBytes())
                            .create();

                    // Send packet
                    channel.send(responsePacket.toBuffer(), serverAddr);
                    logger.info("Changing state from LISTEN to SYN_SENT");
                    logger.debug("Sending SYN packet");
                    logPacketSentOrReceived(responsePacket);

                } else if (state == ARQClientState.SYN_SENT) {
                    // Wait for acknowledgment of SYN packet
                    ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                    SocketAddress router = channel.receive(buf);

                    // Parse a packet from the received raw data.
                    buf.flip();
                    Packet packet = Packet.fromBuffer(buf);
                    buf.flip();

                    logPacketSentOrReceived(packet);

                    // Handshake: received and acknowledgement of SYN_RCVD
                    if (packet.getType() == Packet.Type.SYN_ACK.ordinal()) {
                        logger.info("Received packet is of type SYN_ACK");

                        // Change state to ESTAB
                        state = ARQClientState.ESTAB;

                        // Increment base to next sequence number
                        if (packet.getSequenceNumber() == base) {
                            base++;
                        } else {
                            continue;
                        }

                        // Create ACK packet with next sequence number
                        responsePacket = new Packet
                                .Builder()
                                .setType(Packet.Type.ACK.ordinal())
                                .setPortNumber(serverAddr.getPort())
                                .setPeerAddress(serverAddr.getAddress())
                                .setSequenceNumber(base)
                                .setPayload("ACK for SYN_ACK".getBytes())
                                .create();

                        // Send packet
                        channel.send(responsePacket.toBuffer(), router);
                        logger.debug("Sending ACK for SYN_ACK");
                        logPacketSentOrReceived(responsePacket);

                        logger.info("Changing state from SYN_SENT to ESTAB");

                    }
                } else if (state == ARQClientState.ESTAB) {
                    logger.info("Connection established");
                    ARQSocket clientSocket = new ARQSocket(CLIENT_SOCKET_PORT, 1, new InetSocketAddress("localhost", 8007));
                    resetForNewConnection();
                    return clientSocket;
                }
            }
        }
    }

    @Override
    public int getLocalPort() {
        return CLIENT_SOCKET_PORT;
    }

    private void logPacketSentOrReceived(Packet packet) {
        logger.debug("Packet: {}", packet);
        String getRequestPayload = new String(packet.getPayload(), StandardCharsets.UTF_8);
        logger.debug("Payload: {}",  getRequestPayload);
    }

    private void resetForNewConnection() {
        base = 0L;
        state = ARQClientState.LISTEN;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        SocketAddress routerAddress = new InetSocketAddress("localhost", 3000);
        InetSocketAddress serverAddress = new InetSocketAddress("localhost", 8008);
        SelectiveRepeatARQClient client = new SelectiveRepeatARQClient(serverAddress, routerAddress);

        List<String> requests = new ArrayList<>();
        String content = "We out here";
        requests.addAll(Arrays.asList(
                "GET / HTTP/1.1\r\n",
                "GET /test_file.txt HTTP/1.1\r\n",
                String.format("POST /yeet.txt HTTP/1.1\r\nContent-Length: %d\r\n\r\n%s", content.length(), content)
        ));

        requests.forEach(request -> {
            try {
                // Get new socket
                ARQSocket clientSocket = client.accept();

                logger.debug("Sleeping for 3s");
                sleep(3000);

                // Make request
                logger.debug("Making the following HTTP request: {}", request);
                clientSocket.getOutputStream().write(request.getBytes());

                logger.debug("Sleeping for 3s");
                sleep(3000);

                // Get response from server
                clientSocket.getInputStream();
                clientSocket.close();

                logger.debug("Sleeping for 3s");
                sleep(3000);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });
    }
}

