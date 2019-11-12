package ARQ;

public class ARQSocket {
    int port;
    UDPServer server;

    ARQServerState state;

    public ARQSocket(int port) {
        this.port = port;
        this.server = new UDPServer();
        this.state = ARQServerState.CLOSED;
    }

    public void handshake() {
        // Change state to listen
        this.state = ARQServerState.LISTEN;

        // Receive SYN from client
        Packet packet = new Packet.Builder().create();

        // Initial sequence number is value from SYN packet
        Long initialSequenceNumber =  packet.getSequenceNumber();

        if (packet.getType() == Packet.Type.SYN.ordinal()) {
            // Change state to SYN_RCVD
            this.state = ARQServerState.SYN_RCVD;

            // Create SYN-ACK packet
            Packet synAck = new Packet
                    .Builder()
                    .setType(Packet.Type.SYN_ACK.ordinal())
                    .create();

            // Send acknowledgement
        }

        // Receive acknowlegement of SYN_RCVD
        Packet synRcvd = new Packet.Builder().create();
        if (packet.getType() == Packet.Type.ACK.ordinal()) {
            // Change state to ESTAB
            this.state = ARQServerState.ESTAB;
        }

    }


}
