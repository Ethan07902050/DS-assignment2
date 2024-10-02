import java.io.*;
import java.net.*;
import java.util.HashMap;

public class GETClient {
    private Socket socket;
    public LamportClock clock;
    private String serverDetails;
    private static final int MAX_RETRIES = 3; // Maximum number of retry attempts
    private static final int RETRY_DELAY_MS = 2000; // Delay between retries in milliseconds

    public GETClient(String serverDetails) throws IOException {
        this.serverDetails = serverDetails;
        connectToServer();
        this.clock = new LamportClock();
    }

    // Method to connect to the server
    private void connectToServer() throws IOException {
        String[] serverParts = serverDetails.split(":");
        String serverName = serverParts[0];
        int port = Integer.parseInt(serverParts[1]);
        this.socket = new Socket(serverName, port);
    }

    public HashMap<String, String> sendGetRequest(String stationId) {
        HashMap<String, String> weather = null;
        clock.increaseTime();
        int attempts = 0;

        while (attempts < MAX_RETRIES) {
            try {
                // Attempt to send the request
                RequestResponseHandler.sendGetRequest(socket, stationId, clock.getTime());
                weather = RequestResponseHandler.parseResponse(socket);

                // Handle the Lamport time from the response
                if (weather.get("Lamport-Time") != null) {
                    int receivedTime = Integer.parseInt(weather.get("Lamport-Time"));
                    clock.increaseTime(receivedTime);
                }

                // If the request was successful, break out of the loop
                break;
            } catch (IOException e) {
                attempts++;
                System.err.println("Error sending request: " + e.getMessage());

                // Retry logic: Reconnect and retry after a delay
                if (attempts < MAX_RETRIES) {
                    System.out.println("Retrying (" + attempts + "/" + MAX_RETRIES + ")...");
                    try {
                        // Close the socket and reconnect
                        if (socket != null && !socket.isClosed()) {
                            socket.close();
                        }
                        connectToServer();
                    } catch (IOException retryException) {
                        System.err.println("Error during retry: " + retryException.getMessage());
                    }
                } else {
                    // If all retries fail, throw an exception or handle it appropriately
                    throw new RuntimeException("Failed to send request after " + MAX_RETRIES + " attempts.");
                }
            }

            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                throw new RuntimeException("Unexpected interrupt", e);
            }
        }

        return weather;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java GETClient <server:port> [stationId]");
            return;
        }

        String serverDetails = args[0];
        String stationId = args.length > 1 ? args[1] : null;

        // Check if the serverDetails contains "http://" and clean it if necessary
        if (serverDetails.startsWith("http://")) {
            // Remove "http://"
            serverDetails = serverDetails.substring(7);
        }

        // Split serverDetails into serverName and port
        String[] serverParts = serverDetails.split(":");
        if (serverParts.length != 2) {
            System.out.println("Error: Server details should be in the format 'servername:portnumber'.");
            return;
        }

        try {
            // Initialize the GETClient with the server details
            GETClient client = new GETClient(serverDetails);

            // Send the GET request, passing the stationId if provided
            HashMap<String, String> response = client.sendGetRequest(stationId);

            // Print the response (assuming the response is a map with weather data)
            if (response.get("body") != null) {
                WeatherEntry weather = new WeatherEntry(response.get("body"));
                for (String key : weather.body.keySet()) {
                    System.out.println(key + ": " + weather.body.get(key));
                }
            }
        } catch (IOException e) {
            System.out.println("Error: Unable to connect to the server. Please check the server details.");
        }
    }
}
