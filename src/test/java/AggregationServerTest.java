import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class AggregationServerTest {
    private static String jsonData = "";
    private static Thread serverThread;
    private final String serverDetails = "localhost:4567";

    // @BeforeEach
    // void setup() {
    @BeforeAll
    static void setup() {
        // Start the server in a separate thread
        serverThread = new Thread(() -> AggregationServer.main(new String[0]));
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
        // AggregationServer.shutdown();
        try {
            Files.deleteIfExists(AggregationServer.weatherFile.filePath);
        } catch (IOException e) {
            System.err.println("Error deleting weather storage");
        }
         AggregationServer.weatherData = new HashMap<>();
         AggregationServer.clock = new LamportClock();
    }

    @Test
    public void putWeather201Test() {
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

    @Test
    public void getWeatherTest() {
        String stationId = "IDS60901";

        try {
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

    @Test
    public void putWeatherLimitTest() {
        String stationId = "IDS60901";

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

    @Test
    public void recoveryAfterSaveDataToFileTest() {
        String stationId = "IDS60901";

        try {
            ContentServer contentServer = new ContentServer(serverDetails);

            // Send a PUT request
            contentServer.sendPutRequest(jsonData);

            // Interrupt and shut down the server
            System.out.println("Interrupting the server...");
            AggregationServer.shutdown();
            serverThread.interrupt();
            serverThread.join();  // Wait for the server to stop

            // Call the restart method
            serverThread = new Thread(AggregationServer::restart);
            serverThread.start();

            // Send a GET request
            GETClient client = new GETClient(serverDetails);
            HashMap<String, String> getResponse = client.sendGetRequest(stationId);

            // The data received by the client should be the same as the one sent by the content server
            assertEquals(jsonData, getResponse.get("body"));
        } catch (InterruptedException e) {
            System.out.println("Error occurred: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Server stops");
        }
    }

    @Test
    public void testRecoveryBetweenFilesWriteAndMove() {
        // Step 1: Initialize paths and the storage file
        Path filePath = Paths.get("target/data/temp_weather_data.json.tmp");
        String stationId = "IDS60901";
        Map<String, Deque<WeatherEntry>> weatherData = new HashMap<>();
        weatherData.put(stationId, new LinkedList<>());
        WeatherEntry entry = new WeatherEntry(jsonData);
        weatherData.get(stationId).offerLast(entry);

        try {
            // Step 2: Simulate server failure after `Files.write` but before `Files.move`
            Files.write(filePath, StorageFile.convertWeatherDataToJson(weatherData).getBytes());

            // Step 3: Simulate server crash before `Files.move`
            System.out.println("Interrupting the server...");
            AggregationServer.shutdown();
            serverThread.interrupt();
            serverThread.join();  // Wait for the server to stop

            // Call the restart method
            serverThread = new Thread(AggregationServer::restart);
            serverThread.start();

            // Step 4: Verify the recovered data from the temp file is correct
            GETClient client = new GETClient(serverDetails);
            HashMap<String, String> getResponse = client.sendGetRequest(stationId);
            System.out.println("body: " + getResponse.get("body"));
            assertEquals(jsonData, getResponse.get("body"));
        } catch (InterruptedException e) {
            System.out.println("Error occurred: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Server stops");
        }
    }
}
