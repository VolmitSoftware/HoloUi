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
package art.arcane.holoui.menu.special.inventories.doc;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins {@code src/test/resources/preview-variables.json} — the variable/function catalog document
 * authors and the web editor read — against {@link PreviewStateAdapters#catalog()} and
 * {@link PreviewStateContext#CONTEXT_FUNCTIONS}, in both directions, so the shipped file can never
 * silently drift from what the expression scope actually publishes.
 */
public class VariableCatalogSyncTest {

  /**
   * The two pure functions added specifically for the JSON document system (see
   * {@code ExprFunctions}); the rest of that library predates the document format and is not part
   * of this sync contract.
   */
  private static final Set<String> DOCUMENT_PURE_FUNCTIONS = Set.of("plain", "readable");

  private static final Set<String> KNOWN_TYPES = Set.of("number", "string", "boolean");

  @Test
  public void categoryNamesExactlyMatchTheAdaptersCatalog() {
    JsonObject categories = categories();

    assertEquals(PreviewStateAdapters.catalog().keySet(), categories.keySet());
  }

  @Test
  public void variableNamesWithinEachCategoryExactlyMatchTheAdaptersCatalog() {
    JsonObject categories = categories();
    for (Map.Entry<String, Set<String>> entry : PreviewStateAdapters.catalog().entrySet()) {
      JsonObject variables = categories.getAsJsonObject(entry.getKey());
      assertEquals(entry.getKey(), entry.getValue(), variables.keySet());
    }
  }

  @Test
  public void everyVariableDeclaresAKnownTypeAndANonBlankDescription() {
    JsonObject categories = categories();
    for (String category : categories.keySet()) {
      JsonObject variables = categories.getAsJsonObject(category);
      for (String name : variables.keySet()) {
        JsonObject variable = variables.getAsJsonObject(name);
        String path = category + "." + name;
        assertTrue(path + ".type", variable.has("type"));
        assertTrue(path + " type=" + variable.get("type").getAsString(), KNOWN_TYPES.contains(variable.get("type").getAsString()));
        assertTrue(path + ".description", variable.has("description"));
        assertFalse(path + " description", variable.get("description").getAsString().isBlank());
      }
    }
  }

  @Test
  public void functionsSectionListsEveryContextFunctionAndTheDocumentPureFunctions() {
    JsonObject functions = root().getAsJsonObject("functions");
    Set<String> expected = new HashSet<>(PreviewStateContext.CONTEXT_FUNCTIONS);
    expected.addAll(DOCUMENT_PURE_FUNCTIONS);

    assertEquals(expected, functions.keySet());
  }

  @Test
  public void everyFunctionDeclaresArgsReturnsAndANonBlankDescription() {
    JsonObject functions = root().getAsJsonObject("functions");
    for (String name : functions.keySet()) {
      JsonObject function = functions.getAsJsonObject(name);
      assertTrue(name + ".args", function.has("args") && function.get("args").isJsonArray());
      assertTrue(name + ".returns", function.has("returns"));
      assertFalse(name + ".returns", function.get("returns").getAsString().isBlank());
      assertTrue(name + ".description", function.has("description"));
      assertFalse(name + " description", function.get("description").getAsString().isBlank());
    }
  }

  private static JsonObject categories() {
    return root().getAsJsonObject("categories");
  }

  private static JsonObject root() {
    try (InputStream stream = VariableCatalogSyncTest.class.getResourceAsStream("/preview-variables.json")) {
      if (stream == null) {
        fail("preview-variables.json is missing from src/test/resources");
      }
      String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      return new Gson().fromJson(json, JsonObject.class);
    } catch (IOException e) {
      fail(e.getMessage());
      throw new AssertionError(e);
    }
  }
}
