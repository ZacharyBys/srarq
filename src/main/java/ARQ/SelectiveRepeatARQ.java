package ARQ;

import java.net.DatagramSocket;

public class SelectiveRepeatARQ implements ARQ {
    private int port;
    private ARQSocket socket;

    public SelectiveRepeatARQ(int port) {
        this.port = port;
    }

    public int getLocalPort() {
        return this.port;
    }

    public ARQSocket accept() {
        return this.socket;
    }
}
