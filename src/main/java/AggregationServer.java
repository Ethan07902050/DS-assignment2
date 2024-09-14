import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.concurrent.*;
import java.lang.reflect.Type;

import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;
import org.json.simple.parser.JSONParser;

public class AggregationServer {
    private static final int PORT = 12345;
    private static final BlockingQueue<Task> taskQueue = new PriorityBlockingQueue<>();
    private static volatile boolean running = true;
    private static ServerSocket serverSocket;
    public static LamportClock clock = new LamportClock();
    public static HashMap<String, HashMap<String, String>> weatherStorage;

    public static void main(String[] args) {
        ExecutorService clientHandlingPool = Executors.newCachedThreadPool();
        weatherStorage = new HashMap<>();

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

        public void process() {
            try {
                clock.increaseTime();
                if ("PUT".equals(message.get("operation"))) {
                    // Define the type for HashMap<String, String>
                    Type type = new TypeToken<HashMap<String, String>>() {}.getType();

                    HashMap<String, String> weather = new Gson().fromJson(message.get("body"), type);
                    String stationId = weather.get("id");
                    int statusCode;
                    if (weatherStorage.containsKey(stationId)) {
                        statusCode = 200;
                    } else {
                        statusCode = 201;
                    }
                    RequestResponseHandler.sendResponse(
                        clientSocket,
                        statusCode,
                        weather,
                        clock.getTime()
                    );
                    weatherStorage.put(stationId, weather);
                } else if ("GET".equals(message.get("operation"))) {
                    String id = message.get("id");
                    if (weatherStorage.containsKey(id)) {
                        HashMap<String, String> weather = weatherStorage.get(id);
                        RequestResponseHandler.sendResponse(
                            clientSocket,
                            200,
                            weather,
                            clock.getTime()
                        );
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
