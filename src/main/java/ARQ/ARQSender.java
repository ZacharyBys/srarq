package ARQ;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ARQSender {
    private final int WINDOW_SIZE = 3;
    private final int SEQUENCE_SIZE = 2 * WINDOW_SIZE;
    private final int TIMER_RESEND_VALUE = 3000;

    private int port;
    private DatagramChannel channel;
    private InetSocketAddress peerAddress;
    private SocketAddress routerAddress;

    private Long base;
    private int[] sequenceNumbers;
    private int windowIndex = 0;
    private HashMap<Integer, Timer> packetTimers = new HashMap<Integer, Timer>();

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

            Packet packet = new Packet
                        .Builder()
                        .setType(Packet.Type.DATA.ordinal())
                        .setPeerAddress(peerAddress.getAddress())
                        .setPortNumber(peerAddress.getPort())
                        .setSequenceNumber((initialSequenceNumber++) % SEQUENCE_SIZE + 1)
                        .setPayload(payloadFragment)
                        .create();

            packetsToSend.add(packet);
            remaining -= Packet.MAX_PAYLOAD_LEN;
        }

        return packetsToSend;
    }

    class ResendTask extends TimerTask
    {
        private Packet packet;
        private DatagramChannel channel;
        private SocketAddress routerAddress;
        private int windowIndex;

        public ResendTask(DatagramChannel channel, SocketAddress routerAddress, Packet packet, int windowIndex) {
            this.channel = channel;
            this.routerAddress = routerAddress;
            this.packet = packet;
            this.windowIndex = windowIndex;
        }

        public void run()
        {
            try {
                logger.debug("Sending packet with sequence number {}", this.packet.getSequenceNumber());
                this.channel.send(this.packet.toBuffer(), this.routerAddress);

                Timer timer = new Timer();
                ResendTask resendTask = new ResendTask(this.channel, this.routerAddress, packet, windowIndex);
                packetTimers.put(windowIndex, timer);
                timer.schedule(resendTask, TIMER_RESEND_VALUE);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void setPacketTimer(int windowIndex, Packet packet) {
        Timer timer = new Timer();
        ResendTask resendTask = new ResendTask(this.channel, this.routerAddress, packet, windowIndex);
        packetTimers.put(windowIndex, timer);
        timer.schedule(resendTask, TIMER_RESEND_VALUE);
        logger.debug("Setting Timer");
    }

    private void sendPacket(int windowIndex, Packet packet) throws IOException {
        channel.send(packet.toBuffer(), routerAddress);
        this.setPacketTimer(windowIndex, packet);
    }

    private void cancelPacketResend(int windowIndex) {
        if (packetTimers.containsKey(windowIndex)) {
            packetTimers.get(windowIndex).cancel();
        }

        packetTimers.remove(windowIndex);
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
            this.sendPacket(windowIndex, nextPacket);
        }

        logger.debug("Now waiting to received acknowledgements for all sent packets");
        Set<Long> receivedAcks = new HashSet<>();
        while (windowIndex < sequenceNumbers.length) {
            if (receivedAcks.size() > 0) {
                logger.debug("Received acknowledgment for the following sequence numbers: {}", receivedAcks);
            }

            logger.debug("Next in-order packet is {}", sequenceNumbers[windowIndex]);
            // Receive acknowledgements for sent packets
            channel.receive(buf);

            buf.flip();
            Packet acknowledgmentPacket = Packet.fromBuffer(buf);
            buf.flip();

            if (acknowledgmentPacket.getSequenceNumber() == sequenceNumbers[windowIndex]) {
                logger.debug("Received acknowledgement packet with sequence number {}", acknowledgmentPacket.getSequenceNumber());
                this.cancelPacketResend(windowIndex);
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
                    Packet nextPacket = packetsToSend.get(nextPacketIndex);
                    logger.debug("Sending packet with sequence number {}", nextPacket.getSequenceNumber());
                    this.sendPacket(windowIndex, nextPacket);
                }
            } else {
                logger.debug("Buffering acknowledgement for packet with sequence number {}", acknowledgmentPacket.getSequenceNumber());
                receivedAcks.add(acknowledgmentPacket.getSequenceNumber());
            }

            // Free up any acknowledged sequence numbers and send next packets
            logger.debug("Freeing up sequence numbers to be re-used");
            while (receivedAcks.contains((long) sequenceNumbers[windowIndex])) {
                logger.debug("Freeing up sequence number {}", sequenceNumbers[windowIndex]);
                this.cancelPacketResend(windowIndex);
                receivedAcks.remove(sequenceNumbers[windowIndex++]);

                // Check if we've reached the end
                if (windowIndex >= this.sequenceNumbers.length) {
                    break;
                }

                int nextPacketIndex = windowIndex + WINDOW_SIZE - 1;
                if (nextPacketIndex < sequenceNumbers.length) {
                    logger.debug("Sending next packet at index {}", nextPacketIndex);
                    Packet nextPacket = packetsToSend.get(nextPacketIndex);
                    this.sendPacket(windowIndex, nextPacket);
                    logPacketSentOrReceived(nextPacket);
                }
            }
        }

        // Send packet signifying that whole payload has been sent
        Packet endPacket = new Packet
                .Builder()
                .setType(Packet.Type.DATA_END.ordinal())
                .setPeerAddress(peerAddress.getAddress())
                .setPortNumber(peerAddress.getPort())
                .setSequenceNumber(0)
                .setPayload("".getBytes())
                .create();

        channel.send(endPacket.toBuffer(), routerAddress);
    }

    private void logPacketSentOrReceived(Packet packet) {
        logger.debug("Packet: {}", packet);
        String getRequestPayload = new String(packet.getPayload(), StandardCharsets.UTF_8);
        logger.debug("Payload: {}",  getRequestPayload);
    }

}
