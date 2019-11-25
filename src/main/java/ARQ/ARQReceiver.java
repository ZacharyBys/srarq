package ARQ;

import com.google.common.primitives.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ARQReceiver {
    private final int WINDOW_SIZE = 3;
    private final int SEQUENCE_SIZE = 2 * WINDOW_SIZE;

    private int port;
    private DatagramChannel channel;
    private SocketAddress routerAddress;

    private Long base;
    private int[] sequenceNumbers;
    private int windowIndex = 0;

    private static final Logger logger = LoggerFactory.getLogger(ARQReceiver.class);

    public ARQReceiver(int port, long base, DatagramChannel channel, SocketAddress routerAddress) {
        this.port = port;
        this.base = base;
        this.channel = channel;
        this.routerAddress = routerAddress;
        initializeWindow();
    }

    private void initializeWindow() {
        sequenceNumbers = new int[SEQUENCE_SIZE];
        for (int i = 0; i < SEQUENCE_SIZE; i++) {
            this.sequenceNumbers[i] = i % this.SEQUENCE_SIZE + 1;
        }
        logger.debug("Initialized sequence numbers window {}", sequenceNumbers);
    }

    public byte[] receive() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
        Map<Long, Packet> receivedPacketBuffer = new HashMap();
        byte[] reconstructedPayload = null;
        List<Packet> packets = new ArrayList<>();

        while (true) {
            logger.debug("Waiting for packets");
            logger.debug("Next in-order sequence number: {}", sequenceNumbers[windowIndex]);

            // Receive packet
            channel.receive(buf);

            buf.flip();
            Packet receivedPacket = Packet.fromBuffer(buf);
            buf.flip();

            // We received the last packet
            if (receivedPacket.getType() == Packet.Type.DATA_END.ordinal()) {
                logger.debug("Received last data packet");
                break;
            }

            // Send acknowledgement for received packet
            Packet acknowledgementPacket = new Packet
                    .Builder()
                    .setType(Packet.Type.ACK.ordinal())
                    .setPeerAddress(receivedPacket.getPeerAddress())
                    .setPortNumber(receivedPacket.getPeerPort())
                    .setSequenceNumber(receivedPacket.getSequenceNumber())
                    .setPayload("".getBytes())
                    .create();

            channel.send(acknowledgementPacket.toBuffer(), routerAddress);
            logger.debug("Sending acknowledgement for packet with sequence number {}", receivedPacket.getSequenceNumber());

            if (receivedPacket.getSequenceNumber() == sequenceNumbers[windowIndex]) {
                logger.debug("Last packet received (sequence number={}) was in-order", receivedPacket.getSequenceNumber());
                packets.add(receivedPacket);

                int bufferedIdx = (windowIndex + 1) % SEQUENCE_SIZE;
                // Move base to next not-yet-received packet
                logger.debug("Buffered sequence numbers {}", receivedPacketBuffer.keySet());
                logger.debug("Trying to advance base, starting at sequence number {}", sequenceNumbers[bufferedIdx]);
                while (receivedPacketBuffer.containsKey((long) sequenceNumbers[bufferedIdx])) {
                    logger.debug("Removed packet with sequence number {} from buffer", sequenceNumbers[bufferedIdx]);
                    Packet bufferedPacket = receivedPacketBuffer.remove((long) sequenceNumbers[bufferedIdx]);
                    packets.add(bufferedPacket);
                    bufferedIdx = (bufferedIdx + 1) % SEQUENCE_SIZE;
                }

                windowIndex = bufferedIdx;
            } else {
                // Received out of order packet
                logger.debug("Received out-of-order packet with sequence number {}", receivedPacket.getSequenceNumber());
                receivedPacketBuffer.put(receivedPacket.getSequenceNumber(), receivedPacket);
            }
        }

        reconstructedPayload = reconstructFragmentedPayload(packets);
        logger.debug("Received {} packets", packets.size());
        logger.debug("Complete payload received: \n{}", new String(reconstructedPayload, StandardCharsets.UTF_8));

        return reconstructedPayload;
    }

    protected boolean isWithinReceiveWindow(long receivedPacketSequenceNumber) {
        int windowStart = windowIndex % WINDOW_SIZE;
        int windowEnd = (windowIndex + SEQUENCE_SIZE - 1) % WINDOW_SIZE;

        for (int i = Math.min(windowStart, windowEnd) + 1; i < Math.max(windowStart, windowEnd); i++) {
            if (receivedPacketSequenceNumber == sequenceNumbers[i]) {
                return false;
            }
        }

        return true;
    }

    private byte[] reconstructFragmentedPayload(List<Packet> receivedPackets) {
        List<Byte> reconstructedPayload = new ArrayList<>();
        receivedPackets.forEach(packet -> {
            logPacketSentOrReceived(packet);
            reconstructedPayload.addAll(Bytes.asList(packet.getPayload()));
        });

        return Bytes.toArray(reconstructedPayload);
    }

    private void logPacketSentOrReceived(Packet packet) {
        logger.debug("Packet: {}", packet);
        String getRequestPayload = new String(packet.getPayload(), StandardCharsets.UTF_8);
        logger.debug("Payload: {}",  getRequestPayload);
    }

}
