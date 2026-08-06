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
package art.arcane.holoui.expr;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs every vector in {@code src/test/resources/expr_test_vectors.json} through
 * {@link ExprParser} and {@link ExprEvaluator}. This file is the cross-repo (Java/Dart) contract
 * for the expression language: a future Dart implementation replays the same vectors.
 */
public class ExprVectorsTest {

  private static final String RESOURCE_NAME = "expr_test_vectors.json";
  private static final double DELTA = 1e-9;
  private static final int MINIMUM_VECTOR_COUNT = 40;

  @Test
  public void everyVectorMatchesItsExpectation() throws IOException {
    JsonArray vectors = loadVectors();
    Assert.assertTrue("expected at least " + MINIMUM_VECTOR_COUNT + " vectors, found " + vectors.size(),
        vectors.size() >= MINIMUM_VECTOR_COUNT);
    for (JsonElement element : vectors) {
      runVector(element.getAsJsonObject());
    }
  }

  private static void runVector(JsonObject vector) {
    String source = vector.get("expr").getAsString();
    boolean expectError = vector.has("error") && vector.get("error").getAsBoolean();
    ExprScope scope = new MapScope(readVars(vector));

    if (expectError) {
      try {
        Object result = ExprEvaluator.eval(ExprParser.parse(source), scope);
        Assert.fail("expected error for '" + source + "' but got: " + result);
      } catch (ExprException expected) {
        // expected: either the parser or the evaluator may raise it, both are "error: true".
      }
      return;
    }

    Object actual;
    try {
      actual = ExprEvaluator.eval(ExprParser.parse(source), scope);
    } catch (ExprException e) {
      throw new AssertionError("vector '" + source + "' raised " + e.getMessage(), e);
    }
    assertMatches(source, vector.get("expect"), actual);
  }

  private static void assertMatches(String source, JsonElement expectElement, Object actual) {
    JsonPrimitive expect = expectElement.getAsJsonPrimitive();
    if (expect.isBoolean()) {
      Assert.assertTrue("vector '" + source + "': expected boolean result, got " + actual, actual instanceof Boolean);
      Assert.assertEquals("vector '" + source + "'", expect.getAsBoolean(), actual);
      return;
    }
    if (expect.isNumber()) {
      Assert.assertTrue("vector '" + source + "': expected numeric result, got " + actual, actual instanceof Double);
      Assert.assertEquals("vector '" + source + "'", expect.getAsDouble(), (Double) actual, DELTA);
      return;
    }
    String expectString = expect.getAsString();
    if (expectString.startsWith("#")) {
      // Color expectations are written as hex literals; reuse the parser's own color lexing
      // rather than duplicating its hex-expansion rules here.
      Assert.assertTrue("vector '" + source + "': expected color result, got " + actual, actual instanceof Double);
      double expectColor = ((Expr.Num) ExprParser.parse(expectString)).value();
      Assert.assertEquals("vector '" + source + "'", expectColor, (Double) actual, DELTA);
      return;
    }
    Assert.assertTrue("vector '" + source + "': expected string result, got " + actual, actual instanceof String);
    Assert.assertEquals("vector '" + source + "'", expectString, actual);
  }

  private static Map<String, Object> readVars(JsonObject vector) {
    Map<String, Object> vars = new HashMap<>();
    if (vector.has("vars")) {
      JsonObject varsObject = vector.getAsJsonObject("vars");
      for (Map.Entry<String, JsonElement> entry : varsObject.entrySet()) {
        vars.put(entry.getKey(), toJavaValue(entry.getValue()));
      }
    }
    return vars;
  }

  private static Object toJavaValue(JsonElement element) {
    if (element.isJsonArray()) {
      List<Object> list = new ArrayList<>();
      for (JsonElement item : element.getAsJsonArray()) {
        list.add(toJavaValue(item));
      }
      return list;
    }
    JsonPrimitive primitive = element.getAsJsonPrimitive();
    if (primitive.isBoolean()) {
      return primitive.getAsBoolean();
    }
    if (primitive.isNumber()) {
      return primitive.getAsDouble();
    }
    return primitive.getAsString();
  }

  private static JsonArray loadVectors() throws IOException {
    try (InputStream in = ExprVectorsTest.class.getClassLoader().getResourceAsStream(RESOURCE_NAME)) {
      Assert.assertNotNull("missing test resource: " + RESOURCE_NAME, in);
      try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        return root.getAsJsonArray("vectors");
      }
    }
  }
}
