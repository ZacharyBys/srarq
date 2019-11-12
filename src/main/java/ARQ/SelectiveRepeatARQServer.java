package ARQ;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;

import static java.nio.charset.StandardCharsets.UTF_8;

public class SelectiveRepeatARQServer implements ARQ {
    private int port;
    ARQServerState state;

    private long base;

    public SelectiveRepeatARQServer(int port) {
        this.port = port;
        this.state = ARQServerState.CLOSED;
    }

    public int getLocalPort() {
        return this.port;
    }

    public ARQSocket accept() throws IOException {
        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.bind(new InetSocketAddress(port));
            ByteBuffer buf = ByteBuffer
                    .allocate(Packet.MAX_LEN)
                    .order(ByteOrder.BIG_ENDIAN);

            for (; ; ) {
                if (this.state == ARQServerState.CLOSED) {
                    // Change state to listen
                    this.state = ARQServerState.LISTEN;
                }

                buf.clear();
                SocketAddress router = channel.receive(buf);

                // Parse a packet from the received raw data.
                buf.flip();
                Packet packet = Packet.fromBuffer(buf);
                buf.flip();

                // The packet to respond with
                Packet responsePacket = null;

                if (this.state == ARQServerState.LISTEN) {
                    // Handshake: received a SYN
                    if (packet.getType() == Packet.Type.SYN.ordinal() && this.state == ARQServerState.LISTEN) {
                        // Change state to SYN_RCVD
                        this.state = ARQServerState.SYN_RCVD;

                        // Set base to sequence number in SYN packet
                        this.base = packet.getSequenceNumber();

                        // Create SYN-ACK packet
                        responsePacket = new Packet
                                .Builder()
                                .setType(Packet.Type.SYN_ACK.ordinal())
                                .create();
                    }
                } else if (this.state == ARQServerState.SYN_RCVD) {
                    // Handshake: received and acknowledgement of SYN_RCVD
                    if (packet.getType() == Packet.Type.ACK.ordinal()) {
                        // Change state to ESTAB
                        this.state = ARQServerState.ESTAB;

                        // Create ACK packet with next sequence number
                        responsePacket = new Packet
                                .Builder()
                                .setType(Packet.Type.ACK.ordinal())
                                .setSequenceNumber(++this.base)
                                .create();
                    }
                } else if (this.state == ARQServerState.ESTAB) {
                    // Selective repeat logic
                }

                String payload = new String(packet.getPayload(), UTF_8);

                // Send the response to the router not the client.
                // The peer address of the packet is the address of the client already.
                // We can use toBuilder to copy properties of the current packet.
                // This demonstrate how to create a new packet from an existing packet.
                Packet resp = packet.toBuilder()
                        .setPayload(payload.getBytes())
                        .create();
                channel.send(resp.toBuffer(), router);

            }
        }
    }

}