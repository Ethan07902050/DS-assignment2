import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.HashMap;

public class WeatherEntry {
    public String timestamp;
    public HashMap<String, String> body;

    // ISO 8601 format for consistency
    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // Initialize from a json string
    public WeatherEntry(String body) {
        Type type = new TypeToken<HashMap<String, String>>() {}.getType();
        this.body = new Gson().fromJson(body, type);
        this.timestamp = LocalDateTime.now().format(formatter);
    }

    // Method to retrieve timestamp as LocalDateTime when needed
    public LocalDateTime getTimestampAsLocalDateTime() {
        return LocalDateTime.parse(timestamp, formatter);  // Convert string back to LocalDateTime
    }
}
