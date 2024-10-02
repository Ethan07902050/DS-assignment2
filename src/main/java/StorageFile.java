import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class StorageFile {
    public final Path filePath;

    public StorageFile(String dirStr, String fileStr) throws IOException {
        Path dirPath = Paths.get(dirStr);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath); // Creates the directory if it doesn't exist
        }
        this.filePath = dirPath.resolve(fileStr);
    }

    public Map<String, Deque<WeatherEntry>> recoverDataFromFile() throws IOException {
        recoverIfNeeded();
        if (Files.exists(filePath)) {
            String content = new String(Files.readAllBytes(filePath));
            // Use a JSON parser to convert the content back into the map
            return parseJsonToWeatherData(content);
        }
        return new HashMap<>();
    }

    // Method to recover from failures
    public void recoverIfNeeded() throws IOException {
        // Look for any temporary files in the directory
        Path tempFile = filePath.resolveSibling("temp_" + filePath.getFileName().toString() + ".tmp");

        if (Files.exists(tempFile)) {
            System.out.println("Recovery in progress. Found temporary file: " + tempFile);

            try {
                // Check if the temporary file is valid (e.g., check file size or content)
                // For this example, we assume the temporary file is valid if it exists.

                // Atomically move the temporary file to the original file
                Files.move(tempFile, filePath, StandardCopyOption.ATOMIC_MOVE);
                System.out.println("Recovered from temporary file: " + tempFile);

            } catch (IOException e) {
                System.err.println("Error during recovery: " + e.getMessage());
                throw e;
            } finally {
                // Clean up the temp file if it still exists
                if (Files.exists(tempFile)) {
                    Files.delete(tempFile);
                }
            }
        } else {
            System.out.println("No recovery needed. No temporary file found.");
        }
    }

    // Method to save the data to a file (with atomic write)
    public void saveDataToFile(Map<String, Deque<WeatherEntry>> weatherData) throws IOException {
        // Convert the map to JSON or another format you prefer
        String json = convertWeatherDataToJson(weatherData);

        // Use atomic write to save to file
        Path tempFile = filePath.resolveSibling("temp_" + filePath.getFileName().toString() + ".tmp");
        try {
            Files.write(tempFile, json.getBytes());
            Files.move(tempFile, filePath, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("Error when saving weather data to file: " + e.getMessage());
            throw e;
        } finally {
            if (Files.exists(tempFile)) {
                Files.delete(tempFile);
            }
        }
    }

    // Method to convert the weather data to JSON (use any JSON library)
    public static String convertWeatherDataToJson(Map<String, Deque<WeatherEntry>> weatherData) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create(); // For pretty-printed JSON

        // Define the type of your weatherData (Map<String, Deque<WeatherEntry>>)
        Type type = new TypeToken<Map<String, Deque<WeatherEntry>>>() {}.getType();

        // Convert the weatherData map to a JSON string
        return gson.toJson(weatherData, type);
    }

    // Function to parse JSON content to Map<String, Deque<WeatherEntry>>
    public static Map<String, Deque<WeatherEntry>> parseJsonToWeatherData(String jsonContent) {
        Gson gson = new Gson();

        // Define the type of the map: Map<String, Deque<WeatherEntry>>
        Type type = new TypeToken<Map<String, Deque<WeatherEntry>>>() {}.getType();

        // Parse the JSON string to the specified type
        return gson.fromJson(jsonContent, type);
    }
}
