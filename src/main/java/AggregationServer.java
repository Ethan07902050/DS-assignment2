import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.Iterator;
import java.time.Duration;
import java.time.LocalDateTime;


public class AggregationServer {
    private static BlockingQueue<Task> taskQueue;
    private static ScheduledExecutorService scheduler;
    private static volatile boolean running;
    private static ServerSocket serverSocket;
    private static final int MAX_UPDATES = 20;
    private static final int DATA_EXPIRATION_SECONDS = 30;  // Remove data if no communication for 30 seconds
    private static final int CLEANUP_INTERVAL_SECONDS = 3; // Cleanup interval: 10 seconds

    // List to store active client sockets
    private static List<Socket> activeClientSockets;

    // Map to store the last 20 updates for each station
    public static Map<String, Deque<WeatherEntry>> weatherData;
    public static StorageFile weatherFile;
    public static LamportClock clock = new LamportClock();

    public static void main(String[] args) {
        running = true;
        taskQueue = new PriorityBlockingQueue<>();
        activeClientSockets = new CopyOnWriteArrayList<>();
        ExecutorService clientHandlingPool = Executors.newCachedThreadPool();

        try {
            weatherFile = new StorageFile("target/data", "weather_data.json");
            weatherData = weatherFile.recoverDataFromFile();
        } catch (IOException e) {
            System.err.println("Error creating local storage: " + e.getMessage());
        }

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

        // Start the cleanup thread
        startCleanupThread();

        int PORT;
        if (args.length != 0) PORT = Integer.parseInt(args[0]);
        else PORT = 4567;
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server started and listening on port " + PORT);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    activeClientSockets.add(clientSocket);  // Add to list of active sockets
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
            System.out.println("Error occurred: " + e.getMessage());
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

    public static void startCleanupThread() {
        // Create a scheduled executor that runs the cleanup task periodically
        scheduler = Executors.newScheduledThreadPool(1);

        // Schedule the cleanup task at fixed intervals
        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("Running cleanup task...");
            LocalDateTime now = LocalDateTime.now();

            // Iterate over the weatherData map and remove stations that haven't communicated in 30 seconds
            Iterator<Map.Entry<String, Deque<WeatherEntry>>> iterator = weatherData.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<String, Deque<WeatherEntry>> entry = iterator.next();
                Deque<WeatherEntry> updates = entry.getValue();

                // Get the timestamp of the last update for this station
                WeatherEntry latestEntry = updates.peekLast();
                if (latestEntry != null) {
                    LocalDateTime lastCommunicationTime = latestEntry.getTimestampAsLocalDateTime();
                    Duration duration = Duration.between(lastCommunicationTime, now);

                    // If the last communication is older than DATA_EXPIRATION_SECONDS, remove the station
                    if (duration.getSeconds() > DATA_EXPIRATION_SECONDS) {
                        System.out.println("Removing station " + entry.getKey() + " due to inactivity.");
                        iterator.remove();  // Remove the station from weatherData
                    }
                }
            }

            // Save the updated weather data back to the file
            try {
                weatherFile.saveDataToFile(weatherData);
            } catch (IOException e) {
                System.err.println("Error saving weather data during cleanup: " + e.getMessage());
            }

        }, 0, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS); // Run every 10 seconds
    }

    public static void restart() {
        running = true;  // Reset the running flag
        // Reinitialize the server socket or any necessary components here if needed
        main(new String[0]);  // Restart the server's main method
    }

    public static void shutdown() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            // Close all active client sockets
            for (Socket clientSocket : activeClientSockets) {
                try {
                    if (!clientSocket.isClosed()) {
                        clientSocket.close();
                    }
                } catch (IOException e) {
                    System.out.println("Error closing client socket: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Error occurred: " + e.getMessage());
        }

        scheduler.shutdown(); // Initiates an orderly shutdown

        try {
            // Wait for any running tasks to finish
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                System.err.println("Forcing shutdown due to timeout...");
                scheduler.shutdownNow(); // Forcefully shut down if tasks didn't finish in time
            }
        } catch (InterruptedException e) {
            System.err.println("Shutdown interrupted, forcing immediate shutdown...");
            scheduler.shutdownNow(); // Forcefully shut down if interrupted
        }

        System.out.println("Cleanup thread stopped.");
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        public static boolean isValidJson(String json) {
            try {
                JsonElement element = JsonParser.parseString(json);
                return element.isJsonObject() || element.isJsonArray();
            } catch (JsonSyntaxException e) {
                return false;  // The string does not conform to JSON format
            }
        }

        @Override
        public void run() {
            try {
                // noinspection InfiniteLoopStatement
                while(true) {
                    HashMap<String, String> request = RequestResponseHandler.parseRequest(clientSocket);
                    String op = request.get("operation");
                    String body = request.get("body");
                    String id = request.get("id");
                    if (("PUT".equals(op) && "".equals(body)) || ("GET".equals(op) && !weatherData.containsKey(id))) {
                        RequestResponseHandler.sendResponse(clientSocket, 204, null, -1);
                    } else if ((!"PUT".equals(op)) && (!"GET".equals(op))) {
                        RequestResponseHandler.sendResponse(clientSocket, 400, null, -1);
                    } else if ("PUT".equals(op) && !isValidJson(body)) {
                        RequestResponseHandler.sendResponse(clientSocket, 500, null, -1);
                    } else {
                        int receivedTime = Integer.parseInt(request.get("Lamport-Time"));
                        clock.increaseTime(receivedTime);

                        // Add the task to the priority queue
                        taskQueue.put(new Task(clientSocket, request, receivedTime));
                    }
                }
            } catch (SocketException e) {
                if (running) {
                    System.out.println("Client socket was closed unexpectedly: " + e.getMessage());
                } else {
                    System.out.println("Server shutting down, socket closed.");
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                activeClientSockets.remove(clientSocket);  // Remove from active list when done
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

        public void process() {
            try {
                clock.increaseTime();
                if ("PUT".equals(message.get("operation"))) {
                    WeatherEntry weather = new WeatherEntry(message.get("body"));
                    int statusCode = addWeatherData(weather);
                    weatherFile.saveDataToFile(weatherData);

                    RequestResponseHandler.sendResponse(clientSocket,
                            statusCode,
                            weather.body,
                            clock.getTime());
                } else if ("GET".equals(message.get("operation"))) {
                    String id = message.get("id");
                    if (weatherData.containsKey(id)) {
                        Deque<WeatherEntry> weatherList = weatherData.get(id);
                        WeatherEntry latestWeatherEntry = weatherList.peekLast();

                        if (latestWeatherEntry != null) {
                            RequestResponseHandler.sendResponse(clientSocket,
                                    200,
                                    latestWeatherEntry.body,
                                    clock.getTime());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error when sending response to ContentServer: " + e.getMessage());
            }
        }

        @Override
        public int compareTo(Task other) {
            return Integer.compare(this.priority, other.priority);
        }
    }
}
