package ARQ;

import java.io.IOException;

public interface ARQ {
    ARQSocket accept() throws IOException;
    int getLocalPort();
}
