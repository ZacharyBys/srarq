package ARQ;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;

public class SelectiveRepeatARQServer implements ARQ {
    private int port;
    private ARQServerState state;

    private long base;

    private static final Logger logger = LoggerFactory.getLogger(SelectiveRepeatARQClient.class);

    public SelectiveRepeatARQServer(int port) {
        this.port = port;
        state = ARQServerState.CLOSED;
    }

    public int getLocalPort() {
        return this.port;
    }

    public ARQServerSocket accept() throws IOException {
        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.bind(new InetSocketAddress(port));
            ByteBuffer buf = ByteBuffer
                    .allocate(Packet.MAX_LEN)
                    .order(ByteOrder.BIG_ENDIAN);

            logger.debug("Binding datagram channel to port {}", port);

            for (; ; ) {
                if (state == ARQServerState.CLOSED) {
                    // Change state to listen
                    state = ARQServerState.LISTEN;
                    logger.debug("Changing state from CLOSED to LISTEN");
                }

                // The packet to respond with
                Packet responsePacket = null;
                if (state == ARQServerState.LISTEN) {
                    // Handshake: received a SYN
                    logger.debug("In state LISTEN");

                    buf.clear();
                    SocketAddress router = channel.receive(buf);

                    // Parse a packet from the received raw data.
                    buf.flip();
                    Packet receivedPacket = Packet.fromBuffer(buf);
                    buf.flip();

                    logger.debug("Received packet");
                    logPacketSentOrReceived(receivedPacket);

                    if (receivedPacket.getType() == Packet.Type.SYN.ordinal() && state == ARQServerState.LISTEN) {
                        logger.debug("Received packet is of type SYN");
                        // Change state to SYN_RCVD
                        state = ARQServerState.SYN_RCVD;

                        // Set base to sequence number in SYN packet
                        base = receivedPacket.getSequenceNumber();
                        logger.debug("Setting base sequence number to {}", base);

                        // Create SYN-ACK packet
                        responsePacket = new Packet
                                .Builder()
                                .setType(Packet.Type.SYN_ACK.ordinal())
                                .setSequenceNumber(++base)
                                .setPeerAddress(receivedPacket.getPeerAddress())
                                .setPortNumber(receivedPacket.getPeerPort())
                                .setPayload("SYN_ACK".getBytes())
                                .create();

                        channel.send(responsePacket.toBuffer(), router);
                        logger.debug("Sending packet of type SYN_ACK");
                        logPacketSentOrReceived(responsePacket);

                        logger.debug("Changing state from LISTEN to SYN_RCVD");
                    }
                } else if (state == ARQServerState.SYN_RCVD) {
                    // Handshake: received an acknowledgement of SYN_RCVD
                    logger.debug("In state SYN_RCVD");

                    buf.clear();
                    SocketAddress router = channel.receive(buf);

                    // Parse a packet from the received raw data.
                    buf.flip();
                    Packet receivedPacket = Packet.fromBuffer(buf);
                    buf.flip();

                    logger.debug("Received packet");
                    logPacketSentOrReceived(receivedPacket);

                    if (receivedPacket.getType() == Packet.Type.ACK.ordinal()) {
                        logger.debug("Received ACK for SYN_RCVD packet");
                        // Change state to ESTAB
                        state = ARQServerState.ESTAB;

                        // Increment sequence number if it the next one
                        if (receivedPacket.getSequenceNumber() == base + 1) {
                            base = receivedPacket.getSequenceNumber();
                        } else {
                            continue;
                        }

                        logger.debug("Changing state from SYN_RCVD to ESTAB");

                    }
                } else if (state == ARQServerState.ESTAB) {
                    // Selective repeat logic
                    logger.debug("Connection established");
                    logger.debug("Current sequence number: {}", base);

                    ARQServerSocket serverSocket =  new ARQServerSocket(port, base);
                    resetForNewConnection();
                    return serverSocket;
                }

            }
        }
    }

    private void logPacketSentOrReceived(Packet packet) {
        logger.debug("Packet: {}", packet);
        String getRequestPayload = new String(packet.getPayload(), StandardCharsets.UTF_8);
        logger.debug("Payload: {}",  getRequestPayload);
    }

    private void resetForNewConnection() {
        base = 0;
        state = ARQServerState.CLOSED;
    }

    public static void main(String[] args) throws IOException {
//        OptionParser parser = new OptionParser();
//        parser.acceptsAll(asList("port", "p"), "Listening port")
//                .withOptionalArg()
//                .defaultsTo("8007");
//
//        OptionSet opts = parser.parse(args);
//        int port = Integer.parseInt((String) opts.valueOf("port"));
        SelectiveRepeatARQServer server = new SelectiveRepeatARQServer(8007);
        server.accept();
    }

}
