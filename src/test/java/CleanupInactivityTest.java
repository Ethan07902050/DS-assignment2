import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class CleanupInactivityTest {
    private static String jsonData = "";
    private final String serverDetails = "localhost:4567";

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
    public void testRemovalAfterInactivity() {
        String stationId = "IDS60901";

        try {
            ContentServer contentServer = new ContentServer(serverDetails);
            GETClient client = new GETClient(serverDetails);

            // Step 1: Send a PUT request with weather data
            HashMap<String, String> putResponse = contentServer.sendPutRequest(jsonData);
            int statusCode = Integer.parseInt(putResponse.get("Status-Code"));
            assertEquals(201, statusCode);  // Verify that the data was added successfully

            // Step 2: Immediately send a GET request to verify that the data exists
            HashMap<String, String> getResponse = client.sendGetRequest(stationId);
            statusCode = Integer.parseInt(getResponse.get("Status-Code"));
            assertEquals(200, statusCode);  // Data exists, should return 200 OK
            assertEquals(jsonData, getResponse.get("body"));  // Ensure data matches

            // Step 3: Wait for more than 30 seconds to allow for cleanup of stale data
            // Since waiting for actual 30 seconds is impractical in tests, you can mock or fast-forward the time.
            // However, for simplicity, we'll use Thread.sleep() here.
            Thread.sleep(35000);  // Wait for 31 seconds (slightly more than 30 seconds)

            // Step 4: Send a GET request again to check if the data has been removed
            getResponse = client.sendGetRequest(stationId);
            statusCode = Integer.parseInt(getResponse.get("Status-Code"));

            // The server should have removed the data since there was no communication for 30 seconds
            assertEquals(204, statusCode);  // Verify that the data has been removed

        } catch (IOException | InterruptedException e) {
            System.out.println("Error occurred: " + e.getMessage());
        }
    }

}
