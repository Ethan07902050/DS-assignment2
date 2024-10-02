import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class DataManagementTest {
    private static String jsonData = "";
    private static String port = "5678";
    private final String serverDetails = "localhost:" + port;
    private static Thread serverThread;

    @BeforeAll
    static void setup() {
        // Start the server in a separate thread
        String[] arguments = { port };
        serverThread = new Thread(() -> AggregationServer.main(arguments));
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
}
