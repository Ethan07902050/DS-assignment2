# 2024 DS Assignment 2

## Functionalities

**Completed**
- Text sending works
- Client, server and content server processes start up and communicate
- PUT operation works for one content server
- GET operation works for many read clients
- Aggregation server expunging expired data works (30s)
- Lamport clocks are implemented
- All error codes are implemented

**Todo**
- Retry on errors (server not available etc.) works
- Content servers are replicated and fault-tolerant

## System Overview

### AggregationServer

`AggregationServer` is responsible for handling requests from both content servers and clients, managing weather data, and ensuring that the system adheres to synchronization using the Lamport clock. It maintains weather data for various stations and ensures that only the most recent 20 updates per station are stored. Additionally, it automatically cleans up data from content servers that haven't communicated in the last 30 seconds.

#### Methods:

- **main()**:  
  Initializes the server, loads existing weather data from the storage file, and starts three threads
  - One for listening connections from clients or content servers, and invoking ClientHandler.
  - One for processing GET or PUT requests from the task queue, which is a priority queue ordered by the tasks' Lamport timestamps.
  - One for periodically cleaning up stale data.

- **startCleanupThread()**:  
  Starts a background thread that periodically removes weather data from content servers that have not communicated for over 30 seconds. It also saves the updated weather data back to the storage file.

- **restart()**:  
  Restarts the server, resetting necessary components and re-invoking the main method.

- **shutdown()**:  
  Gracefully shuts down the server by stopping the listener socket and halting task processing.

- **ClientHandler.run()**:  
  Handles `GET` and `PUT` operations. It validates JSON input, processes the requests, and sends appropriate responses back to the clients. If it is a valid request, it would be pushed into the task queue.

- **Task.process()**:  
  Processes tasks from the task queue, updating weather data and ensuring that data is persisted. It responds to the client after processing the task.

### WeatherEntry
`WeatherEntry` represents a weather update for a station. It stores weather data (such as temperature, humidity, etc.) in a `HashMap` and timestamps the entry with the current time.

#### Methods:

- **WeatherEntry(String body)**:  
  Constructs a new `WeatherEntry` by parsing the JSON string `body` and extracting weather data. It also timestamps the entry using the current date and time.

- **getTimestampAsLocalDateTime()**:  
  Converts the `timestamp` field (stored as a string) back into a `LocalDateTime` object for time-based operations like cleanup or sorting.

### StorageFile
`StorageFile` is responsible for persisting weather data to a file and recovering data after a server crash or restart. It uses JSON for data serialization and ensures that file writes are atomic to avoid corruption.

#### Methods:

- **StorageFile(String dirStr, String fileStr)**:  
  Initializes the storage file and ensures that the directory exists. It creates necessary directories if they are missing.

- **recoverDataFromFile()**:  
  Recovers weather data from the storage file after a server restart or crash. If a temporary file exists (indicating incomplete writes), it recovers data from that file.

- **saveDataToFile(Map<String, Deque<WeatherEntry>> weatherData)**:  
  Saves weather data to the file, ensuring the write operation is atomic. It first writes data to a temporary file, then moves it to the permanent file location.

- **convertWeatherDataToJson()**:  
  Converts the weather data map into a JSON string for storage.

- **parseJsonToWeatherData(String jsonContent)**:  
  Parses a JSON string back into a `Map<String, Deque<WeatherEntry>>` for recovery purposes.

### LamportClock
`LamportClock` is responsible for maintaining Lamport time synchronization between distributed servers or clients. It ensures that events are ordered correctly based on causality, even when the servers do not share a global clock.

#### Methods
- **LamportClock()**:  
  Initializes the Lamport clock with the starting time `t = 0`.

- **increaseTime()**:  
  Increments the local Lamport clock time when the server performs an action, such as sending a message or processing a request.

- **increaseTime(int receivedTime)**:  
  Updates the local Lamport clock time based on a received time from another server. It ensures that the local time is always ahead of or equal to the received time.

- **getTime()**:  
  Returns the current Lamport time.


### ContentServer
The `ContentServer` class simulates a client that sends weather data to the `AggregationServer` using HTTP `PUT` requests. The data is parsed from a local file, converted to JSON, and sent to the server. The `ContentServer` also maintains a Lamport clock to ensure synchronization with the server.

#### Methods:

- **main(String[] args)**:  
  This is the entry point for the `ContentServer` application. It parses the server details and the file path from the command line arguments, reads weather data from the file, converts it to JSON, and sends it to the server using a `PUT` request.

- **ContentServer(String serverDetails)**:  
  Initializes the `ContentServer` by connecting to the specified `AggregationServer` using a socket and setting up a Lamport clock for synchronization.

- **parseFile(String filePath)**:  
  Reads a local file line by line and extracts key-value pairs (weather data), storing them in a `HashMap`. It ensures the `id` field is present in the file for proper identification.

- **sendPutRequest(String jsonData)**:  
  Sends a `PUT` request to the `AggregationServer`, including weather data in JSON format. It also updates the local Lamport clock and processes the Lamport time returned in the server’s response.

### GETClient
The `GETClient` class is responsible for querying the `AggregationServer` for weather data using HTTP `GET` requests. It sends a station ID to the server and retrieves the latest weather data for that station. The `GETClient` also maintains and synchronizes a Lamport clock to ensure event ordering between client and server.

#### Methods:

- **GETClient(String serverDetails)**:  
  Initializes the client by connecting to the specified `AggregationServer` using a socket and setting up a Lamport clock for synchronization.

- **sendGetRequest(String stationId)**:  
  Sends a `GET` request to the `AggregationServer` to retrieve weather data for the specified station ID. The client updates its local Lamport clock and adjusts it based on the server’s response.

### RequestResponseHandler
`RequestResponseHandler` is a utility class that manages the sending and receiving of HTTP requests and responses between the `ContentServer`, `GETClient`, and `AggregationServer`. It constructs and parses `PUT` and `GET` requests and handles the server’s responses.

#### Methods:
- **sendPutRequest(Socket socket, String jsonData, int lamportTime)**:  
  Sends an HTTP `PUT` request with weather data in JSON format. It includes the current Lamport time in the request headers and sends it to the server via the provided socket.

- **sendGetRequest(Socket socket, String stationId, int lamportTime)**:  
  Sends an HTTP `GET` request to retrieve weather data for a specified station ID. It includes the current Lamport time in the request headers and sends the request via the provided socket.

- **sendResponse(Socket socket, int statusCode, HashMap<String, String> message, int lamportTime)**:  
  Sends an HTTP response with the given status code and optional JSON data. It includes the current Lamport time in the response headers and sends it via the provided socket.

- **parseRequest(Socket socket)**:  
  Parses an incoming HTTP request (either `GET` or `PUT`) and extracts relevant information such as the operation type, station ID, and request body. It reads the request line-by-line and handles both headers and body content.

- **parseResponse(Socket socket)**:  
  Parses the server’s HTTP response, extracting the status code, headers, and body. It reads the response line-by-line and converts the body back into a `HashMap`.

---

## Functional Tests
Here is an overview of the functional test classes designed to validate various behaviors of the `AggregationServer`. The tests ensure the correctness of server functionality related to status codes, Lamport clock synchronization, failure recovery, data management, and cleanup for inactivity. The test scripts are in the folder `src/test`.

### 1. StatusCodeTest
This class verifies that the `AggregationServer` returns the correct HTTP status codes for various types of requests, including successful operations, malformed requests, unsupported operations, and empty requests.

**Tests:**
- **putWeather200Test**:  
  This test checks that the server responds with a 200 OK status code after successfully processing a subsequent `PUT` request from the same content server, indicating that the data was updated.

- **putWeather201Test**:  
  This test checks that the server responds with a 201 Created status code after successfully receiving and processing a `PUT` request for new weather data from a content server.

- **putWeather204Test**:  
  This test checks that the server responds with a 204 No Content status code when the `PUT` request contains an empty body, indicating that there was no content to process.

- **postWeather400Test**:  
  This test verifies that the server returns a 400 Bad Request status code when an unsupported HTTP method (`POST`) is used. It ensures that only valid `GET` and `PUT` methods are allowed.

- **putWeather500Test**:  
  This test verifies that the server responds with a 500 Internal Server Error status code when malformed JSON is sent in a `PUT` request. The test simulates an invalid JSON format and checks that the server properly handles the error by returning the appropriate status code.

### 2. ClientServerInteractionTest
The ClientServerInteractionTest class verifies various aspects of the interaction between `ContentServers`, `GETClients`, and the `AggregationServer`. These tests focus on ensuring correct operation, synchronization using the Lamport clock, and consistency when handling simultaneous GET and PUT requests from multiple clients and content servers.

**Test:**
- **getWeatherLamportClockTest**:  
  This test checks whether the Lamport clock is properly synchronized between the `ContentServer` and `AggregationServer`. A content server first sends a `PUT` request and verifies the correct Lamport clock state after the request. A client then sends a `GET` request and checks that the Lamport clock values reflect the correct event ordering, ensuring that the clock value increases as expected with each operation.

- **getOperationMultipleClientsTest**
  This test ensures that when multiple clients simultaneously perform `GET` requests, they all retrieve the correct weather data. It verifies that the `AggregationServer` serves the same data to all clients without errors or race conditions.

- **interleavedPutAndGetTest**
  This test simulates a scenario where a `PUT` request, a `GET` request, and another `PUT` request are made in sequence. The goal is to ensure that the correct Lamport clock ordering is maintained, and the `GET` request returns the data from the first `PUT`, while the second `PUT` is processed afterward.

- **multipleContentServersPutTest**
  This test ensures that when multiple content servers send `PUT` requests simultaneously, the `AggregationServer` serializes the requests based on the Lamport clock. It also verifies that the final state of the weather data reflects the order in which the `PUT` requests were processed.

### 3. CleanupInactivityTest
This test class verifies that the `AggregationServer` properly cleans up stale data from content servers that have not communicated within a specified period (30 seconds).

**Test:**
- **testRemovalAfterInactivity**:  
  This test ensures that content from a content server, which has not communicated for more than 30 seconds, is removed from the server's storage. It simulates a content server sending a `PUT` request and then checks if the data is properly deleted after 30 seconds of inactivity. The test asserts that after this period, a `GET` request for the station returns a 204 status code (indicating the data has been removed).

### **4. DataManagementTest**
This class ensures that the `AggregationServer` properly manages data storage, specifically enforcing a limit on the number of weather entries retained for each content server.

**Test:**
- **putWeatherLimitTest**:  
  This test verifies that the server enforces a limit of 20 weather data entries per content server. It sends 25 consecutive `PUT` requests with weather data and checks that only the latest 20 entries are retained for a specific station. The test confirms this by asserting that the `Deque` for the station contains exactly 20 items.

### **5. FailureRecoveryTest**
This class tests the server’s ability to recover from various failure scenarios, including recovering from crashes after data has been written to storage and from incomplete file writes during server crashes.

**Tests:**
- **recoveryAfterSaveDataToFileTest**:  
  This test simulates a server crash after successfully saving data to the storage file. It sends a `PUT` request, simulates a server crash and restart, and checks whether the server successfully recovers the weather data from the storage file. The test verifies that a `GET` request after the server restart returns the correct weather data.

- **testRecoveryBetweenFilesWriteAndMove**:  
  This test simulates a server crash between writing data to a temporary file (`Files.write`) and moving it to the permanent location (`Files.move`). It verifies whether the server detects and recovers data from the temporary file upon restart. The test asserts that the weather data is correctly recovered after the crash.

Here's a simple explanation of how to run tests in a Maven project, suitable for including in your README:

## How to Run Tests
1. **Open a Terminal**: Navigate to the root directory of your Maven project (the directory containing the `pom.xml` file).

2. **Run the Tests**:
    - Use the following command to run all the tests in your project:
      ```bash
      mvn test
      ```
3. **Test Reports**:
    - After running the tests, Maven generates a test report in the `target/surefire-reports/` directory. You can open this report to see detailed information about the test results, including which tests passed, failed, or were skipped.

4. **Running Specific Tests**:
    - To run a specific test class, use the following command:
      ```bash
      mvn -Dtest=ClassName test
      ```
      For example, to run only the `StatusCodeTest` class:
      ```bash
      mvn -Dtest=StatusCodeTest test
      ```