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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SettingsTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  private final List<LogRecord> logged = new ArrayList<>();

  private Handler capture;
  private long stamp = System.currentTimeMillis();

  @Before
  public void captureLog() {
    capture = new Handler() {
      @Override
      public void publish(LogRecord record) {
        logged.add(record);
      }

      @Override
      public void flush() {
      }

      @Override
      public void close() {
      }
    };
    Logger.getLogger("HoloUi").addHandler(capture);
  }

  @After
  public void releaseLog() {
    Logger.getLogger("HoloUi").removeHandler(capture);
  }

  @Test
  public void freshFileAdvertisesEveryKeyWithItsDefault() throws IOException {
    File file = new File(temp.newFolder("fresh"), "settings.json");

    new FreshFixture(file);

    JsonObject json = read(file);
    assertEquals(Set.of("flag", "amount", "label"), json.keySet());
    assertTrue(json.get("flag").getAsBoolean());
    assertEquals(0.5D, json.get("amount").getAsDouble(), 0D);
    assertEquals("preset", json.get("label").getAsString());
  }

  @Test
  public void unparsableNumberKeepsTheLastGoodValueAndReloadsTheRest() throws IOException {
    File file = new File(temp.newFolder("number"), "settings.json");
    write(file, "{\"flag\":false,\"amount\":2.5,\"label\":\"live\"}");
    ReloadFixture settings = new ReloadFixture(file);
    assertEquals(2.5D, ReloadFixture.AMOUNT.value(), 0D);

    write(file, "{\"flag\":false,\"amount\":\"abc\",\"label\":\"edited\"}");
    settings.update();

    assertEquals(2.5D, ReloadFixture.AMOUNT.value(), 0D);
    assertEquals("edited", ReloadFixture.LABEL.value());
    List<String> warnings = warnings();
    assertEquals(1, warnings.size());
    assertTrue(warnings.getFirst().contains("amount"));
    assertTrue(warnings.getFirst().contains("abc"));
  }

  @Test
  public void jsonNullKeepsTheLastGoodValueAndReportsOncePerFileChange() throws IOException {
    File file = new File(temp.newFolder("null"), "settings.json");
    write(file, "{\"flag\":true,\"amount\":2.5,\"label\":\"live\"}");
    ReloadFixture settings = new ReloadFixture(file);

    write(file, "{\"flag\":null,\"amount\":2.5,\"label\":\"live\"}");
    settings.update();
    settings.update();
    settings.update();

    assertTrue(ReloadFixture.FLAG.value());
    assertEquals(1, warnings().size());
    assertTrue(warnings().getFirst().contains("flag"));
  }

  @Test
  public void nonObjectRootKeepsEveryValue() throws IOException {
    File file = new File(temp.newFolder("root"), "settings.json");
    write(file, "{\"flag\":false,\"amount\":2.5,\"label\":\"live\"}");
    ReloadFixture settings = new ReloadFixture(file);

    write(file, "[]");
    settings.update();

    assertEquals(2.5D, ReloadFixture.AMOUNT.value(), 0D);
    assertEquals("live", ReloadFixture.LABEL.value());
    assertEquals(1, warnings().size());
  }

  @Test
  public void listenerFailureRestoresThePreviousValue() throws IOException {
    File file = new File(temp.newFolder("listener"), "settings.json");
    write(file, "{\"value\":\"stable\"}");
    ListenerFixture settings = new ListenerFixture(file);

    write(file, "{\"value\":\"rejected\"}");
    settings.update();

    assertEquals("stable", ListenerFixture.VALUE.value());
    assertEquals(1, warnings().size());
  }

  private List<String> warnings() {
    List<String> messages = new ArrayList<>();
    for (LogRecord record : logged) {
      if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
        messages.add(record.getMessage());
      }
    }
    return messages;
  }

  private static JsonObject read(File file) throws IOException {
    return JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
  }

  private void write(File file, String json) throws IOException {
    Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
    stamp += 5000L;
    assertTrue(file.setLastModified(stamp));
  }

  private static final class FreshFixture extends Settings {

    private static final Entry<Boolean> FLAG = new Entry<>(EntryType.BOOLEAN, true, v -> {
    });
    private static final Entry<Double> AMOUNT = new Entry<>(EntryType.DOUBLE, 0.5D, v -> {
    });
    private static final Entry<String> LABEL = new Entry<>(EntryType.STRING, "preset", v -> {
    });

    private FreshFixture(File file) {
      super(file);
    }

    @Override
    protected void registerFields() {
      registerField("flag", FLAG);
      registerField("amount", AMOUNT);
      registerField("label", LABEL);
    }
  }

  private static final class ReloadFixture extends Settings {

    private static final Entry<Boolean> FLAG = new Entry<>(EntryType.BOOLEAN, true, v -> {
    });
    private static final Entry<Double> AMOUNT = new Entry<>(EntryType.DOUBLE, 0.5D, v -> {
    });
    private static final Entry<String> LABEL = new Entry<>(EntryType.STRING, "preset", v -> {
    });

    private ReloadFixture(File file) {
      super(file);
    }

    @Override
    protected void registerFields() {
      registerField("flag", FLAG);
      registerField("amount", AMOUNT);
      registerField("label", LABEL);
    }
  }

  private static final class ListenerFixture extends Settings {

    private static final Entry<String> VALUE = new Entry<>(EntryType.STRING, "preset", value -> {
      if ("rejected".equals(value)) {
        throw new IllegalStateException("rejected by listener");
      }
    });

    private ListenerFixture(File file) {
      super(file);
    }

    @Override
    protected void registerFields() {
      registerField("value", VALUE);
    }
  }
}
