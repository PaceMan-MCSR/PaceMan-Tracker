package gg.paceman.tracker;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import gg.paceman.tracker.util.ExceptionUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ItemTracker {
    private static final Gson GSON = new Gson();
    private Dictionary<String, Integer> estimatedCounts;
    private Dictionary<String, Integer> usages;

    public void tryUpdate(Path worldPath) {
        try {
            this.update(worldPath);
        } catch (Exception e) {
            PaceManTracker.logError("ItemTracker update failed: " + ExceptionUtil.toDetailedString(e));
        }
    }

    private void update(Path worldPath) throws IOException, JsonSyntaxException {
        // Clear tables (and trash old ones)
        this.estimatedCounts = new Hashtable<>();
        this.usages = new Hashtable<>();

        Path recordFile = worldPath.resolve("speedrunigt").resolve("record.json");
        if (!Files.exists(recordFile)) { // No record file shouldn't happen, but I guess if it does then give up /shrug
            return;
        }

        JsonObject json = GSON.fromJson(new String(Files.readAllBytes(recordFile)), JsonObject.class);

        if (json == null || !json.keySet().contains("stats")) {
            return;
        }

        JsonObject stats = json.getAsJsonObject("stats");
        Set<Map.Entry<String, JsonElement>> entries = stats.entrySet();
        if (entries.isEmpty()) {
            return;
        }
        stats = entries.stream().findAny().get().getValue().getAsJsonObject().getAsJsonObject("stats");

        //
        if (stats.has("minecraft:picked_up")) {
            JsonObject pickedUp = stats.getAsJsonObject("minecraft:picked_up");
            for (Map.Entry<String, JsonElement> entry : pickedUp.entrySet()) {
                // Set count estimate for this item
                this.estimatedCounts.put(entry.getKey(), entry.getValue().getAsInt());
            }
        }

        if (stats.has("minecraft:dropped")) {
            JsonObject dropped = stats.getAsJsonObject("minecraft:dropped");
            for (Map.Entry<String, JsonElement> entry : dropped.entrySet()) {
                // Subtract from count estimate for this item
                Optional.ofNullable(this.estimatedCounts.get(entry.getKey()))
                        .ifPresent(i -> this.estimatedCounts.put(entry.getKey(), i - entry.getValue().getAsInt()));
            }
        }

        if (stats.has("minecraft:used")) {
            JsonObject used = stats.getAsJsonObject("minecraft:used");
            for (Map.Entry<String, JsonElement> entry : used.entrySet()) {
                // Subtract from count estimate for this item
                Optional.ofNullable(this.estimatedCounts.get(entry.getKey()))
                        .ifPresent(i -> this.estimatedCounts.put(entry.getKey(), i - entry.getValue().getAsInt()));
                // Set usages for this item
                this.usages.put(entry.getKey(), entry.getValue().getAsInt());
            }
        }
    }

    public int getEstimatedCount(String item) {
        return Optional.ofNullable(this.estimatedCounts.get(item)).orElse(0);
    }

    public int getUsages(String item) {
        return Optional.ofNullable(this.usages.get(item)).orElse(0);
    }

    public Optional<JsonObject> constructItemData(Set<String> itemsToGetEstimate, Set<String> itemsToGetUsage) {
        JsonObject estimatedCounts = new JsonObject();
        itemsToGetEstimate.stream().filter(s -> this.getEstimatedCount(s) > 0).forEach(s -> estimatedCounts.addProperty(s, this.getEstimatedCount(s)));
        JsonObject usages = new JsonObject();
        itemsToGetUsage.stream().filter(s -> this.getUsages(s) > 0).forEach(s -> usages.addProperty(s, this.getUsages(s)));
        JsonObject itemData = new JsonObject();
        if (estimatedCounts.size() == 0 && usages.size() == 0) {
            return Optional.empty();
        }
        itemData.add("estimatedCounts", estimatedCounts);
        itemData.add("usages", usages);
        return Optional.of(itemData);
    }
}
