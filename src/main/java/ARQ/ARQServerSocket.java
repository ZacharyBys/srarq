package ARQ;

import com.google.common.primitives.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ARQServerSocket {
    private DatagramChannel channel;
    private int port;
    private long base;
    private SocketAddress routerAddress;
    private InetAddress peerAddress;
    private int peerPort;

    private static final Logger logger = LoggerFactory.getLogger(ARQServerSocket.class);

    public ARQServerSocket(int port, long base) {
        this.port = port;
        this.base = base;
    }

    private class ARQByteOutputStream extends ByteArrayOutputStream {
        @Override
        public void write(byte[] b) throws IOException {
            List<Packet> packetsToSend = new ArrayList<>();

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
                    logger.debug("Sent DATA_END packet");
                    packet = new Packet
                            .Builder()
                            .setType(Packet.Type.DATA_END.ordinal())
                            .setPeerAddress(peerAddress)
                            .setPortNumber(peerPort)
                            .setSequenceNumber(base++)
                            .setPayload(payloadFragment)
                            .create();
                } else {
                    packet = new Packet
                            .Builder()
                            .setType(Packet.Type.DATA.ordinal())
                            .setPeerAddress(peerAddress)
                            .setPortNumber(peerPort)
                            .setSequenceNumber(base++)
                            .setPayload(payloadFragment)
                            .create();
                }

                packetsToSend.add(packet);
                remaining -= Packet.MAX_PAYLOAD_LEN;
            }

            logger.debug("Split response into {} fragments", packetsToSend.size());

            ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
            try (DatagramChannel channel = DatagramChannel.open()) {
                channel.bind(new InetSocketAddress(port));

                for (Packet responsePacket: packetsToSend) {
                    logger.debug("{}", routerAddress);
                    channel.send(responsePacket.toBuffer(), routerAddress);
                    logger.debug("Sending packet: {}", responsePacket);
                    logger.debug("Router: {}", responsePacket);
                    String responsePacketPayload = new String(responsePacket.getPayload(), StandardCharsets.UTF_8);
                    logger.debug("Payload: {}",  responsePacketPayload);
                }
            }
        }
    }

    public OutputStream getOutputStream() throws IOException {
        return new ARQByteOutputStream();
    }

    public InputStream getInputStream() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
        List<Byte> payload = new ArrayList<>();
        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.bind(new InetSocketAddress(port));
            while (true) {
                // Receive next packet and remember source
                this.routerAddress = channel.receive(buf);

                buf.flip();
                Packet receivedPacket = Packet.fromBuffer(buf);
                buf.flip();

                this.peerAddress = receivedPacket.getPeerAddress();
                this.peerPort = receivedPacket.getPeerPort();

                // Collect payload
                payload.addAll(Bytes.asList(receivedPacket.getPayload()));

                // Receive data until last fragment is reached
                if (receivedPacket.getType() == Packet.Type.DATA_END.ordinal()) {
                    break;
                }
            }
        }
        return new ByteArrayInputStream(Bytes.toArray(payload));
    }

    public void close() {

    }

}
