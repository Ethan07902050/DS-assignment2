import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class RequestResponseHandler {
    private static void readBody(BufferedReader reader, HashMap<String, String> resultMap)
            throws IOException {
        String line;
        StringBuilder bodyBuilder = new StringBuilder();

        // Parse headers
        int contentLength = 0;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            if (line.startsWith("Content-Length:")) {
                contentLength = Integer.parseInt(line.split(":")[1].trim());
                resultMap.put("Content-Length", String.valueOf(contentLength));
            } else {
                String[] items = line.split(":");
                resultMap.put(items[0], items[1].trim());
            }
        }

        // Read the body based on Content-Length
        for (int i = 0; i < contentLength; i++) {
            bodyBuilder.append((char) reader.read());
        }
        resultMap.put("body", bodyBuilder.toString());
    }

    public static void sendResponse(
        Socket socket,
        int statusCode,
        HashMap<String, String> message,
        int lamportTime
    ) throws IOException {
        String statusText = "";
        if (statusCode == 200) statusText = "OK";
        else if (statusCode == 201) statusText = "Created";

        String jsonData = new Gson().toJson(message);

        // Calculate Content-Length
        int contentLength = jsonData.getBytes(StandardCharsets.UTF_8).length;

        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        out.println("HTTP/1.1 " + statusCode + " " + statusText);
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + contentLength);
        out.println("Lamport-Time: " + lamportTime);
        out.println();
        out.println(jsonData);
        out.flush();
    }

    public static void sendGetRequest(Socket socket, String stationId, int lamportTime) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream));
        writer.println("GET /weather.json?id=" + stationId + " HTTP/1.1");
        writer.println("Lamport-Time: " + lamportTime);
        writer.println();
        writer.flush();
    }

    public static void sendPutRequest(Socket socket, String jsonData, int lamportTime) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream));

        // Calculate Content-Length
        int contentLength = jsonData.getBytes(StandardCharsets.UTF_8).length;

        // Construct the HTTP PUT request manually
        writer.println("PUT /weather.json HTTP/1.1");
        writer.println("User-Agent: ATOMClient/1/0");
        writer.println("Content-Type: application/json");
        writer.println("Content-Length: " + contentLength);
        writer.println("Lamport-Time: " + lamportTime);
        writer.println();
        writer.println(jsonData);
        writer.flush();
    }

    // Function to parse PUT request from a client
    public static HashMap<String, String> parseRequest(Socket socket) throws IOException {
        HashMap<String, String> resultMap = new HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String line;

        // Parse the request line
        line = reader.readLine();
        if (line != null) {
            if (line.startsWith("PUT")) {
                resultMap.put("operation", "PUT");
            } else if (line.startsWith("GET")) {
                resultMap.put("operation", "GET");
                String path = line.split(" ")[1];
                String param = path.split("\\?")[1];
                String id = param.split("=")[1];
                resultMap.put("id", id);
            }
        }

        readBody(reader, resultMap);
        return resultMap;
    }

    // Function to parse the server's response
    public static HashMap<String, String> parseResponse(Socket socket) throws IOException {
        HashMap<String, String> resultMap = new HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String line;

        // Parse the response line
        line = reader.readLine();
        if (line != null && line.startsWith("HTTP/1.1")) {
            String[] responseParts = line.split(" ");
            resultMap.put("Status-Code", responseParts[1]);  // The second part is the status code
        } else {
            throw new IllegalArgumentException("Not a valid HTTP response");
        }

        readBody(reader, resultMap);
        return resultMap;
    }
}
