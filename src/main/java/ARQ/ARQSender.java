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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ARQSender {
    private final int WINDOW_SIZE = 3;
    private final int SEQUENCE_SIZE = 2 * WINDOW_SIZE;

    private int port;
    private DatagramChannel channel;
    private InetSocketAddress peerAddress;
    private SocketAddress routerAddress;

    private Long base;
    private int[] sequenceNumbers;
    private int windowIndex = 0;

    private static final Logger logger = LoggerFactory.getLogger(ARQSender.class);

    public ARQSender(int port, long base, DatagramChannel channel, InetSocketAddress peerAddress, SocketAddress routerAddress) {
        this.port = port;
        this.base = base;
        this.channel = channel;
        this.peerAddress = peerAddress;
        this.routerAddress = routerAddress;
    }

    private void initializeWindow(int numPackets) {
        sequenceNumbers = new int[numPackets];
        for (int i = 0; i < numPackets; i++) {
            this.sequenceNumbers[i] = i % this.SEQUENCE_SIZE + 1;
        }
        logger.debug("Initialized sequence numbers window: {}", sequenceNumbers);
    }

    private List<Packet> fragmentPacketPayload(byte[] b) {
        List<Packet> packetsToSend = new ArrayList<>();

        long initialSequenceNumber = 0;
        int remaining = b.length;
        while (remaining > 0) {
            int start = b.length - remaining;
            int offset = Math.min(start + Packet.MAX_PAYLOAD_LEN, b.length);

            byte[] payloadFragment = new byte[Packet.MAX_PAYLOAD_LEN];
            int j = 0;
            for (int i = start; i < offset; i++) {
                payloadFragment[j++] = b[i];
            }
            Packet packet;
            if (remaining <= Packet.MAX_PAYLOAD_LEN) {
                packet = new Packet
                        .Builder()
                        .setType(Packet.Type.DATA_END.ordinal())
                        .setPeerAddress(peerAddress.getAddress())
                        .setPortNumber(peerAddress.getPort())
                        .setSequenceNumber((initialSequenceNumber++) % SEQUENCE_SIZE + 1)
                        .setPayload(payloadFragment)
                        .create();
            } else {
                packet = new Packet
                        .Builder()
                        .setType(Packet.Type.DATA.ordinal())
                        .setPeerAddress(peerAddress.getAddress())
                        .setPortNumber(peerAddress.getPort())
                        .setSequenceNumber((initialSequenceNumber++) % SEQUENCE_SIZE + 1)
                        .setPayload(payloadFragment)
                        .create();
            }

            packetsToSend.add(packet);
            remaining -= Packet.MAX_PAYLOAD_LEN;
        }

        return packetsToSend;
    }

    public void send(byte[] payload) throws IOException {
        // Fragment payload into packets
        List<Packet> packetsToSend = fragmentPacketPayload(payload);
        logger.debug("Split response payload into {} fragments", packetsToSend.size());

        // Initialize sender window
        initializeWindow(packetsToSend.size());

        // Receive loop
        // 1. Receive packet
        // 2. If packet sequence # == sequenceNumbers[windowIndex]:
        //     windowIndex ++
        //      send packets[windowIndex + windowSize]
        //     while sequenceNumbers[windowIndex] is in receicevedAcks:
        //          receivedAcks.delete(sequenceNumber[windowIndex])
        //          windowIndex ++
        //          send packets[windowIndex + windowSize]
        // 3. Else:
        //          receivedAcks.add(packet sequence #)

        ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
        // Send packets from windowIndex to windowIndex + WINDOW_SIZE
        for (int i = windowIndex; i < windowIndex + WINDOW_SIZE && i < sequenceNumbers.length; i++) {
            Packet nextPacket = packetsToSend.get(i);
            channel.send(nextPacket.toBuffer(), routerAddress);
            logPacketSentOrReceived(nextPacket);
        }

        logger.debug("Now waiting to received acknowledgements for all sent packets");
        Set<Long> receivedAcks = new HashSet<>();
        while (windowIndex < sequenceNumbers.length) {
            if (receivedAcks.size() > 0) {
                logger.debug("Received acknowledgment for the following sequence numbers: {}", receivedAcks);
            }
            // Receive acknowledgements for sent packets
            channel.receive(buf);

            buf.flip();
            Packet acknowledgmentPacket = Packet.fromBuffer(buf);
            buf.flip();
            logPacketSentOrReceived(acknowledgmentPacket);
            if (acknowledgmentPacket.getSequenceNumber() == sequenceNumbers[windowIndex]) {
                logger.debug("Received acknowledgement packet with sequence number {}", acknowledgmentPacket.getSequenceNumber());
                // Increment send base
                windowIndex++;

                // Check if we've reached the end
                if (windowIndex >= this.sequenceNumbers.length) {
                    logger.debug("Received all packets");
                    break;
                }

                // Send next packet
                int nextPacketIndex = windowIndex + WINDOW_SIZE - 1;
                if (nextPacketIndex < sequenceNumbers.length) {
                    logger.debug("Sending next packet at index {}", nextPacketIndex);
                    Packet nextPacket = packetsToSend.get(nextPacketIndex);
                    channel.send(nextPacket.toBuffer(), routerAddress);
                    logPacketSentOrReceived(nextPacket);
                }

                // Free up any acknowledged sequence numbers and send next packets
                logger.debug("Freeing up sequence numbers to be re-used");
                while (receivedAcks.contains(sequenceNumbers[windowIndex])) {
                    logger.debug("Freeing up sequence number {}", sequenceNumbers[windowIndex]);
                    receivedAcks.remove(sequenceNumbers[windowIndex++]);

                    // Check if we've reached the end
                    if (windowIndex > this.sequenceNumbers.length) {
                        break;
                    }

                    nextPacketIndex = windowIndex + WINDOW_SIZE - 1;
                    if (nextPacketIndex < sequenceNumbers.length) {
                        logger.debug("Sending next packet at index {}", nextPacketIndex);
                        Packet nextPacket = packetsToSend.get(nextPacketIndex);
                        channel.send(nextPacket.toBuffer(), routerAddress);
                        logPacketSentOrReceived(nextPacket);
                    }
                }
            } else {
                receivedAcks.add(acknowledgmentPacket.getSequenceNumber());
            }
        }
    }

    private void logPacketSentOrReceived(Packet packet) {
        logger.debug("Packet: {}", packet);
        String getRequestPayload = new String(packet.getPayload(), StandardCharsets.UTF_8);
        logger.debug("Payload: {}",  getRequestPayload);
    }

}
