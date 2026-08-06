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

import art.arcane.holoui.menu.special.inventories.doc.CompiledPreviewDocument.CardTemplate;
import art.arcane.holoui.menu.special.inventories.doc.CompiledPreviewDocument.CompiledExpr;
import art.arcane.holoui.menu.special.inventories.doc.CompiledPreviewDocument.CompiledMatch;
import art.arcane.holoui.menu.special.inventories.doc.CompiledPreviewDocument.CompiledVariant;
import art.arcane.holoui.menu.special.inventories.doc.CompiledPreviewDocument.ElementTemplate;
import art.arcane.holoui.menu.special.inventories.doc.CompiledPreviewDocument.ElementType;
import org.bukkit.Material;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PreviewDocumentParserTest {

  private static final double EPSILON = 1.0E-9;
  private static final double DEFAULT_WELL_COLOR = (double) 0xFF15151BL;

  // ---------------------------------------------------------------------
  // Minimal valid document
  // ---------------------------------------------------------------------

  @Test
  public void minimalValidDocumentParses() {
    CompiledPreviewDocument doc = parse("minimal.json", """
        {
          "elements": [
            { "type": "label", "text": "'hi'" }
          ]
        }
        """);

    assertEquals("minimal.json", doc.name());
    assertEquals(0, doc.priority());
    assertEquals(1, doc.elements().size());
    ElementTemplate label = doc.elements().get(0);
    assertEquals(ElementType.LABEL, label.type());
    assertEquals("hi", label.text().constant());
    assertNull(doc.card());
    assertTrue(doc.variants().isEmpty());
    assertTrue(doc.vars().isEmpty());
  }

  @Test
  public void emptyDocumentParsesWithNoMatchNoElements() {
    CompiledPreviewDocument doc = parse("empty.json", "{}");

    assertEquals(0, doc.elements().size());
    assertTrue(doc.variants().isEmpty());
    assertNull(doc.card());
    assertNotNullMatch(doc.match());
  }

  // ---------------------------------------------------------------------
  // JSON syntax errors
  // ---------------------------------------------------------------------

  @Test
  public void malformedJsonIsWrappedWithDocumentName() {
    try {
      PreviewDocumentParser.parse("broken.json", "{ not valid json");
      fail("expected a PreviewDocumentException");
    } catch (PreviewDocumentException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().startsWith("broken.json"));
      assertEquals("broken.json", expected.documentName());
    }
  }

  @Test
  public void emptyStringIsRejected() {
    try {
      PreviewDocumentParser.parse("nothing.json", "");
      fail("expected a PreviewDocumentException");
    } catch (PreviewDocumentException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("nothing.json"));
    }
  }

  // ---------------------------------------------------------------------
  // Element type validation
  // ---------------------------------------------------------------------

  @Test
  public void rejectsUnknownElementType() {
    PreviewDocumentException error = expectFailure("badtype.json", """
        { "elements": [ { "type": "circle" } ] }
        """);
    assertTrue(error.getMessage(), error.getMessage().contains("elements[0].type"));
  }

  @Test
  public void rejectsMissingElementType() {
    PreviewDocumentException error = expectFailure("notype.json", """
        { "elements": [ { "x": 1 } ] }
        """);
    assertTrue(error.getMessage(), error.getMessage().contains("elements[0].type"));
  }

  @Test
  public void panelRequiresWidthHeightColor() {
    assertTrue(expectFailure("p1.json", "{ \"elements\": [ { \"type\": \"panel\", \"height\": 1, \"color\": 1 } ] }")
        .getMessage().contains("elements[0].width"));
    assertTrue(expectFailure("p2.json", "{ \"elements\": [ { \"type\": \"panel\", \"width\": 1, \"color\": 1 } ] }")
        .getMessage().contains("elements[0].height"));
    assertTrue(expectFailure("p3.json", "{ \"elements\": [ { \"type\": \"panel\", \"width\": 1, \"height\": 1 } ] }")
        .getMessage().contains("elements[0].color"));
  }

  @Test
  public void cellRequiresSizeAndColor() {
    assertTrue(expectFailure("c1.json", "{ \"elements\": [ { \"type\": \"cell\", \"color\": 1 } ] }")
        .getMessage().contains("elements[0].size"));
    assertTrue(expectFailure("c2.json", "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4 } ] }")
        .getMessage().contains("elements[0].color"));
  }

  @Test
  public void slotRequiresSizeAndIndex() {
    assertTrue(expectFailure("s1.json", "{ \"elements\": [ { \"type\": \"slot\", \"index\": 0 } ] }")
        .getMessage().contains("elements[0].size"));
    assertTrue(expectFailure("s2.json", "{ \"elements\": [ { \"type\": \"slot\", \"size\": 18 } ] }")
        .getMessage().contains("elements[0].index"));
  }

  @Test
  public void labelRequiresText() {
    assertTrue(expectFailure("l1.json", "{ \"elements\": [ { \"type\": \"label\" } ] }")
        .getMessage().contains("elements[0].text"));
  }

  @Test
  public void explicitJsonNullOnARequiredFieldIsRejectedSameAsAbsent() {
    // Gson binds a JSON `null` to JsonNull.INSTANCE (a non-null JsonElement), not a bare Java
    // null, so the required-field check must not mistake an explicit null for a present value.
    PreviewDocumentException widthError = expectFailure("nullwidth.json",
        "{ \"elements\": [ { \"type\": \"panel\", \"width\": null, \"height\": 1, \"color\": 1 } ] }");
    assertTrue(widthError.getMessage(), widthError.getMessage().contains("elements[0].width"));

    PreviewDocumentException indexError = expectFailure("nullindex.json",
        "{ \"elements\": [ { \"type\": \"slot\", \"size\": 18, \"index\": null } ] }");
    assertTrue(indexError.getMessage(), indexError.getMessage().contains("elements[0].index"));
  }

  @Test
  public void explicitJsonNullOnARequiredRepeatCountIsRejected() {
    PreviewDocumentException error = expectFailure("nullrepeatcount.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": 1, \"repeat\": { \"count\": null } } ] }");
    assertTrue(error.getMessage(), error.getMessage().contains("elements[0].repeat.count"));
  }

  // ---------------------------------------------------------------------
  // Defaults: x/y, z per type, slot wellColor, label background/visible
  // ---------------------------------------------------------------------

  @Test
  public void xAndYDefaultToZero() {
    ElementTemplate cell = onlyElement(parse("xy.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": 1 } ] }"));
    assertEquals(0.0, (Double) cell.x().constant(), EPSILON);
    assertEquals(0.0, (Double) cell.y().constant(), EPSILON);
  }

  @Test
  public void zDefaultsPerType() {
    ElementTemplate panel = onlyElement(parse("zp.json",
        "{ \"elements\": [ { \"type\": \"panel\", \"width\": 1, \"height\": 1, \"color\": 1 } ] }"));
    ElementTemplate cell = onlyElement(parse("zc.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 1, \"color\": 1 } ] }"));
    ElementTemplate slot = onlyElement(parse("zs.json",
        "{ \"elements\": [ { \"type\": \"slot\", \"size\": 1, \"index\": 0 } ] }"));
    ElementTemplate label = onlyElement(parse("zl.json",
        "{ \"elements\": [ { \"type\": \"label\", \"text\": \"'x'\" } ] }"));

    assertEquals(1.0, (Double) panel.z().constant(), EPSILON);
    assertEquals(4.0, (Double) cell.z().constant(), EPSILON);
    assertEquals(4.0, (Double) slot.z().constant(), EPSILON);
    assertEquals(6.0, (Double) label.z().constant(), EPSILON);
  }

  @Test
  public void slotWellColorDefaultsToTheWellColorConstant() {
    ElementTemplate slot = onlyElement(parse("well.json",
        "{ \"elements\": [ { \"type\": \"slot\", \"size\": 18, \"index\": 0 } ] }"));
    assertEquals(DEFAULT_WELL_COLOR, (Double) slot.wellColor().constant(), EPSILON);
  }

  @Test
  public void labelBackgroundAndVisibleDefault() {
    ElementTemplate label = onlyElement(parse("lbl.json",
        "{ \"elements\": [ { \"type\": \"label\", \"text\": \"'x'\" } ] }"));
    assertEquals(0.0, (Double) label.background().constant(), EPSILON);
    assertEquals(Boolean.TRUE, label.visible().constant());
  }

  // ---------------------------------------------------------------------
  // Constant folding: number vs string both fold to the same constant
  // ---------------------------------------------------------------------

  @Test
  public void colorFieldAcceptsNumberOrStringExpressionFoldingToTheSameConstant() {
    double expected = (double) 0xFF000000L;
    ElementTemplate numeric = onlyElement(parse("colnum.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": " + expected + " } ] }"));
    ElementTemplate string = onlyElement(parse("colstr.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": \"#FF000000\" } ] }"));

    assertTrue(numeric.color().isConstant());
    assertTrue(string.color().isConstant());
    assertEquals(expected, (Double) numeric.color().constant(), EPSILON);
    assertEquals((Double) numeric.color().constant(), (Double) string.color().constant(), EPSILON);
  }

  @Test
  public void nonConstantExpressionIsNotFolded() {
    ElementTemplate cell = onlyElement(parse("dyn.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": 1, \"x\": \"cookTime\" } ] }"));
    assertFalse(cell.x().isConstant());
    assertNull(cell.x().constant());
  }

  @Test
  public void divisionByZeroFoldingErrorNamesTheFieldPath() {
    PreviewDocumentException error = expectFailure("divzero.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": 1, \"x\": \"1/0\" } ] }");
    assertTrue(error.getMessage(), error.getMessage().contains("elements[0].x"));
    assertTrue(error.getMessage(), error.getMessage().contains("division by zero"));
  }

  // ---------------------------------------------------------------------
  // Expression syntax errors
  // ---------------------------------------------------------------------

  @Test
  public void exprSyntaxErrorIsWrappedWithDocumentNameAndFieldPath() {
    PreviewDocumentException error = expectFailure("furnace.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": 1, \"x\": \"1 +\" } ] }");
    assertTrue(error.getMessage(), error.getMessage().startsWith("furnace.json"));
    assertTrue(error.getMessage(), error.getMessage().contains("elements[0].x"));
    assertTrue(error.getMessage(), error.getMessage().contains("at "));
  }

  // ---------------------------------------------------------------------
  // Variable validation
  // ---------------------------------------------------------------------

  @Test
  public void unknownBareVariableIsRejected() {
    PreviewDocumentException error = expectFailure("unkvar.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": 1, \"x\": \"noSuchThing\" } ] }");
    assertTrue(error.getMessage(), error.getMessage().contains("elements[0].x"));
    assertTrue(error.getMessage(), error.getMessage().contains("noSuchThing"));
  }

  @Test
  public void knownAdapterVariableFromAnyCategoryIsAccepted() {
    // The document declares no block/entity match, so it does not know its category yet; any
    // cataloged adapter name (from any category) must still be accepted.
    ElementTemplate cell = onlyElement(parse("known.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": 1, \"x\": \"cookTime\" } ] }"));
    assertFalse(cell.x().isConstant());
  }

  @Test
  public void universalCatalogNamesAreAccepted() {
    ElementTemplate cell = onlyElement(parse("universal.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": 1, \"x\": \"time\" } ] }"));
    assertFalse(cell.x().isConstant());
  }

  @Test
  public void declaredVarsAreAcceptedAndUndeclaredVarsAreRejected() {
    CompiledPreviewDocument doc = parse("vars.json", """
        {
          "match": { "vars": { "foo": 1 } },
          "elements": [ { "type": "cell", "size": 4, "color": 1, "x": "vars.foo" } ]
        }
        """);
    assertFalse(onlyElement(doc).x().isConstant());

    PreviewDocumentException error = expectFailure("badvars.json", """
        { "elements": [ { "type": "cell", "size": 4, "color": 1, "x": "vars.bar" } ] }
        """);
    assertTrue(error.getMessage(), error.getMessage().contains("vars.bar"));
  }

  @Test
  public void varsDeclaredOnAVariantAreAcceptedDocumentWide() {
    CompiledPreviewDocument doc = parse("variantvars.json", """
        {
          "variants": [ { "vars": { "tint": 1 } } ],
          "elements": [ { "type": "cell", "size": 4, "color": "vars.tint" } ]
        }
        """);
    assertFalse(onlyElement(doc).color().isConstant());
  }

  @Test
  public void repeatVarIsInScopeForOtherFieldsButNotForItsOwnCount() {
    CompiledPreviewDocument doc = parse("repeatscope.json", """
        {
          "elements": [
            { "type": "cell", "size": 4, "color": 1, "x": "i * 20", "repeat": { "count": 3 } }
          ]
        }
        """);
    assertFalse(onlyElement(doc).x().isConstant());

    PreviewDocumentException error = expectFailure("repeatcountbad.json", """
        { "elements": [ { "type": "cell", "size": 4, "color": 1, "repeat": { "count": "i" } } ] }
        """);
    assertTrue(error.getMessage(), error.getMessage().contains("i"));
  }

  @Test
  public void repeatVarDefaultsToIAndCanBeRenamed() {
    ElementTemplate withDefault = onlyElement(parse("repeatdefault.json", """
        { "elements": [ { "type": "cell", "size": 4, "color": 1, "x": "i", "repeat": { "count": 2 } } ] }
        """));
    assertEquals("i", withDefault.repeat().var());

    ElementTemplate renamed = onlyElement(parse("repeatrenamed.json", """
        { "elements": [ { "type": "cell", "size": 4, "color": 1, "x": "n", "repeat": { "count": 2, "var": "n" } } ] }
        """));
    assertEquals("n", renamed.repeat().var());
    assertFalse(renamed.x().isConstant());
  }

  @Test
  public void bareNameNotInScopeIsRejectedEvenIfItLooksLikeARepeatVar() {
    PreviewDocumentException error = expectFailure("noscope.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": 1, \"x\": \"i\" } ] }");
    assertTrue(error.getMessage(), error.getMessage().contains("i"));
  }

  @Test
  public void unknownPrefixIsAcceptedAsAProviderNamespace() {
    ElementTemplate cell = onlyElement(parse("provider.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": 1, \"x\": \"adapt.level\" } ] }"));
    assertFalse(cell.x().isConstant());
  }

  @Test
  public void knownCategoryRootWithUnknownSpecificNameIsRejected() {
    PreviewDocumentException error = expectFailure("badcategory.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": 1, \"x\": \"inventory.bogus\" } ] }");
    assertTrue(error.getMessage(), error.getMessage().contains("inventory.bogus"));
  }

  @Test
  public void reservedNamespaceTypoIsRejected() {
    // "surge" is reserved (surge.active/surge.gain are cataloged under it), so a typo of a real
    // surge variable must hard-fail instead of silently compiling as an unverified provider name.
    PreviewDocumentException error = expectFailure("surgetypo.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": 1, \"x\": \"surge.bogus\" } ] }");
    assertTrue(error.getMessage(), error.getMessage().contains("surge.bogus"));
  }

  @Test
  public void unreservedProviderNamespaceWarnsAndCompiles() {
    // "adapt" is not a reserved namespace (no cataloged variable is prefixed with it), so a
    // currently-unregistered provider name must warn and still compile, not hard-fail.
    ElementTemplate cell = onlyElement(parse("adaptxp.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": 1, \"x\": \"adapt.xp\" } ] }"));
    assertFalse(cell.x().isConstant());
  }

  // ---------------------------------------------------------------------
  // Repeat caps
  // ---------------------------------------------------------------------

  @Test
  public void constantRepeatCountOverCapIsRejected() {
    PreviewDocumentException error = expectFailure("bigrepeat.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": 1, \"repeat\": { \"count\": 2000 } } ] }");
    assertTrue(error.getMessage(), error.getMessage().contains("elements[0].repeat.count"));
    assertTrue(error.getMessage(), error.getMessage().contains("1024"));
  }

  @Test
  public void nonConstantRepeatCountSkipsTheCapCheck() {
    ElementTemplate cell = onlyElement(parse("dynrepeat.json", """
        { "elements": [ { "type": "cell", "size": 4, "color": 1, "repeat": { "count": "inventory.size" } } ] }
        """));
    assertFalse(cell.repeat().count().isConstant());
  }

  @Test
  public void totalTemplateCountOverCapIsRejected() {
    String elements = String.join(",",
        "{ \"type\": \"cell\", \"size\": 4, \"color\": 1, \"repeat\": { \"count\": 1024 } }",
        "{ \"type\": \"cell\", \"size\": 4, \"color\": 1, \"repeat\": { \"count\": 1024 } }",
        "{ \"type\": \"cell\", \"size\": 4, \"color\": 1, \"repeat\": { \"count\": 1024 } }",
        "{ \"type\": \"cell\", \"size\": 4, \"color\": 1, \"repeat\": { \"count\": 1024 } }",
        "{ \"type\": \"cell\", \"size\": 4, \"color\": 1 }");
    PreviewDocumentException error = expectFailure("toomany.json", "{ \"elements\": [ " + elements + " ] }");
    assertTrue(error.getMessage(), error.getMessage().contains("4096"));
  }

  @Test
  public void repeatVarMustBeAValidIdentifier() {
    PreviewDocumentException error = expectFailure("badvarname.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": 1, \"repeat\": { \"count\": 2, \"var\": \"1bad\" } } ] }");
    assertTrue(error.getMessage(), error.getMessage().contains("elements[0].repeat.var"));
  }

  @Test
  public void repeatVarCollidingWithACatalogVariableIsRejected() {
    // checkVariableName resolves flatCatalog names before repeat scope, so a repeat var named
    // "cookTime" would silently make the loop variable unreachable rather than fail loudly.
    PreviewDocumentException error = expectFailure("repeatvarcollides.json",
        "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": 1, \"repeat\": { \"count\": 2, \"var\": \"cookTime\" } } ] }");
    assertTrue(error.getMessage(), error.getMessage().contains("elements[0].repeat.var"));
    assertTrue(error.getMessage(), error.getMessage().contains("cookTime"));
  }

  // ---------------------------------------------------------------------
  // Match: globs, exact names, special
  // ---------------------------------------------------------------------

  @Test
  public void blocksSplitIntoExactNamesAndCompiledGlobPredicates() {
    CompiledPreviewDocument doc = parse("blocks.json", """
        { "match": { "blocks": ["FURNACE", "OAK_*"] } }
        """);
    CompiledMatch match = doc.match();
    assertTrue(match.exactBlocks().contains("FURNACE"));
    assertEquals(1, match.blockGlobs().size());
    assertTrue(match.blockGlobs().get(0).test("OAK_LOG"));
    assertFalse(match.blockGlobs().get(0).test("SPRUCE_LOG"));
  }

  @Test
  public void unknownExactMaterialStillCompiles() {
    CompiledPreviewDocument doc = parse("unkblock.json", """
        { "match": { "blocks": ["NOT_A_REAL_MATERIAL_XYZ"] } }
        """);
    assertTrue(doc.match().exactBlocks().contains("NOT_A_REAL_MATERIAL_XYZ"));
  }

  @Test
  public void globWithNoCurrentMatchesStillCompilesSilently() {
    CompiledPreviewDocument doc = parse("globnomatch.json", """
        { "match": { "blocks": ["ZZZZ_NO_MATCH_*"] } }
        """);
    assertEquals(1, doc.match().blockGlobs().size());
  }

  @Test
  public void entitiesSplitIntoExactNamesAndCompiledGlobPredicates() {
    CompiledPreviewDocument doc = parse("entities.json", """
        { "match": { "entities": ["HOPPER_MINECART", "*_MINECART"] } }
        """);
    CompiledMatch match = doc.match();
    assertTrue(match.exactEntities().contains("HOPPER_MINECART"));
    assertEquals(1, match.entityGlobs().size());
    assertTrue(match.entityGlobs().get(0).test("CHEST_MINECART"));
  }

  @Test
  public void unknownExactEntityTypeStillCompiles() {
    CompiledPreviewDocument doc = parse("unkentity.json", """
        { "match": { "entities": ["NOT_A_REAL_ENTITY_XYZ"] } }
        """);
    assertTrue(doc.match().exactEntities().contains("NOT_A_REAL_ENTITY_XYZ"));
  }

  @Test
  public void validSpecialValuesAreAccepted() {
    for (String special : new String[]{"enderChest", "locked", "anyInventoryHolder"}) {
      CompiledPreviewDocument doc = parse("special.json", "{ \"match\": { \"special\": \"" + special + "\" } }");
      assertEquals(special, doc.match().special());
    }
  }

  @Test
  public void invalidSpecialValueIsRejected() {
    PreviewDocumentException error = expectFailure("badspecial.json",
        "{ \"match\": { \"special\": \"bogus\" } }");
    assertTrue(error.getMessage(), error.getMessage().contains("match.special"));
  }

  @Test
  public void priorityDefaultsToZero() {
    CompiledPreviewDocument doc = parse("noprio.json", "{}");
    assertEquals(0, doc.priority());
  }

  @Test
  public void priorityIsReadFromTopLevelMatch() {
    CompiledPreviewDocument doc = parse("prio.json", "{ \"match\": { \"priority\": 5 } }");
    assertEquals(5, doc.priority());
  }

  // ---------------------------------------------------------------------
  // matchSummary()
  // ---------------------------------------------------------------------

  @Test
  public void matchSummaryCountsExactAndGlobMatchersAcrossVariants() {
    CompiledPreviewDocument doc = parse("summary.json", """
        {
          "match": { "blocks": ["CHEST", "*_SHELF"], "entities": ["ZOMBIE"], "priority": 7 },
          "variants": [ { "blocks": ["BARREL"] } ]
        }
        """);

    CompiledPreviewDocument.MatchSummary summary = doc.matchSummary();
    assertEquals(3, summary.blocks());
    assertEquals(1, summary.entities());
    assertNull(summary.special());
    assertEquals(7, summary.priority());
  }

  @Test
  public void matchSummaryCarriesTheSpecialMarker() {
    CompiledPreviewDocument doc = parse("summaryspecial.json", "{ \"match\": { \"special\": \"locked\" } }");

    assertEquals("locked", doc.matchSummary().special());
    assertEquals(0, doc.matchSummary().blocks());
    assertEquals(0, doc.matchSummary().entities());
  }

  // ---------------------------------------------------------------------
  // Null entries in arrays (Gson binds these to a literal null list element, not an absent one)
  // ---------------------------------------------------------------------

  @Test
  public void nullElementEntryIsRejectedNamingTheIndex() {
    PreviewDocumentException error = expectFailure("nullelement.json", "{ \"elements\": [ null ] }");
    assertTrue(error.getMessage(), error.getMessage().startsWith("nullelement.json"));
    assertTrue(error.getMessage(), error.getMessage().contains("elements[0]"));
  }

  @Test
  public void nullBlockEntryIsRejectedNamingTheIndex() {
    PreviewDocumentException error = expectFailure("nullblock.json", "{ \"match\": { \"blocks\": [ null ] } }");
    assertTrue(error.getMessage(), error.getMessage().startsWith("nullblock.json"));
    assertTrue(error.getMessage(), error.getMessage().contains("match.blocks[0]"));
  }

  @Test
  public void nullVariantEntryIsRejectedNamingTheIndex() {
    PreviewDocumentException error = expectFailure("nullvariant.json", "{ \"variants\": [ null ] }");
    assertTrue(error.getMessage(), error.getMessage().startsWith("nullvariant.json"));
    assertTrue(error.getMessage(), error.getMessage().contains("variants[0]"));
  }

  // ---------------------------------------------------------------------
  // Vars map: primitive coercion
  // ---------------------------------------------------------------------

  @Test
  public void varsMapConvertsPrimitivesToDoubleBooleanOrStringOnly() {
    CompiledPreviewDocument doc = parse("varmap.json", """
        { "match": { "vars": { "n": 3, "b": true, "s": "hello" } } }
        """);
    assertEquals(3.0, (Double) doc.vars().get("n"), EPSILON);
    assertEquals(Boolean.TRUE, doc.vars().get("b"));
    assertEquals("hello", doc.vars().get("s"));
  }

  @Test
  public void varsMapStringValueIsNeverParsedAsAnExpression() {
    CompiledPreviewDocument doc = parse("varstring.json", """
        { "match": { "vars": { "s": "1 + 1" } } }
        """);
    assertEquals("1 + 1", doc.vars().get("s"));
  }

  @Test
  public void varsMapParsesColorLiteralStringsIntoUnsignedArgbNumbers() {
    CompiledPreviewDocument doc = parse("varcolor.json", """
        { "match": { "vars": { "short": "#F00", "rgb": "#1B1B22", "argb": "#FFB02E26" } } }
        """);
    assertEquals((double) 0xFFFF0000L, (Double) doc.vars().get("short"), EPSILON);
    assertEquals((double) 0xFF1B1B22L, (Double) doc.vars().get("rgb"), EPSILON);
    assertEquals((double) 0xFFB02E26L, (Double) doc.vars().get("argb"), EPSILON);
  }

  @Test
  public void variantVarsAlsoParseColorLiterals() {
    CompiledPreviewDocument doc = parse("variantcolor.json", """
        {
          "match": { "blocks": ["CHEST"], "vars": { "accent": "#F2A535" } },
          "variants": [ { "blocks": ["BARREL"], "vars": { "accent": "#F2D451" } } ]
        }
        """);
    assertEquals((double) 0xFFF2D451L, (Double) doc.varsForBlock(Material.BARREL).get("accent"), EPSILON);
    assertEquals((double) 0xFFF2A535L, (Double) doc.varsForBlock(Material.CHEST).get("accent"), EPSILON);
  }

  @Test
  public void varsMapRejectsAMalformedColorLiteral() {
    PreviewDocumentException error = expectFailure("badvarcolor.json", """
        { "match": { "vars": { "accent": "#GGGGGG" } } }
        """);
    assertTrue(error.getMessage(), error.getMessage().contains("match.vars.accent"));

    PreviewDocumentException length = expectFailure("badvarcolorlength.json", """
        { "match": { "vars": { "accent": "#FFFF" } } }
        """);
    assertTrue(length.getMessage(), length.getMessage().contains("match.vars.accent"));

    PreviewDocumentException trailing = expectFailure("badvarcolortrailing.json", """
        { "match": { "vars": { "accent": "#FF0000 chest" } } }
        """);
    assertTrue(trailing.getMessage(), trailing.getMessage().contains("match.vars.accent"));
  }

  /** Only a leading '#' selects the colour grammar; every other string stays exactly as written. */
  @Test
  public void varsMapLeavesNonColorStringsAlone() {
    CompiledPreviewDocument doc = parse("varplain.json", """
        { "match": { "vars": { "tag": "<#F2A535>", "key": "holoui.preview.theme.title.chest", "empty": "" } } }
        """);
    assertEquals("<#F2A535>", doc.vars().get("tag"));
    assertEquals("holoui.preview.theme.title.chest", doc.vars().get("key"));
    assertEquals("", doc.vars().get("empty"));
  }

  @Test
  public void varsMapRejectsNonScalarValues() {
    PreviewDocumentException error = expectFailure("badvarshape.json", """
        { "match": { "vars": { "bad": [1, 2] } } }
        """);
    assertTrue(error.getMessage(), error.getMessage().contains("match.vars.bad"));
  }

  // ---------------------------------------------------------------------
  // Variants
  // ---------------------------------------------------------------------

  @Test
  public void variantsCompileTheirOwnMatchAndVars() {
    CompiledPreviewDocument doc = parse("variants.json", """
        {
          "variants": [
            { "blocks": ["BLAST_FURNACE"], "vars": { "tint": 1 } },
            { "blocks": ["SMOKER"], "vars": { "tint": 2 } }
          ]
        }
        """);
    List<CompiledVariant> variants = doc.variants();
    assertEquals(2, variants.size());
    assertTrue(variants.get(0).match().exactBlocks().contains("BLAST_FURNACE"));
    assertEquals(1.0, (Double) variants.get(0).vars().get("tint"), EPSILON);
    assertTrue(variants.get(1).match().exactBlocks().contains("SMOKER"));
    assertEquals(2.0, (Double) variants.get(1).vars().get("tint"), EPSILON);
  }

  // ---------------------------------------------------------------------
  // Card
  // ---------------------------------------------------------------------

  @Test
  public void cardIsNullWhenAbsent() {
    CompiledPreviewDocument doc = parse("nocard.json", "{}");
    assertNull(doc.card());
  }

  @Test
  public void cardDefaultsWhenFieldsAreOmitted() {
    CompiledPreviewDocument doc = parse("emptycard.json", "{ \"card\": {} }");
    CardTemplate card = doc.card();
    assertEquals(Boolean.TRUE, card.framed().constant());
    assertNull(card.title());
    assertNull(card.accent());
    assertEquals(82, card.minHalfWidth());
  }

  @Test
  public void cardFramedAcceptsBooleanOrExpressionString() {
    CardTemplate constant = parse("cardbool.json", "{ \"card\": { \"framed\": true } }").card();
    assertEquals(Boolean.TRUE, constant.framed().constant());

    CardTemplate dynamic = parse("carddyn.json", """
        { "match": { "vars": { "flag": true } }, "card": { "framed": "vars.flag" } }
        """).card();
    assertFalse(dynamic.framed().isConstant());
  }

  @Test
  public void cardFramedRejectsANumber() {
    PreviewDocumentException error = expectFailure("cardbadframed.json",
        "{ \"card\": { \"framed\": 5 } }");
    assertTrue(error.getMessage(), error.getMessage().contains("card.framed"));
  }

  @Test
  public void cardTitleAndAccentCompileAsExpressions() {
    CardTemplate card = parse("cardtitle.json", """
        { "card": { "title": "'Furnace'", "accent": "#FF00FF00" } }
        """).card();
    assertEquals("Furnace", card.title().constant());
    assertEquals((double) 0xFF00FF00L, (Double) card.accent().constant(), EPSILON);
  }

  @Test
  public void cardMinHalfWidthUsesTheGivenValueWhenPresent() {
    CardTemplate card = parse("cardwidth.json", "{ \"card\": { \"minHalfWidth\": 120 } }").card();
    assertEquals(120, card.minHalfWidth());
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private static CompiledPreviewDocument parse(String name, String json) {
    return PreviewDocumentParser.parse(name, json);
  }

  private static ElementTemplate onlyElement(CompiledPreviewDocument doc) {
    assertEquals(1, doc.elements().size());
    return doc.elements().get(0);
  }

  private static PreviewDocumentException expectFailure(String name, String json) {
    try {
      PreviewDocumentParser.parse(name, json);
      fail("expected a PreviewDocumentException for " + name);
      throw new AssertionError("unreachable");
    } catch (PreviewDocumentException expected) {
      return expected;
    }
  }

  private static void assertNotNullMatch(CompiledMatch match) {
    assertTrue(match.exactBlocks().isEmpty());
    assertTrue(match.blockGlobs().isEmpty());
    assertTrue(match.exactEntities().isEmpty());
    assertTrue(match.entityGlobs().isEmpty());
    assertNull(match.special());
  }
}
