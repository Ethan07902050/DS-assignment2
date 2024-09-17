import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class LamportClockTest {
    private static String jsonData = "";

    @BeforeAll
    static void setup() {
        // Start the server in a separate thread
        Thread serverThread = new Thread(() -> AggregationServer.main(new String[0]));
        serverThread.start();

        String filePath = "weather.txt";
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
    public void getWeatherTest() {
        String stationId = "IDS60901";

        try {
            String serverDetails = "localhost:4567";
            ContentServer contentServer = new ContentServer(serverDetails);
            GETClient client = new GETClient(serverDetails);

            // Send a PUT request
            HashMap<String, String> putResponse = contentServer.sendPutRequest(jsonData);
            int statusCode = Integer.parseInt(putResponse.get("Status-Code"));
            assertEquals(201, statusCode);

            // The ContentServer send a request with lamport time 1
            // The lamport time of AggregationServer is 2 when receiving the request,
            // and is increased by 1 when sending the response
            // Thus the lamport time of ContentServer when receiving response should be 4
            assertEquals(4, contentServer.clock.getTime());

            // Send a GET request
            HashMap<String, String> getResponse = client.sendGetRequest(stationId);
            statusCode = Integer.parseInt(getResponse.get("Status-Code"));

            assertEquals(200, statusCode);
            // The data received by the client should be the same as the one sent by the content server
            assertEquals(jsonData, getResponse.get("body"));

            // The Client send a request with lamport time 1
            // The lamport time of AggregationServer is 4 when receiving the request,
            // and is increased by 5 when sending the response
            // Thus the lamport time of Client when receiving response should be 6
            assertEquals(6, client.clock.getTime());
        } catch (IOException e) {
            System.out.println("Error occurred: " + e.getMessage());
        }
    }
}
