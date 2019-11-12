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
import java.util.List;

public class SelectiveRepeatARQClient {

    private ARQClientState state;
    private Long base;
    private InetSocketAddress serverAddr;
    private SocketAddress routerAddr;

    public SelectiveRepeatARQClient(InetSocketAddress serverAddr, SocketAddress routerAddr) {
        state = ARQClientState.LISTEN;
        this.base = 0L;
        this.serverAddr = serverAddr;
        this.routerAddr = routerAddr;
    }

    private static final Logger logger = LoggerFactory.getLogger(SelectiveRepeatARQClient.class);

    public void run() throws IOException {
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
                    channel.send(responsePacket.toBuffer(), routerAddr);
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
                    // Selective repeat logic
                    logger.info("Selective repeat");
                    logger.debug("Current sequence number: {}", base);

                    // BELOW IS JUST A TEST

                    // Send GET request
                    Packet getRequestPacket = new Packet
                            .Builder()
                            .setType(Packet.Type.DATA_END.ordinal())
                            .setPortNumber(serverAddr.getPort())
                            .setPeerAddress(serverAddr.getAddress())
                            .setSequenceNumber(++base)
                            .setPayload("GET / HTTP/1.1\r\n".getBytes())
                            .create();

                    // Send GET request packet
                    channel.send(getRequestPacket.toBuffer(), routerAddr);
                    logger.debug("Sending GET request packet");
                    logPacketSentOrReceived(getRequestPacket);

                    ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                    SocketAddress router = channel.receive(buf);

                    List<Packet> packets = new ArrayList<>();

                    // Parse all packets containing fragmented data
                    buf.flip();
                    Packet packet = Packet.fromBuffer(buf);
                    buf.flip();
                    while (packet.getType() != Packet.Type.DATA_END.ordinal()) {
                        packets.add(packet);
                        buf = ByteBuffer.allocate(Packet.MAX_LEN);
                        channel.receive(buf);
                        buf.flip();
                        packet = Packet.fromBuffer(buf);
                        buf.flip();
                    }

                    // Add last DATA_END packet
                    packets.add(packet);

                    StringBuilder response = new StringBuilder();
                    logger.debug("Received {} packets", packets.size());
                    for (Packet p : packets) {
                        logPacketSentOrReceived(p);
                        String payload = new String(p.getPayload(), StandardCharsets.UTF_8);
                        response.append(payload);
                    }

                    logger.debug("Complete payload received: {}", response);
                    return;
                }
//                // Try to receive a packet within timeout.
//                channel.configureBlocking(false);
//                Selector selector = Selector.open();
//                channel.register(selector, OP_READ);
//                logger.debug("Waiting for the response");
//                selector.select(5000);
//
//                Set<SelectionKey> keys = selector.selectedKeys();
//                if(keys.isEmpty()){
//                    logger.error("No response for packet with sequence number {} after timeout", responsePacket.getSequenceNumber());
//                    return;
//                }
//
//                keys.clear();
            }
        }
    }

    private void logPacketSentOrReceived(Packet packet) {
        logger.debug("Packet: {}", packet);
        String getRequestPayload = new String(packet.getPayload(), StandardCharsets.UTF_8);
        logger.debug("Payload: {}",  getRequestPayload);
    }

    public static void main(String[] args) throws IOException {
//        OptionParser parser = new OptionParser();
//        parser.accepts("router-host", "Router hostname")
//                .withOptionalArg()
//                .defaultsTo("localhost");
//
//        parser.accepts("router-port", "Router port number")
//                .withOptionalArg()
//                .defaultsTo("3000");
//
//        parser.accepts("server-host", "EchoServer hostname")
//                .withOptionalArg()
//                .defaultsTo("localhost");
//
//        parser.accepts("server-port", "EchoServer listening port")
//                .withOptionalArg()
//                .defaultsTo("8007");
//
//        OptionSet opts = parser.parse(args);
//
//        // Router address
//        String routerHost = (String) opts.valueOf("router-host");
//        int routerPort = Integer.parseInt((String) opts.valueOf("router-port"));
//
//        // Server address
//        String serverHost = (String) opts.valueOf("server-host");
//        int serverPort = Integer.parseInt((String) opts.valueOf("server-port"));

        SocketAddress routerAddress = new InetSocketAddress("localhost", 3000);
        InetSocketAddress serverAddress = new InetSocketAddress("localhost", 8007);

        SelectiveRepeatARQClient client = new SelectiveRepeatARQClient(serverAddress, routerAddress);
        client.run();
    }
}

