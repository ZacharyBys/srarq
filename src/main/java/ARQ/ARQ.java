package ARQ;

import java.io.IOException;

public interface ARQ {
    public int getLocalPort();
    public ARQServerSocket accept() throws IOException;
}
