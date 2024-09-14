import java.io.*;
import java.net.*;
import java.util.HashMap;

public class GETClient {
    private Socket socket;
    public LamportClock clock;

    public GETClient(String serverDetails) throws IOException {
        String[] serverParts = serverDetails.split(":");
        String serverName = serverParts[0];
        int port = Integer.parseInt(serverParts[1]);
        this.socket = new Socket(serverName, port);
        this.clock = new LamportClock();
    }

    public HashMap<String, String> sendGetRequest(String stationId) {
        HashMap<String, String> weather;
        clock.increaseTime();
        try {
            RequestResponseHandler.sendGetRequest(socket, stationId, clock.getTime());
            weather = RequestResponseHandler.parseResponse(socket);
            int receivedTime = Integer.parseInt(weather.get("Lamport-Time"));
            clock.increaseTime(receivedTime);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return weather;
    }
}
