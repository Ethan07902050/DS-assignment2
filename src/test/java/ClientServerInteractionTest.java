import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class ClientServerInteractionTest {
    private static String jsonData1 = "";
    private static String jsonData2 = "";
    private final String serverDetails = "localhost:4567";

    private static String readFileAsJson(String filePath) {
        try {
            // Parse the file
            Map<String, String> dataMap = ContentServer.parseFile(filePath);

            // Convert to JSON
            return new Gson().toJson(dataMap);
        } catch (IOException e) {
            return "";
        }
    }

    @BeforeAll
    static void setup() {
        // Start the server in a separate thread
        Thread serverThread = new Thread(() -> AggregationServer.main(new String[0]));
        serverThread.start();

        jsonData1 = readFileAsJson("weather_1.txt");
        jsonData2 = readFileAsJson("weather_2.txt");
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
    public void getWeatherLamportClockTest() {
        String stationId = "IDS60901";

        try {
            ContentServer contentServer = new ContentServer(serverDetails);
            GETClient client = new GETClient(serverDetails);

            // Send a PUT request
            HashMap<String, String> putResponse = contentServer.sendPutRequest(jsonData1);
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
            assertEquals(jsonData1, getResponse.get("body"));

            // The Client send a request with lamport time 1
            // The lamport time of AggregationServer is 4 when receiving the request,
            // and is increased to 5 before sending the response
            // Thus the lamport time of Client when receiving response should be 6
            assertEquals(6, client.clock.getTime());
        } catch (IOException e) {
            System.out.println("Error occurred: " + e.getMessage());
        }
    }

    @Test
    public void getOperationMultipleClientsTest() {
        String stationId = "IDS60901";

        try {
            ContentServer contentServer = new ContentServer(serverDetails);

            // Send a PUT request to add data
            contentServer.sendPutRequest(jsonData1);

            // Simulate multiple GET clients
            GETClient client1 = new GETClient(serverDetails);
            GETClient client2 = new GETClient(serverDetails);
            GETClient client3 = new GETClient(serverDetails);

            // Send GET requests simultaneously
            HashMap<String, String> getResponse1 = client1.sendGetRequest(stationId);
            HashMap<String, String> getResponse2 = client2.sendGetRequest(stationId);
            HashMap<String, String> getResponse3 = client3.sendGetRequest(stationId);

            // All GET requests should receive the correct status and data
            assertEquals(200, Integer.parseInt(getResponse1.get("Status-Code")));
            assertEquals(jsonData1, getResponse1.get("body"));

            assertEquals(200, Integer.parseInt(getResponse2.get("Status-Code")));
            assertEquals(jsonData1, getResponse2.get("body"));

            assertEquals(200, Integer.parseInt(getResponse3.get("Status-Code")));
            assertEquals(jsonData1, getResponse3.get("body"));

        } catch (IOException e) {
            System.out.println("Error occurred: " + e.getMessage());
        }
    }

    @Test
    public void interleavedPutAndGetTest() {
        String stationId = "IDS60901";

        try {
            ContentServer contentServer1 = new ContentServer(serverDetails);
            ContentServer contentServer2 = new ContentServer(serverDetails);
            GETClient client = new GETClient(serverDetails);

            // 1. First PUT request
            HashMap<String, String> putResponse1 = contentServer1.sendPutRequest(jsonData1);
            int statusCode = Integer.parseInt(putResponse1.get("Status-Code"));
            assertEquals(201, statusCode);  // Ensure first PUT succeeds

            // Ensure Lamport clock synchronization after the first PUT
            assertEquals(4, contentServer1.clock.getTime());

            // 2. GET request - must return updated data from the first PUT
            HashMap<String, String> getResponse = client.sendGetRequest(stationId);
            statusCode = Integer.parseInt(getResponse.get("Status-Code"));
            assertEquals(200, statusCode);
            assertEquals(jsonData1, getResponse.get("body"));  // The GET request should return the correct data
            assertEquals(6, client.clock.getTime());  // Ensure GET clock is correct

            // 3. Second PUT request
            HashMap<String, String> putResponse2 = contentServer2.sendPutRequest(jsonData2);
            statusCode = Integer.parseInt(putResponse2.get("Status-Code"));
            assertEquals(201, statusCode);  // Ensure second PUT succeeds

            // Ensure Lamport clock synchronization after second PUT
            assertEquals(8, contentServer2.clock.getTime());

        } catch (IOException e) {
            System.out.println("Error occurred: " + e.getMessage());
        }
    }

    @Test
    public void multipleContentServersPutTest() {
        String stationId1 = "IDS60901";
        String stationId2 = "IDS60902";

        try {
            ContentServer contentServer1 = new ContentServer(serverDetails);
            ContentServer contentServer2 = new ContentServer(serverDetails);

            // Simulate two simultaneous PUT requests from two different content servers
            HashMap<String, String> putResponse1 = contentServer1.sendPutRequest(jsonData1);
            HashMap<String, String> putResponse2 = contentServer2.sendPutRequest(jsonData2);

            // Ensure both PUTs were successful
            assertEquals(201, Integer.parseInt(putResponse1.get("Status-Code")));
            assertEquals(201, Integer.parseInt(putResponse2.get("Status-Code")));

            // Verify that the Lamport clock times are serialized correctly
            // ContentServer1 should have a lower Lamport time than ContentServer2
            assertTrue(contentServer1.clock.getTime() < contentServer2.clock.getTime());

            // Retrieve the latest data from the server to ensure the final state
            GETClient client = new GETClient(serverDetails);

            HashMap<String, String> getResponse = client.sendGetRequest(stationId1);
            int statusCode = Integer.parseInt(getResponse.get("Status-Code"));
            assertEquals(200, statusCode);
            assertEquals(jsonData1, getResponse.get("body"));

            getResponse = client.sendGetRequest(stationId2);
            statusCode = Integer.parseInt(getResponse.get("Status-Code"));
            assertEquals(200, statusCode);
            assertEquals(jsonData2, getResponse.get("body"));
        } catch (IOException e) {
            System.out.println("Error occurred: " + e.getMessage());
        }
    }

}
