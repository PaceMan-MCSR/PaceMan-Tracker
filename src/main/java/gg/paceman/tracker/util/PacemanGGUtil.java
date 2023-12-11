package gg.paceman.tracker.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gg.paceman.tracker.PaceManTracker;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

public class PacemanGGUtil {
    private static final String PACEMANGG_ENDPOINT = "https://paceman.gg/api/sendevent";
    private static final int SUCCESS_RESPONSE_CODE = 201;

    public static PaceManResponse sendCancelToPacemanGG(String accessKey) {
        JsonObject eventModelInput = new JsonObject();
        // Access Key
        eventModelInput.addProperty("accessKey", accessKey);
        // Empty Event List
        eventModelInput.add("eventList", new JsonArray());
        // Kill flag
        eventModelInput.addProperty("kill", true);
        return PacemanGGUtil.sendToPacemanGG(eventModelInput.toString());
    }

    public static PaceManResponse sendEventsToPacemanGG(String accessKey, String latestWorldContents, List<String> events, long timeSinceRunStart) {
        JsonObject eventModelInput = new JsonObject();
        eventModelInput.addProperty("accessKey", accessKey);

        if (latestWorldContents != null) {
            JsonObject latestWorldJson = new Gson().fromJson(latestWorldContents, JsonObject.class);
            String worldId = PacemanGGUtil.sha256Hash(latestWorldJson.get("world_path").getAsString());
            JsonArray mods = latestWorldJson.getAsJsonArray("mods");
            String gameVersion = latestWorldJson.get("version").getAsString();
            String category = latestWorldJson.get("category").getAsString();

            JsonObject gameData = new JsonObject();
            gameData.addProperty("worldId", worldId);
            gameData.addProperty("gameVersion", gameVersion);
            gameData.addProperty("category", category);
            gameData.add("modList", mods);
            gameData.addProperty("trackerVersion", PaceManTracker.VERSION);

            eventModelInput.add("gameData", gameData);
        }

        JsonArray eventList = new JsonArray();
        events.forEach(eventList::add);
        eventModelInput.add("eventList", eventList);

        eventModelInput.addProperty("timeSinceRunStart", timeSinceRunStart);

        String toSend = eventModelInput.toString();
        return PacemanGGUtil.sendToPacemanGG(toSend);
    }

    private static PaceManResponse sendToPacemanGG(String toSend) {
        PaceManTracker.logDebug("Sending exactly: " + toSend);
        int responseCode;
        try {
            PostResponse out = PacemanGGUtil.sendData(PACEMANGG_ENDPOINT, toSend);
            responseCode = out.code;
            PaceManTracker.logDebug("Response " + responseCode + ": " + out.message);
        } catch (IOException e) {
            return PaceManResponse.SEND_ERROR;
        }

        if (responseCode == SUCCESS_RESPONSE_CODE) {
            return PaceManResponse.SUCCESS;
        } else {
            return PaceManResponse.DENIED;
        }
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


    private static PostResponse sendData(String endpointUrl, String jsonData) throws IOException {
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
            int responseCode = connection.getResponseCode();
            String message = responseCode >= 400 ? PacemanGGUtil.readStream(connection.getErrorStream()) : connection.getResponseMessage();


            // Return the response code
            return new PostResponse(responseCode, message);
        } finally {
            // Close the connection
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readStream(InputStream inputStream) {
        return new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.joining("\n"));
    }

    public enum PaceManResponse {
        SUCCESS, // 201 response
        DENIED, // non 201 response
        SEND_ERROR // error while trying to send
    }

    private static class PostResponse {
        private final int code;
        private final String message;

        private PostResponse(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}
