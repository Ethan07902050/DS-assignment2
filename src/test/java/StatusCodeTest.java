import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class StatusCodeTest {
    private static String jsonData = "";
    private final String serverDetails = "localhost:4567";

    @BeforeAll
    static void setup() {
        // Start the server in a separate thread
        Thread serverThread = new Thread(() -> AggregationServer.main(new String[0]));
        serverThread.start();

        String filePath = "weather_1.txt";
        try {
            // Parse the file
            Map<String, String> dataMap = ContentServer.parseFile(filePath);

            // Convert to JSON
            jsonData = new Gson().toJson(dataMap);
        } catch (IOException e) {
            System.out.println("Cannot parse file " + filePath + ": " + e.getMessage());
        }
    }

    @AfterEach
    public void cleanUpServer() {
        try {
            Files.deleteIfExists(AggregationServer.weatherFile.filePath);
        } catch (IOException e) {
            System.err.println("Error deleting weather storage");
        }
        AggregationServer.weatherData = new HashMap<>();
        AggregationServer.clock = new LamportClock();
    }

    @Test
    public void putWeather200Test() {
        try {
            ContentServer contentServer = new ContentServer(serverDetails);

            HashMap<String, String> response = contentServer.sendPutRequest(jsonData);
            int statusCode = Integer.parseInt(response.get("Status-Code"));
            assertEquals(201, statusCode);

            // The AggregationServer should return 200 for the second request from the same ContentServer
            response = contentServer.sendPutRequest(jsonData);
            statusCode = Integer.parseInt(response.get("Status-Code"));
            assertEquals(200, statusCode);
        } catch (IOException e) {
            System.out.println("Error occurred: " + e.getMessage());
        }
    }

    @Test
    public void putWeather201Test() {
        try {
            ContentServer contentServer = new ContentServer(serverDetails);
            HashMap<String, String> response = contentServer.sendPutRequest(jsonData);
            int statusCode = Integer.parseInt(response.get("Status-Code"));
            assertEquals(201, statusCode);  // 201 Created
        } catch (IOException e) {
            System.out.println("Error occurred: " + e.getMessage());
        }
    }

    @Test
    public void putWeather204Test() {
        try {
            ContentServer contentServer = new ContentServer(serverDetails);

            String emptyMessage = "";
            HashMap<String, String> response = contentServer.sendPutRequest(emptyMessage);
            int statusCode = Integer.parseInt(response.get("Status-Code"));
            assertEquals(204, statusCode);
        } catch (IOException e) {
            System.out.println("Error occurred: " + e.getMessage());
        }
    }

    @Test
    public void postWeather400Test() {
        try {
            ContentServer contentServer = new ContentServer(serverDetails);
            OutputStream outputStream = contentServer.socket.getOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream));

            writer.println("POST /weather.json HTTP/1.1");
            writer.println("Lamport-Time: 1");
            writer.println();
            writer.flush();

            HashMap<String, String> response = RequestResponseHandler.parseResponse(contentServer.socket);
            int statusCode = Integer.parseInt(response.get("Status-Code"));
            assertEquals(400, statusCode);
        } catch (IOException e) {
            System.out.println("Error occurred: " + e.getMessage());
        }
    }

    @Test
    public void putWeather500Test() {
        try {
            ContentServer contentServer = new ContentServer(serverDetails);

            String malformedJson = jsonData.substring(0, jsonData.length() - 1);
            HashMap<String, String> response = contentServer.sendPutRequest(malformedJson);
            int statusCode = Integer.parseInt(response.get("Status-Code"));
            assertEquals(500, statusCode);
        } catch (IOException e) {
            System.out.println("Error occurred: " + e.getMessage());
        }
    }
}
