import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;

public class ContentServer {
    private String serverName;
    private int port;
    public Socket socket;
    public LamportClock clock;

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java ContentServer <server:port> <file-path>");
            return;
        }

        String serverDetails = args[0];
        String filePath = args[1];

        try {
            // Parse the file
            Map<String, String> dataMap = parseFile(filePath);

            // Convert to JSON
            String jsonData = new Gson().toJson(dataMap);

            // Send PUT request using Sockets
            ContentServer contentServer = new ContentServer(serverDetails);
            contentServer.sendPutRequest(jsonData);

        } catch (Exception e) {
            System.out.println("Error: Unable to connect to the server. Please check the server details." + e.getMessage());
        }
    }

    public ContentServer(String serverDetails) throws IOException {
        String[] serverParts = serverDetails.split(":");
        this.serverName = serverParts[0];
        this.port = Integer.parseInt(serverParts[1]);
        this.socket = new Socket(serverName, port);
        this.clock = new LamportClock();
    }

    public static Map<String, String> parseFile(String filePath) throws IOException {
        // Use ClassLoader to get the resource
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(filePath);

        // Read the file line by line
        Map<String, String> dataMap = new HashMap<>();
        // Check that the resource is not null
        if (inputStream == null) return dataMap;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Process each line
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        dataMap.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error parsing file: " + e.getMessage());
        }

        // Validate presence of 'id' key
        if (!dataMap.containsKey("id")) {
            throw new IllegalArgumentException("Missing 'id' in the input file.");
        }

        return dataMap;
    }

    public HashMap<String, String> sendPutRequest(String jsonData) throws IOException {
        clock.increaseTime();
        RequestResponseHandler.sendPutRequest(socket, jsonData, clock.getTime());

        HashMap<String, String> response = RequestResponseHandler.parseResponse(socket);
        if (response.get("Lamport-Time") != null) {
            int receivedTime = Integer.parseInt(response.get("Lamport-Time"));
            clock.increaseTime(receivedTime);
        }
        return response;
    }
}
