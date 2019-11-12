package ARQ;

import java.io.IOException;
import java.net.DatagramSocket;

public interface ARQ {
    public int getLocalPort();
    public ARQSocket accept() throws IOException;
}
