import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class AggregationServerTest {
    @BeforeAll
    static void setup() {
        // Start the server in a separate thread
        Thread serverThread = new Thread(() -> {
            AggregationServer.main(new String[0]);
        });
        serverThread.start();
    }

    @AfterEach
    public void cleanUpServerStorage() {
        AggregationServer.weatherData = new HashMap<>();
    }

    @Test
    public void putWeather201Test() {
        String serverDetails = "localhost:12345";
        String filePath = "weather.txt";

        String jsonData = "";
        try {
            // Parse the file
            Map<String, String> dataMap = ContentServer.parseFile(filePath);

            // Convert to JSON
            jsonData = new Gson().toJson(dataMap);
        } catch (IOException e) {
            System.out.println("Cannot parse file " + filePath + ": " + e.getMessage());
        }
        assertNotEquals("", jsonData);

        try {
            // Create ContentServer
            ContentServer contentServer = new ContentServer(serverDetails);

            // Try sending the PUT request
            HashMap<String, String> response = contentServer.sendPutRequest(jsonData);
            int statusCode = Integer.parseInt(response.get("Status-Code"));

            assertEquals(201, statusCode);
        } catch (IOException e) {
            // Handle both ContentServer creation and PUT request exceptions here
            System.out.println("Error occurred: " + e.getMessage());
        }
    }

    @Test
    public void putWeather200Test() {
        String serverDetails = "localhost:12345";
        String filePath = "weather.txt";

        String jsonData = "";
        try {
            // Parse the file
            Map<String, String> dataMap = ContentServer.parseFile(filePath);

            // Convert to JSON
            jsonData = new Gson().toJson(dataMap);
        } catch (IOException e) {
            System.out.println("Cannot parse file " + filePath + ": " + e.getMessage());
        }
        assertNotEquals("", jsonData);

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
    public void getWeatherTest() {
        String serverDetails = "localhost:12345";
        String filePath = "weather.txt";

        try {
            // Parse the file
            Map<String, String> dataMap = ContentServer.parseFile(filePath);

            // Convert to JSON
            String jsonData = new Gson().toJson(dataMap);

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
            HashMap<String, String> getResponse = client.sendGetRequest(dataMap.get("id"));
            statusCode = Integer.parseInt(getResponse.get("Status-Code"));

            assertEquals(200, statusCode);
            // The data received by the client should be the same as the one sent by the body server
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

    @Test
    public void putWeatherLimitTest() {
        String serverDetails = "localhost:12345";
        String filePath = "weather.txt";
        String stationId = "IDS60901";

        String jsonData = "";
        try {
            // Parse the file
            Map<String, String> dataMap = ContentServer.parseFile(filePath);

            // Convert to JSON
            jsonData = new Gson().toJson(dataMap);
        } catch (IOException e) {
            System.out.println("Cannot parse file " + filePath + ": " + e.getMessage());
        }
        assertNotEquals("", jsonData);

        try {
            // Create ContentServer
            ContentServer contentServer = new ContentServer(serverDetails);

            // Loop to send 25 PUT requests to the Aggregation Server
            for (int i = 1; i <= 25; i++) {
                contentServer.sendPutRequest(jsonData);
            }
        } catch (IOException e) {
            // Handle both ContentServer creation and PUT request exceptions here
            System.out.println("Error occurred: " + e.getMessage());
        }

        // Verify that exactly 20 records are kept for the station
        Deque<WeatherEntry> weatherList = AggregationServer.weatherData.get(stationId);
        assertEquals(20, weatherList.size());
    }
}
