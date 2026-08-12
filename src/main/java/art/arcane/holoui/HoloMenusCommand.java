package art.arcane.holoui;

import art.arcane.holoui.config.menu.MenuRevisionConflictException;
import art.arcane.holoui.config.menu.MenuRowMutations;
import art.arcane.holoui.localization.HoloLocalization;
import art.arcane.holoui.localization.HoloMessages;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;
import java.util.function.UnaryOperator;
import java.util.logging.Level;

@Director(name = "menus", description = "Persistently edit loaded menu content", descriptionKey = "holoui.command.menus.root")
public final class HoloMenusCommand {
  public static final String PERMISSION = HoloCommand.ROOT_PERM + ".menus";

  @Director(name = "addrow", description = "Append a text decoration row", descriptionKey = "holoui.command.menus.addrow")
  public void addRow(
      @Param(name = "menu", description = "Loaded menu id", descriptionKey = "holoui.parameter.content_menu", customHandler = HoloCommand.ExistingMenuHandler.class)
      String menuId,
      @Param(name = "text", description = "MiniMessage row text", descriptionKey = "holoui.parameter.row_text")
      String text,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    mutate(sender, menuId, "addrow", document -> MenuRowMutations.addTextRow(document, text), PERMISSION);
  }

  @Director(name = "insertrow", description = "Insert a text decoration row", descriptionKey = "holoui.command.menus.insertrow")
  public void insertRow(
      @Param(name = "menu", description = "Loaded menu id", descriptionKey = "holoui.parameter.content_menu", customHandler = HoloCommand.ExistingMenuHandler.class)
      String menuId,
      @Param(name = "row", description = "One-based component row", descriptionKey = "holoui.parameter.row_index")
      int row,
      @Param(name = "text", description = "MiniMessage row text", descriptionKey = "holoui.parameter.row_text")
      String text,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    mutate(sender, menuId, "insertrow", document -> MenuRowMutations.insertTextRow(document, row, text), PERMISSION);
  }

  @Director(name = "setrow", description = "Set a button or decoration row's text", descriptionKey = "holoui.command.menus.setrow")
  public void setRow(
      @Param(name = "menu", description = "Loaded menu id", descriptionKey = "holoui.parameter.content_menu", customHandler = HoloCommand.ExistingMenuHandler.class)
      String menuId,
      @Param(name = "row", description = "One-based component row", descriptionKey = "holoui.parameter.row_index")
      int row,
      @Param(name = "text", description = "MiniMessage row text", descriptionKey = "holoui.parameter.row_text")
      String text,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    mutate(sender, menuId, "setrow", document -> MenuRowMutations.setTextRow(document, row, text), PERMISSION);
  }

  @Director(name = "removerow", description = "Remove a component row", descriptionKey = "holoui.command.menus.removerow")
  public void removeRow(
      @Param(name = "menu", description = "Loaded menu id", descriptionKey = "holoui.parameter.content_menu", customHandler = HoloCommand.ExistingMenuHandler.class)
      String menuId,
      @Param(name = "row", description = "One-based component row", descriptionKey = "holoui.parameter.row_index")
      int row,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    mutate(sender, menuId, "removerow", document -> MenuRowMutations.removeRow(document, row), PERMISSION);
  }

  @Director(name = "offsetrow", description = "Move a row with absolute or ~relative offsets", descriptionKey = "holoui.command.menus.offsetrow")
  public void offsetRow(
      @Param(name = "menu", description = "Loaded menu id", descriptionKey = "holoui.parameter.content_menu", customHandler = HoloCommand.ExistingMenuHandler.class)
      String menuId,
      @Param(name = "row", description = "One-based component row", descriptionKey = "holoui.parameter.row_index")
      int row,
      @Param(name = "x", description = "Absolute or ~relative row X", descriptionKey = "holoui.parameter.row_x")
      String x,
      @Param(name = "y", description = "Absolute or ~relative row Y", descriptionKey = "holoui.parameter.row_y")
      String y,
      @Param(name = "z", description = "Absolute or ~relative row Z", descriptionKey = "holoui.parameter.row_z")
      String z,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    mutate(sender, menuId, "offsetrow",
        document -> MenuRowMutations.offsetRow(document, row, x, y, z), PERMISSION);
  }

  @Director(name = "seticon", description = "Replace a button or decoration row icon", descriptionKey = "holoui.command.menus.seticon")
  public void setIcon(
      @Param(name = "menu", description = "Loaded menu id", descriptionKey = "holoui.parameter.content_menu", customHandler = HoloCommand.ExistingMenuHandler.class)
      String menuId,
      @Param(name = "row", description = "One-based component row", descriptionKey = "holoui.parameter.row_index")
      int row,
      @Param(name = "type", description = "Icon type", descriptionKey = "holoui.parameter.row_icon_type", customHandler = IconTypeHandler.class)
      String type,
      @Param(name = "value", description = "Icon content or registry id", descriptionKey = "holoui.parameter.row_icon_value")
      String value,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    mutate(sender, menuId, "seticon",
        document -> setIconMutation(document, row, type, value), PERMISSION);
  }

  @Director(name = "style", description = "Set or clear one row display-style property", descriptionKey = "holoui.command.menus.style")
  public void style(
      @Param(name = "menu", description = "Loaded menu id", descriptionKey = "holoui.parameter.content_menu", customHandler = HoloCommand.ExistingMenuHandler.class)
      String menuId,
      @Param(name = "row", description = "One-based component row", descriptionKey = "holoui.parameter.row_index")
      int row,
      @Param(name = "property", description = "Display-style property", descriptionKey = "holoui.parameter.row_style_property", customHandler = StylePropertyHandler.class)
      String property,
      @Param(name = "value", description = "Style value, or * to clear", descriptionKey = "holoui.parameter.row_style_value")
      String value,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    mutate(sender, menuId, "style",
        document -> MenuRowMutations.setStyle(document, row, property, value), PERMISSION);
  }

  @Director(name = "image", description = "Replace menu content with one centered image file", descriptionKey = "holoui.command.menus.image")
  public void image(
      @Param(name = "menu", description = "Loaded menu id", descriptionKey = "holoui.parameter.content_menu", customHandler = HoloCommand.ExistingMenuHandler.class)
      String menuId,
      @Param(name = "path", description = "File beneath the HoloUI images folder", descriptionKey = "holoui.parameter.image_path")
      String path,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    mutate(sender, menuId, "image",
        document -> replaceWithImageMutation(document, path), PERMISSION);
  }

  @Director(name = "copy", description = "Copy a loaded menu to a new nested id", descriptionKey = "holoui.command.menus.copy")
  public void copy(
      @Param(name = "menu", description = "Loaded source menu id", descriptionKey = "holoui.parameter.content_menu", customHandler = HoloCommand.ExistingMenuHandler.class)
      String menuId,
      @Param(name = "newMenu", description = "New nested menu id", descriptionKey = "holoui.parameter.new_menu")
      String newMenuId,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender, PERMISSION)) {
      return;
    }
    HoloUI.INSTANCE.getConfigManager().copyMenu(menuId, newMenuId)
        .whenComplete((document, failure) -> {
          if (failure != null) {
            reportFailure(sender, newMenuId, failure);
            return;
          }
          sendLater(sender, HoloMessages.MENU_CONTENT_COPIED,
              MessageArgs.builder()
                  .untrusted("source", menuId)
                  .untrusted("menu", document.id())
                  .untrusted("revision", shortRevision(document.revision()))
                  .build());
        });
  }

  static void mutate(CommandSender sender, String menuId, String operation,
                     UnaryOperator<JsonObject> mutation, String permission) {
    if (!checkPermission(sender, permission)) {
      return;
    }
    HoloUI.INSTANCE.getConfigManager().mutateMenu(menuId, mutation)
        .whenComplete((document, failure) -> {
          if (failure != null) {
            reportFailure(sender, menuId, failure);
            return;
          }
          sendLater(sender, HoloMessages.MENU_CONTENT_UPDATED,
              MessageArgs.builder()
                  .untrusted("operation", operation)
                  .untrusted("menu", document.id())
                  .untrusted("revision", shortRevision(document.revision()))
                  .build());
        });
  }

  static JsonObject setIconMutation(JsonObject document, int row, String type, String value) {
    validateIconImages(type, value);
    return MenuRowMutations.setIcon(document, row, type, value);
  }

  static JsonObject replaceWithImageMutation(JsonObject document, String path) {
    requireImageFile(path);
    return MenuRowMutations.replaceWithImage(document, path);
  }

  private static void validateIconImages(String type, String value) {
    if (type == null) {
      return;
    }
    String normalized = type.strip().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    if (normalized.equals("image") || normalized.equals("textimage")) {
      requireImageFile(value);
      return;
    }
    if (!normalized.equals("animated")
        && !normalized.equals("animatedimage")
        && !normalized.equals("animatedtextimage")) {
      return;
    }
    if (value == null) {
      requireImageFile(null);
      return;
    }
    for (String frame : value.split(",", -1)) {
      requireImageFile(frame.strip());
    }
  }

  private static void requireImageFile(String path) {
    try {
      HoloUI.INSTANCE.getConfigManager().getImage(path);
    } catch (IOException | RuntimeException failure) {
      throw new IllegalArgumentException(
          "image must be a readable file inside plugins/holoui/images: " + String.valueOf(path), failure);
    }
  }

  private static boolean checkPermission(CommandSender sender, String permission) {
    if (sender.hasPermission(permission)) {
      return true;
    }
    send(sender, HoloMessages.PERMISSION_DENIED,
        MessageArgs.builder().untrusted("permission", permission).build());
    return false;
  }

  private static void reportFailure(CommandSender sender, String menuId, Throwable failure) {
    Throwable cause = BoardCommandSupport.rootCause(failure);
    if (cause instanceof MenuRevisionConflictException conflict) {
      sendLater(sender, HoloMessages.MENU_CONTENT_REVISION_CONFLICT,
          MessageArgs.builder()
              .untrusted("menu", conflict.menuId())
              .untrusted("expected", shortRevision(conflict.expectedRevision()))
              .untrusted("actual", shortRevision(conflict.actualRevision()))
              .build());
      return;
    }
    if (cause instanceof FileAlreadyExistsException) {
      sendLater(sender, HoloMessages.MENU_CONTENT_ALREADY_EXISTS,
          MessageArgs.builder().untrusted("menu", menuId).build());
      return;
    }
    if (cause instanceof NoSuchElementException || cause instanceof NoSuchFileException) {
      sendLater(sender, HoloMessages.MENU_UNAVAILABLE,
          MessageArgs.builder().untrusted("menu", menuId).build());
      return;
    }
    if (cause instanceof IllegalArgumentException || cause instanceof JsonParseException) {
      sendLater(sender, HoloMessages.MENU_CONTENT_INVALID,
          MessageArgs.builder().untrusted("reason", safeReason(cause)).build());
      return;
    }
    if (!(cause instanceof CancellationException)) {
      HoloUI.logExceptionStack(true, cause, "Persistent menu content command failed for menu \"%s\".", menuId);
    }
    sendLater(sender, HoloMessages.MENU_CONTENT_FAILED,
        MessageArgs.builder()
            .untrusted("menu", menuId)
            .untrusted("reason", safeReason(cause))
            .build());
  }

  private static void sendLater(CommandSender sender, TextKey key, MessageArgs arguments) {
    Runnable feedback = () -> send(sender, key, arguments);
    boolean accepted = sender instanceof Player player
        ? SchedulerUtils.runEntity(HoloUI.INSTANCE, player, feedback)
        : SchedulerUtils.runGlobal(HoloUI.INSTANCE, feedback);
    if (!accepted) {
      HoloUI.log(Level.WARNING, "Unable to schedule persistent menu command feedback for %s.", sender.getName());
    }
  }

  private static void send(CommandSender sender, TextKey key, MessageArgs arguments) {
    sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(key, arguments));
  }

  private static String shortRevision(String revision) {
    if (revision == null) {
      return "unknown";
    }
    return revision.length() <= 12 ? revision : revision.substring(0, 12);
  }

  private static String safeReason(Throwable failure) {
    String message = failure == null ? null : failure.getMessage();
    return message == null || message.isBlank()
        ? failure == null ? "unknown failure" : failure.getClass().getSimpleName()
        : message;
  }

  public static final class IconTypeHandler implements DirectorParameterHandler<String> {
    @Override
    public KList<String> getPossibilities() {
      return new KList<>(List.of("text", "image", "animated", "item", "block", "customItem", "entity"));
    }

    @Override
    public String toString(String value) {
      return value == null ? "" : value;
    }

    @Override
    public String parse(String in, boolean force) throws DirectorParsingException {
      if (in == null || in.isBlank()) {
        throw new DirectorParsingException(
            HoloLocalization.globalText(HoloMessages.ERROR_ROW_ICON_TYPE_REQUIRED));
      }
      return in.strip();
    }

    @Override
    public boolean supports(Class<?> type) {
      return type == String.class;
    }
  }

  public static final class StylePropertyHandler implements DirectorParameterHandler<String> {
    @Override
    public KList<String> getPossibilities() {
      return new KList<>(List.of(
          "billboard", "shadow", "seeThrough", "textAlignment", "backgroundArgb",
          "textOpacity", "lineWidth", "brightness", "viewRange", "shadowRadius",
          "shadowStrength", "cullingWidth", "cullingHeight", "glowColor", "scale",
          "scaleX", "scaleY", "scaleZ"));
    }

    @Override
    public String toString(String value) {
      return value == null ? "" : value;
    }

    @Override
    public String parse(String in, boolean force) throws DirectorParsingException {
      if (in == null || in.isBlank()) {
        throw new DirectorParsingException(
            HoloLocalization.globalText(HoloMessages.ERROR_ROW_STYLE_PROPERTY_REQUIRED));
      }
      return in.strip();
    }

    @Override
    public boolean supports(Class<?> type) {
      return type == String.class;
    }
  }
}
