package gg.paceman.tracker.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gg.paceman.tracker.PaceManTracker;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class PacemanGGUtil {
    private static final String PACEMANGG_ENDPOINT = "https://paceman.gg/api/sendevent";
    private static final int SUCCESS_RESPONSE = 201;
    private static final int TOTAL_TRIES = 5;

    public static void sendToPacemanGG(String accessKey, String latestWorldContents, List<String> events) {
        JsonObject eventModelInput = new JsonObject();
        eventModelInput.addProperty("accessKey", accessKey);

        if (latestWorldContents != null) {
            JsonObject latestWorldJson = new Gson().fromJson(latestWorldContents, JsonObject.class);
            String worldId = sha256Hash(latestWorldJson.get("world_path").getAsString());
            JsonArray mods = latestWorldJson.getAsJsonArray("mods");
            String gameVersion = latestWorldJson.get("version").getAsString();
            String category = latestWorldJson.get("category").getAsString();

            JsonObject gameData = new JsonObject();
            gameData.addProperty("worldId", worldId);
            gameData.addProperty("gameVersion", gameVersion);
            gameData.addProperty("category", category);
            gameData.add("modList", mods);

            eventModelInput.add("gameData", gameData);
        }

        JsonArray eventList = new JsonArray();
        events.forEach(eventList::add);
        eventModelInput.add("eventList", eventList);

        String toSend = eventModelInput.toString();
        sendToPacemanGG(toSend);
    }

    private static void sendToPacemanGG(String toSend) {
        PaceManTracker.logDebug("Sending: " + toSend);

        for (int i = 0; i < TOTAL_TRIES; i++) {
            int response;
            try {
                response = sendData(PACEMANGG_ENDPOINT, toSend);
            } catch (IOException e) {
                PaceManTracker.logDebug("Request timed out or ran into an exception, retrying...");
                continue; // retries on timeout or other exceptions
            }

            if (response == SUCCESS_RESPONSE) {
                PaceManTracker.logDebug("Sent successfully");
                return;
            } else {
                // erm throw the bad code to the user who will throw it at the dev
                throw new RuntimeException("Failed to send to " + PACEMANGG_ENDPOINT + ", response code " + response);
                // TODO: Implement response code checks (like 401 == invalid auth or some shit like that)
            }
        }
        // At this point it has gone through all tries without succeeding or receiving a bad response code.
        throw new RuntimeException("Failed to send to " + PACEMANGG_ENDPOINT + " due to an error attempting to send for each of the " + TOTAL_TRIES + " attempts");
    }

    private static String sha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }


    private static int sendData(String endpointUrl, String jsonData) throws IOException {
        // Create URL object
        URL url = new URL(endpointUrl);
        HttpURLConnection connection = null;
        try {
            // Open connection
            connection = (HttpURLConnection) url.openConnection();

            // Set the necessary properties
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Write JSON data to the connection output stream
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Return the response code
            return connection.getResponseCode();
        } finally {
            // Close the connection
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
