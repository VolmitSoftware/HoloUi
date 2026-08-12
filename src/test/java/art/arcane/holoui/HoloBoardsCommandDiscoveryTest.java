package art.arcane.holoui;

import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeParameter;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HoloBoardsCommandDiscoveryTest {
  @Test
  public void boardsGroupDiscoversEveryOperatorAction() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new HoloCommand(null));
    DirectorRuntimeNode boards = child(engine.getRoot(), "boards");

    assertNotNull(boards);
    Set<String> names = boards.getChildren().stream()
        .map(node -> node.getDescriptor().getName())
        .collect(Collectors.toUnmodifiableSet());
    assertEquals(Set.of(
        "list", "reload", "near", "info", "create", "delete", "rename", "copy",
        "move", "movehere", "tp", "rotate", "scale", "align", "menu", "ranges",
        "visibility", "permissions", "follow", "unfollow", "edit", "save", "cancel",
        "addrow", "insertrow", "setrow", "removerow", "offsetrow", "seticon", "style",
        "image", "editweb"
    ), names);
  }

  @Test
  public void menusGroupDiscoversPersistentContentActions() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new HoloCommand(null));
    DirectorRuntimeNode menus = child(engine.getRoot(), "menus");

    assertNotNull(menus);
    Set<String> names = menus.getChildren().stream()
        .map(node -> node.getDescriptor().getName())
        .collect(Collectors.toUnmodifiableSet());
    assertEquals(Set.of(
        "addrow", "insertrow", "setrow", "removerow", "offsetrow", "seticon", "style",
        "image", "copy"
    ), names);
  }

  @Test
  public void contentParametersExposePageDefaultsAndValueCompletions() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new HoloCommand(null));
    DirectorRuntimeNode boards = child(engine.getRoot(), "boards");
    DirectorRuntimeNode menus = child(engine.getRoot(), "menus");

    assertEquals(List.of("page", "sender"), parameterNames(child(boards, "list")));
    assertEquals(List.of("menu", "row", "type", "value", "sender"),
        parameterNames(child(menus, "seticon")));
    assertEquals(List.of("menu", "row", "property", "value", "sender"),
        parameterNames(child(menus, "style")));
    assertEquals(List.of("menu", "path", "sender"), parameterNames(child(menus, "image")));
    assertEquals(List.of("board", "row", "type", "value", "sender"),
        parameterNames(child(boards, "seticon")));
    assertEquals(List.of("board", "row", "property", "value", "sender"),
        parameterNames(child(boards, "style")));
    assertEquals(List.of("board", "path", "sender"), parameterNames(child(boards, "image")));

    DirectorRuntimeParameter page = parameter(child(boards, "list"), "page");
    assertEquals(int.class, page.getDescriptor().getType());
    assertEquals("1", page.getDescriptor().getDefaultValue());
    assertEquals("holoui.parameter.page", page.getDescriptor().getDescriptionKey());

    DirectorRuntimeParameter menuIconType = parameter(child(menus, "seticon"), "type");
    assertTrue(menuIconType.getCustomHandlerOrNull() instanceof HoloMenusCommand.IconTypeHandler);
    assertEquals(
        Set.of("text", "image", "animated", "item", "block", "customItem", "entity"),
        Set.copyOf(menuIconType.getCustomHandlerOrNull().getPossibilities())
    );

    DirectorRuntimeParameter boardStyleProperty = parameter(child(boards, "style"), "property");
    assertTrue(boardStyleProperty.getCustomHandlerOrNull() instanceof HoloMenusCommand.StylePropertyHandler);
    assertTrue(boardStyleProperty.getCustomHandlerOrNull().getPossibilities().contains("backgroundArgb"));
    assertEquals("path", parameter(child(boards, "image"), "path").getDescriptor().getName());
  }

  @Test
  public void boardAliasesRemainDiscoverable() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new HoloCommand(null));
    DirectorRuntimeNode boards = child(engine.getRoot(), "boards");

    assertTrue(child(boards, "delete").allNames().contains("remove"));
    assertTrue(child(boards, "movehere").allNames().contains("tphere"));
    assertTrue(child(boards, "menu").allNames().contains("root"));
  }

  @Test
  public void importGroupExposesExplicitPreviewAndApplyModes() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new HoloCommand(null));
    DirectorRuntimeNode imports = child(engine.getRoot(), "import");

    assertNotNull(imports);
    Set<String> names = imports.getChildren().stream()
        .map(node -> node.getDescriptor().getName())
        .collect(Collectors.toUnmodifiableSet());
    assertEquals(Set.of("preview", "apply"), names);
    assertTrue(child(imports, "preview").allNames().contains("dry-run"));
  }

  @Test
  public void syncGroupExposesOperatorControlsForEverySenderSurface() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new HoloCommand(null));
    DirectorRuntimeNode sync = child(engine.getRoot(), "sync");
    DirectorRuntimeNode boards = child(engine.getRoot(), "boards");

    assertNotNull(sync);
    Set<String> names = sync.getChildren().stream()
        .map(node -> node.getDescriptor().getName())
        .collect(Collectors.toUnmodifiableSet());
    assertEquals(Set.of("list", "status", "revoke", "pull"), names);
    assertEquals(List.of("sender"), parameterNames(child(sync, "list")));
    assertEquals(List.of("session", "sender"), parameterNames(child(sync, "status")));
    assertEquals(List.of("session", "sender"), parameterNames(child(sync, "revoke")));
    assertEquals(List.of("session", "sender"), parameterNames(child(sync, "pull")));
    assertTrue(child(sync, "pull").allNames().contains("poll"));
    assertTrue(child(boards, "editweb").allNames().contains("webedit"));
  }

  private static DirectorRuntimeNode child(DirectorRuntimeNode parent, String name) {
    for (DirectorRuntimeNode node : parent.getChildren()) {
      if (node.allNames().contains(name)) {
        return node;
      }
    }
    return null;
  }

  private static DirectorRuntimeParameter parameter(DirectorRuntimeNode node, String name) {
    assertNotNull(node);
    return node.getParameters().stream()
        .filter(parameter -> parameter.getDescriptor().getName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static List<String> parameterNames(DirectorRuntimeNode node) {
    assertNotNull(node);
    return node.getParameters().stream()
        .map(parameter -> parameter.getDescriptor().getName())
        .toList();
  }
}
