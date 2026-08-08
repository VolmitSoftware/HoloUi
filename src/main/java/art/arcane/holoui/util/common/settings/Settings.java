/*
 * HoloUI is a holographic user interface for Minecraft Bukkit Servers
 * Copyright (c) 2025 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package art.arcane.holoui.util.common.settings;

import art.arcane.holoui.HoloUI;
import art.arcane.volmlib.util.io.FileWatcher;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;

public abstract class Settings {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final Map<String, Entry<?>> fields = Maps.newHashMap();

  private final File file;
  private final FileWatcher fileWatcher;

  public Settings(File file) {
    this.file = file;
    registerFields();

    if (!file.exists() || file.isDirectory()) {
      try {
        HoloUI.log(Level.INFO, "Settings file missing, generating new default file.");
        if (file.isDirectory())
          FileUtils.deleteQuietly(file);
        else
          file.getParentFile().mkdirs();
        file.createNewFile();
        writeJson();
      } catch (IOException e) {
        HoloUI.logExceptionStack(false, e, "An error occurred while writing default settings to %s.", file.getName());
      }
    } else
      doReload(false);

    this.fileWatcher = new FileWatcher(file);
  }

  public void update() {
    if (fileWatcher.checkModified())
      doReload(true);
  }

  public void write() {
    writeJson();
  }

  protected abstract void registerFields();

  protected void registerField(String field, Entry<?> entry) {
    this.fields.putIfAbsent(field, entry);
  }

  private void doReload(boolean triggerListeners) {
    try (FileReader reader = new FileReader(file)) {
      JsonElement element = JsonParser.parseReader(reader);
      if (element == null || !element.isJsonObject()) {
        HoloUI.log(Level.WARNING, "%s: root is not a json object, keeping the last good values.", file.getName());
        return;
      }
      JsonObject obj = element.getAsJsonObject();
      fields.forEach((f, e) -> reloadField(f, e, obj, triggerListeners));
    } catch (IOException | RuntimeException e) {
      HoloUI.logExceptionStack(false, e, "An error occurred while reloading settings from %s.", file.getName());
    }
  }

  private void reloadField(String key, Entry<?> entry, JsonObject obj, boolean triggerListener) {
    try {
      entry.update(key, obj, triggerListener);
    } catch (RuntimeException failure) {
      HoloUI.logExceptionStack(false, failure, "%s: %s = %s was rejected; keeping %s.",
          file.getName(), key, obj.get(key), entry.value());
    }
  }

  private void writeJson() {
    try (FileWriter writer = new FileWriter(file)) {
      JsonObject obj = new JsonObject();
      fields.forEach((name, field) -> field.serialize(name, obj));
      GSON.toJson(obj, writer);
    } catch (IOException e) {
      HoloUI.logExceptionStack(false, e, "An error occurred while writing settings to %s.", file.getName());
    }
  }

  @RequiredArgsConstructor
  public static class Entry<V> {

    private final EntryType<V> type;
    private final V defaultValue;
    private final Consumer<V> onChange;

    private V value;

    public V value() {
      return value == null ? (value = defaultValue) : value;
    }

    private void serialize(String key, JsonObject json) {
      this.type.serialize(key, value(), json);
    }

    private void update(String key, JsonObject obj, boolean triggerListener) {
      V previous = value;
      V updated = obj.has(key) ? type.parse(key, obj) : defaultValue;
      value = updated;
      try {
        if (triggerListener) {
          onChange.accept(updated);
        }
      } catch (RuntimeException failure) {
        value = previous;
        throw failure;
      }
    }
  }
}
