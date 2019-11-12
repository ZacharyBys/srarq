package ARQ;

import com.google.common.primitives.Bytes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ARQServerSocket {
    private DatagramChannel channel;
    private int port;
    private long base;

    public ARQServerSocket(int port, long base) {
        this.port = port;
        this.base = base;
    }

    public OutputStream getOutputStream() throws IOException {
        return null;
    }

    public InputStream getInputStream() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
        List<Byte> payload = new ArrayList<>();
        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.bind(new InetSocketAddress(port));
            while (true) {
                // Receive next packet
                channel.receive(buf);

                buf.flip();
                Packet receivedPacket = Packet.fromBuffer(buf);
                buf.flip();

                // Collect payload
                for (byte b : receivedPacket.getPayload()) {
                    payload.add(b);
                }

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
