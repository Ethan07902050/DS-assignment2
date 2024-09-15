import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.HashMap;
import java.util.concurrent.*;
import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.json.simple.parser.JSONParser;

public class AggregationServer {
    private static final int PORT = 12345;
    private static final BlockingQueue<Task> taskQueue = new PriorityBlockingQueue<>();
    private static volatile boolean running = true;
    private static ServerSocket serverSocket;

    // Map to store the last 20 updates for each station
    public static Map<String, Deque<WeatherEntry>> weatherData = new HashMap<>();
    private static final int MAX_UPDATES = 20;

    public static LamportClock clock = new LamportClock();

    public static void main(String[] args) {
        ExecutorService clientHandlingPool = Executors.newCachedThreadPool();

        // Start the task processing thread
        Thread processingThread = new Thread(() -> {
            while (running || !taskQueue.isEmpty()) {
                try {
                    Task task = taskQueue.poll(1, TimeUnit.SECONDS);
                    if (task != null) {
                        task.process();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        processingThread.start();

        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server started and listening on port " + PORT);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientHandlingPool.execute(new ClientHandler(clientSocket));
                } catch (SocketException e) {
                    if (!running) {
                        System.out.println("Server stopped accepting new connections.");
                    } else {
                        throw e;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            clientHandlingPool.shutdown();
            try {
                if (!clientHandlingPool.awaitTermination(60, TimeUnit.SECONDS)) {
                    clientHandlingPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                clientHandlingPool.shutdownNow();
            }
            // System.out.println("Server shut down.");
        }
    }

    public static void shutdown() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final JSONParser jsonParser = new JSONParser();

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                // noinspection InfiniteLoopStatement
                while(true) {
                    HashMap<String, String> request = RequestResponseHandler.parseRequest(clientSocket);
                    int receivedTime = Integer.parseInt(request.get("Lamport-Time"));
                    clock.increaseTime(receivedTime);

                    // Add the task to the priority queue
                    taskQueue.put(new Task(clientSocket, request, receivedTime));
                }
            } catch (SocketException e) {
                if (running) {
                    System.out.println("Client socket was closed unexpectedly: " + e.getMessage());
                } else {
                    System.out.println("Server shutting down, socket closed.");
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class Task implements Comparable<Task> {
        private final HashMap<String, String> message;
        private final Socket clientSocket;
        private final int priority;

        public Task(Socket clientSocket, HashMap<String, String> message, int priority) {
            this.clientSocket = clientSocket;
            this.message = message;
            this.priority = priority;
        }

        // Method to add new weather data to the map
        private int addWeatherData(WeatherEntry entry) {
            String stationId = entry.body.get("id");
            int statusCode;

            // Get the deque for the stationId, or create it if it doesn't exist
            if (weatherData.containsKey(stationId)) {
                statusCode = 200;
            } else {
                statusCode = 201;
                weatherData.put(stationId, new LinkedList<>());
            }

            Deque<WeatherEntry> updates = weatherData.get(stationId);

            // If the deque is already full (20 entries), remove the oldest one
            if (updates.size() >= MAX_UPDATES) {
                updates.pollFirst(); // Removes the oldest update
            }

            // Add the new entry to the deque
            updates.offerLast(entry);
            return statusCode;
        }

        // Method to save the data to a file (with atomic write)
        private void saveDataToFile() throws IOException {
            Path dirPath = Paths.get("data");
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath); // Creates the directory if it doesn't exist
            }
            Path filePath = dirPath.resolve("weather_data.json");

            // Convert the map to JSON or another format you prefer
            String json = convertWeatherDataToJson();

            // Use atomic write to save to file
            Path tempFile = Files.createTempFile(filePath.getParent(), "temp_", ".tmp");
            try {
                Files.write(tempFile, json.getBytes());
                Files.move(tempFile, filePath, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                System.out.println("Error when saving weather data to file: " + e.getMessage());
                throw e;
            } finally {
                if (Files.exists(tempFile)) {
                    Files.delete(tempFile);
                }
            }
        }

        // Method to convert the weather data to JSON (use any JSON library)
        private String convertWeatherDataToJson() {
            Gson gson = new GsonBuilder().setPrettyPrinting().create(); // For pretty-printed JSON

            // Define the type of your weatherData (Map<String, Deque<WeatherEntry>>)
            Type type = new TypeToken<Map<String, Deque<WeatherEntry>>>() {}.getType();

            // Convert the weatherData map to a JSON string
            return gson.toJson(weatherData, type);
        }

        public void process() {
            try {
                clock.increaseTime();
                if ("PUT".equals(message.get("operation"))) {
                    WeatherEntry weather = new WeatherEntry(message.get("body"));
                    int statusCode = addWeatherData(weather);
                    saveDataToFile();

                    RequestResponseHandler.sendResponse(
                        clientSocket,
                        statusCode,
                        weather.body,
                        clock.getTime()
                    );
                } else if ("GET".equals(message.get("operation"))) {
                    String id = message.get("id");
                    if (weatherData.containsKey(id)) {
                        Deque<WeatherEntry> weatherList = weatherData.get(id);
                        WeatherEntry latestWeatherEntry = weatherList.peekLast();

                        if (latestWeatherEntry != null) {
                            RequestResponseHandler.sendResponse(
                                    clientSocket,
                                    200,
                                    latestWeatherEntry.body,
                                    clock.getTime());
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Error when sending response to ContentServer: " + e.getMessage());
            }

        }

        @Override
        public int compareTo(Task other) {
            return Integer.compare(this.priority, other.priority);
        }
    }
}
