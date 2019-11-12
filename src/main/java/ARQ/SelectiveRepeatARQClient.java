package ARQ;

import jdk.internal.joptsimple.OptionParser;
import jdk.internal.joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.security.Policy;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.charset.StandardCharsets.UTF_8;

public class SelectiveRepeatARQClient {

    private ARQClientState state;
    private Long base;
    private InetSocketAddress serverAddr;
    private SocketAddress routerAddr;

    public SelectiveRepeatARQClient(InetSocketAddress serverAddr, SocketAddress routerAddr) {
        this.state = ARQClientState.LISTEN;
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
                if (this.state == ARQClientState.LISTEN) {
                    // Change state to SYN_RCVD
                    this.state = ARQClientState.SYN_SENT;

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
                    logger.debug("Sending packet of type SYN: {}", responsePacket);
                    logger.debug("Router: {}", responsePacket);
                    String responsePacketPayload = new String(responsePacket.getPayload(), StandardCharsets.UTF_8);
                    logger.debug("Payload: {}",  responsePacketPayload);

                } else if (this.state == ARQClientState.SYN_SENT) {
                    // Wait for acknowledgment of SYN packet
                    ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                    SocketAddress router = channel.receive(buf);

                    // Parse a packet from the received raw data.
                    buf.flip();
                    Packet packet = Packet.fromBuffer(buf);
                    buf.flip();

                    logger.debug("Received packet: {}", packet);
                    logger.debug("Router: {}", router);
                    String payload = new String(packet.getPayload(), StandardCharsets.UTF_8);
                    logger.debug("Payload: {}",  payload);

                    // Handshake: received and acknowledgement of SYN_RCVD
                    if (packet.getType() == Packet.Type.SYN_ACK.ordinal()) {
                        logger.info("Received packet is of type SYN_ACK");

                        // Change state to ESTAB
                        this.state = ARQClientState.ESTAB;

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
                        logger.debug("Sending ACK for SYN_ACK: {}", responsePacket);
                        logger.debug("Router: {}", responsePacket);
                        String responsePacketPayload = new String(responsePacket.getPayload(), StandardCharsets.UTF_8);
                        logger.debug("Payload: {}",  responsePacketPayload);

                        logger.info("Changing state from SYN_SENT to ESTAB");

                    }
                } else if (this.state == ARQClientState.ESTAB) {
                    // Selective repeat logic
                    logger.info("Selective repeat");
                    logger.debug("Current sequence number: {}", base);
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

