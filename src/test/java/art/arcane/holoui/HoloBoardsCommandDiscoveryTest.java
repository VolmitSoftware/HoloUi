package art.arcane.holoui;

import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeParameter;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HoloBoardsCommandDiscoveryTest {
  @Test
  public void rootCompletionShowsTheCanonicalSingularWorkflow() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new HoloCommand(null));
    List<String> suggestions = engine.tabComplete(
        new DirectorInvocation(new TestSender(), "hui", List.of("")));

    assertTrue(suggestions.containsAll(List.of("board", "menu", "preview", "item")));
    assertFalse(suggestions.contains("boards"));
    assertFalse(suggestions.contains("menus"));
    assertFalse(suggestions.contains("previews"));
    assertFalse(suggestions.contains("items"));
  }

  @Test
  public void boardsGroupDiscoversEveryOperatorAction() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new HoloCommand(null));
    DirectorRuntimeNode boards = child(engine.getRoot(), "board");

    assertNotNull(boards);
    Set<String> names = boards.getChildren().stream()
        .map(node -> node.getDescriptor().getName())
        .collect(Collectors.toUnmodifiableSet());
    assertEquals(Set.of(
        "list", "reload", "near", "info", "create", "delete", "rename", "copy",
        "move", "here", "teleport", "rotate", "scale", "align", "menu", "ranges",
        "visibility", "permissions", "follow", "unfollow", "edit", "save", "cancel",
        "addrow", "insertrow", "setrow", "removerow", "offsetrow", "seticon", "style",
        "image", "web"
    ), names);
  }

  @Test
  public void menusGroupDiscoversPersistentContentActions() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new HoloCommand(null));
    DirectorRuntimeNode menus = child(engine.getRoot(), "menu");

    assertNotNull(menus);
    Set<String> names = menus.getChildren().stream()
        .map(node -> node.getDescriptor().getName())
        .collect(Collectors.toUnmodifiableSet());
    assertEquals(Set.of(
        "addrow", "insertrow", "setrow", "removerow", "offsetrow", "seticon", "style",
        "image", "copy", "create"
    ), names);
  }

  @Test
  public void contentParametersExposePageDefaultsAndValueCompletions() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new HoloCommand(null));
    DirectorRuntimeNode boards = child(engine.getRoot(), "board");
    DirectorRuntimeNode menus = child(engine.getRoot(), "menu");

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

    DirectorRuntimeParameter createMenu = parameter(child(boards, "create"), "menu");
    assertEquals("*", createMenu.getDescriptor().getDefaultValue());
    assertFalse(createMenu.getDescriptor().isRequired());

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
    DirectorRuntimeNode boards = child(engine.getRoot(), "board");

    assertTrue(child(boards, "delete").allNames().contains("remove"));
    assertTrue(child(boards, "here").allNames().contains("movehere"));
    assertTrue(child(boards, "here").allNames().contains("tphere"));
    assertTrue(child(boards, "teleport").allNames().contains("tp"));
    assertTrue(child(boards, "web").allNames().contains("editweb"));
    assertTrue(child(boards, "web").allNames().contains("webedit"));
    assertTrue(child(boards, "menu").allNames().contains("root"));
    assertTrue(boards.allNames().contains("boards"));
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
    DirectorRuntimeNode boards = child(engine.getRoot(), "board");

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
    assertTrue(child(boards, "web").allNames().contains("webedit"));
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

  private static final class TestSender implements DirectorSender {
    @Override
    public boolean isPlayer() {
      return true;
    }

    @Override
    public String getName() {
      return "test";
    }

    @Override
    public void sendMessage(String message) {
    }
  }
}
