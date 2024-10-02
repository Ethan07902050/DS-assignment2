import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class FailureRecoveryTest {
    private static String jsonData = "";
    private static Thread serverThread;
    private final String serverDetails = "localhost:4567";

    @BeforeAll
    static void setup() {
        // Start the server in a separate thread
        serverThread = new Thread(() -> AggregationServer.main(new String[0]));
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

    @AfterAll
    static void shutdownServer() throws InterruptedException {
        AggregationServer.shutdown();
        serverThread.interrupt();
        serverThread.join();
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
    public void recoveryBetweenFilesWriteAndMoveTest() {
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

    @Test
    public void clientRetryOnErrorTest() {
        String stationId = "IDS60901";

        try {
            ContentServer contentServer = new ContentServer(serverDetails);
            GETClient client = new GETClient(serverDetails);

            // Send a PUT request
            contentServer.sendPutRequest(jsonData);

            // Simulate server shutdown
            System.out.println("Interrupting the server...");
            AggregationServer.shutdown();
            serverThread.interrupt();
            serverThread.join();  // Wait for the server to stop

            // Create a thread for running the GET request
            Thread clientRequestThread = new Thread(() -> {
                // Send a GET request
                HashMap<String, String> getResponse = client.sendGetRequest(stationId);

                // The data received by the client should be the same as the one sent by the content server
                assertEquals(jsonData, getResponse.get("body"));
            });

            // Start the client request thread
            clientRequestThread.start();

            // Call the restart method
            serverThread = new Thread(AggregationServer::restart);
            serverThread.start();

            // Wait for the client request thread to finish
            clientRequestThread.join();
        } catch (InterruptedException e) {
            System.out.println("Error occurred: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Server stops");
        }
    }
}
