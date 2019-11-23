package ARQ;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;

public class ARQSocket {
    private static final int ROUTER_PORT = 3000;

    private int port;
    private long base;

    private DatagramChannel channel;
    private SocketAddress routerAddress;
    private InetSocketAddress peerAddress;

    private static final Logger logger = LoggerFactory.getLogger(ARQSocket.class);

    public ARQSocket(int port, long base, InetSocketAddress peerAddress) throws IOException {
        this.port = port;
        this.base = base;
        this.channel = initializeDatagramChannel(port);
        this.routerAddress = new InetSocketAddress("localhost", ROUTER_PORT);
        this.peerAddress = peerAddress;
    }

    private DatagramChannel initializeDatagramChannel(int port) throws IOException {
        DatagramChannel channel = DatagramChannel.open();
        channel.bind(new InetSocketAddress(port));
        return channel;
    }

    private class ARQByteOutputStream extends ByteArrayOutputStream {
        @Override
        public void write(byte[] b) throws IOException {
            ARQSender arqSender = new ARQSender(port, base, channel, peerAddress, routerAddress);
            arqSender.send(b);
        }
    }

    public OutputStream getOutputStream() throws IOException {
        return new ARQByteOutputStream();
    }

    public InputStream getInputStream() throws IOException {
        ARQReceiver arqReceiver = new ARQReceiver(port, base, channel, routerAddress);
        byte[] payload = arqReceiver.receive();
        return new ByteArrayInputStream(payload);
    }

    public void close() throws IOException {
        channel.close();
    }

}
