package me.hsgamer.bettergui.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class VersionChecker {

  private final Plugin plugin;
  private final int resourceId;

  public VersionChecker(Plugin plugin, int resourceId) {
    this.plugin = plugin;
    this.resourceId = resourceId;
  }

  public void getVersion(final Consumer<String> consumer) {
    Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
      try {
        URL url = new URL(
            "https://api.spigotmc.org/simple/0.1/index.php?action=getResource&id=" + resourceId);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.addRequestProperty("User-Agent",
            "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:25.0) Gecko/20100101 Firefox/25.0");

        InputStreamReader reader = new InputStreamReader(connection.getInputStream());
        JsonObject object = new JsonParser().parse(reader).getAsJsonObject();
        reader.close();
        connection.disconnect();

        String version = object.get("current_version").getAsString();
        if (version == null) {
          throw new IOException("Cannot get the plugin version");
        }
        consumer.accept(version);
      } catch (IOException exception) {
        this.plugin.getLogger().warning("Cannot look for updates: " + exception.getMessage());
      }
    });
  }
}