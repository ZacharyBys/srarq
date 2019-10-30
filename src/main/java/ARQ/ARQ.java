package ARQ;

import java.net.DatagramSocket;

public interface ARQ {
    public int getLocalPort();
    public ARQSocket accept();
}
