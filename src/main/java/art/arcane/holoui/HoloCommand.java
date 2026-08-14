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
package art.arcane.holoui;

import art.arcane.holoui.board.BoardIds;
import art.arcane.holoui.board.BoardTransform;
import art.arcane.holoui.config.HuiSettings;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.editor.EditorMenuHandoff;
import art.arcane.holoui.editor.sync.EditorSyncOpenResult;
import art.arcane.holoui.localization.HoloLocalization;
import art.arcane.holoui.localization.HoloMessages;
import art.arcane.holoui.service.HologramCreationService;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.theme.DirectorProduct;
import art.arcane.volmlib.util.director.theme.DirectorTheme;
import art.arcane.volmlib.util.director.theme.DirectorThemes;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;

@Director(name = "holoui", aliases = {"holo", "hui", "holou", "hu"}, description = "HoloUI command root", descriptionKey = "holoui.command.root")
public class HoloCommand {
  public static final String ROOT_PERM = "holoui.command";
  private static final String OMITTED_HOLOGRAM_TEXT = "\u2063";

  private final HoloUI plugin;
  private HoloItemsCommand items;
  private HoloPreviewsCommand previews;
  private HoloBoardsCommand boards;
  private HoloMenusCommand menus;
  private HoloImportCommand legacyImport;
  private HoloSyncCommand sync;

  public HoloCommand(HoloUI plugin) {
    this.plugin = plugin;
  }

  public void shutdown() {
    if (boards != null) {
      boards.shutdown();
    }
  }

  @Director(name = "list", description = "List all configured menus you can open", descriptionKey = "holoui.command.list")
  public void list(@Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender") CommandSender sender) {
    String permission = ROOT_PERM + ".list";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    if (plugin.getConfigManager().keys().isEmpty()) {
      sender.sendMessage(plugin.getLocalization().legacy(HoloMessages.NO_MENUS));
      return;
    }

    DirectorMiniMenu.Theme theme = DirectorMiniMenu.Theme.fromDirectorTheme(DirectorThemes.forProduct(DirectorProduct.HOLOUI));
    List<String> lines = new ArrayList<>();
    lines.add(DirectorMiniMenu.banner(plugin.getLocalization().text(HoloMessages.MENU_LIST_HEADER), theme));
    for (String menu : plugin.getConfigManager().keys()) {
      String hover = plugin.getLocalization().text(
          HoloMessages.MENU_LIST_ENTRY,
          MessageArgs.builder().untrusted("menu", menu).build()
      );
      lines.add(menuEntryLine(menu, hover, theme));
    }
    lines.add(DirectorMiniMenu.bar(theme));
    DirectorMiniMenu.deliver(sender, lines);
  }

  @Director(name = "create", description = "Create a persistent hologram at your current position", descriptionKey = "holoui.command.create")
  public void create(
      @Param(name = "hologram", description = "New hologram and root-menu id", descriptionKey = "holoui.parameter.hologram_id", customHandler = HologramIdHandler.class)
      String id,
      @Param(name = "text", description = "Optional MiniMessage text", descriptionKey = "holoui.parameter.hologram_text", defaultValue = OMITTED_HOLOGRAM_TEXT)
      String text,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!sender.hasPermission(HoloBoardsCommand.PERMISSION)) {
      sendPermissionDenied(sender, HoloBoardsCommand.PERMISSION);
      playCreateOutcome(sender, false);
      return;
    }
    if (!(sender instanceof Player player)) {
      sender.sendMessage(plugin.getLocalization().legacy(HoloMessages.COMMAND_PLAYER_ONLY));
      playCreateOutcome(sender, false);
      return;
    }

    Location location = player.getLocation().clone();
    World world = location.getWorld();
    if (world == null) {
      sender.sendMessage(plugin.getLocalization().legacy(
          HoloMessages.BOARDS_WORLD_UNAVAILABLE,
          MessageArgs.builder().untrusted("world", "unknown").build()
      ));
      playCreateOutcome(sender, false);
      return;
    }
    BoardTransform transform = new BoardTransform(
        world.getKey().toString(),
        world.getUID(),
        location.getX(),
        location.getY(),
        location.getZ(),
        location.getYaw(),
        location.getPitch(),
        0.0D,
        1.0D
    );
    String initialText = OMITTED_HOLOGRAM_TEXT.equals(text) ? null : text;
    plugin.getHologramCreationService().create(id, initialText, transform)
        .whenComplete((creation, failure) -> sendCreateResult(sender, id, creation, failure));
  }

  @Director(name = "open", description = "Open a menu by id, or show the menu list when set to *", descriptionKey = "holoui.command.open")
  public void open(
      @Param(name = "menu", description = "Menu id to open (* shows all menus)", descriptionKey = "holoui.parameter.menu", defaultValue = "*", customHandler = MenuNameHandler.class)
      String menuName,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    String permission = ROOT_PERM + ".open";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    if ("*".equals(menuName.trim())) {
      list(sender);
      return;
    }

    if (!(sender instanceof Player player)) {
      sender.sendMessage(plugin.getLocalization().legacy(HoloMessages.MENUS_PLAYER_ONLY));
      return;
    }

    openMenu(player, sender, menuName, true);
  }

  @Director(name = "back", description = "Reopen your previous menu session", descriptionKey = "holoui.command.back")
  public void back(@Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender") CommandSender sender) {
    String permission = ROOT_PERM + ".back";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    if (!(sender instanceof Player player)) {
      sender.sendMessage(plugin.getLocalization().legacy(HoloMessages.COMMAND_PLAYER_ONLY));
      return;
    }

    if (!plugin.getSessionManager().openLastSession(player)) {
      player.sendMessage(plugin.getLocalization().legacy(HoloMessages.NO_PREVIOUS_MENU));
    }
  }

  @Director(name = "close", description = "Close your currently open menu session", descriptionKey = "holoui.command.close")
  public void close(@Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender") CommandSender sender) {
    String permission = ROOT_PERM + ".close";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    if (!(sender instanceof Player player)) {
      sender.sendMessage(plugin.getLocalization().legacy(HoloMessages.COMMAND_PLAYER_ONLY));
      return;
    }

    if (plugin.getSessionManager().destroySession(player, false)) {
      player.sendMessage(plugin.getLocalization().legacy(HoloMessages.MENU_CLOSED));
    } else {
      player.sendMessage(plugin.getLocalization().legacy(HoloMessages.NO_OPEN_MENU));
    }
  }

  @Director(name = "move", description = "Move your open menu to your current position", descriptionKey = "holoui.command.move")
  public void move(@Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender") CommandSender sender) {
    String permission = ROOT_PERM + ".move";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    if (!(sender instanceof Player player)) {
      sender.sendMessage(plugin.getLocalization().legacy(HoloMessages.COMMAND_PLAYER_ONLY));
      return;
    }

    if (plugin.getSessionManager().moveSession(player)) {
      player.sendMessage(plugin.getLocalization().legacy(HoloMessages.MENU_MOVED));
    } else {
      player.sendMessage(plugin.getLocalization().legacy(HoloMessages.NO_OPEN_MENU));
    }
  }

  @Director(name = "builder", description = "Link to the hosted HoloUI web editor", descriptionKey = "holoui.command.builder")
  public void builder(@Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender") CommandSender sender) {
    String permission = ROOT_PERM + ".builder";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    String url = HuiSettings.builderUrl();
    if (!(sender instanceof Player)) {
      // a terminal cannot click, so the bare url is the only useful form
      sender.sendMessage(plugin.getLocalization().legacy(
          HoloMessages.BUILDER_OPEN,
          MessageArgs.builder().untrusted("url", url).build()
      ));
      return;
    }

    DirectorMiniMenu.Theme theme = DirectorMiniMenu.Theme.fromDirectorTheme(DirectorThemes.forProduct(DirectorProduct.HOLOUI));
    String hover = plugin.getLocalization().text(HoloMessages.BUILDER_LINK_HOVER);
    List<String> lines = new ArrayList<>();
    lines.add(DirectorMiniMenu.banner(plugin.getLocalization().text(HoloMessages.BUILDER_HEADER), theme));
    lines.add("<hover:show_text:'" + DirectorMiniMenu.escapeText(hover).replace("\\", "\\\\").replace("'", "\\'") + "'>"
        + "<click:open_url:'" + url + "'>"
        + "<" + theme.muted() + ">⇀</" + theme.muted() + "> "
        + "<gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">" + DirectorMiniMenu.escapeText(url) + "</gradient>"
        + "</click></hover>");
    lines.add(DirectorMiniMenu.bar(theme));
    DirectorMiniMenu.deliver(sender, lines);
  }

  @Director(name = "edit", description = "Open a loaded menu in the web editor", descriptionKey = "holoui.command.edit")
  public void edit(
      @Param(name = "menu", description = "Loaded menu id", descriptionKey = "holoui.parameter.edit_menu", customHandler = ExistingMenuHandler.class)
      String menuName,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    String permission = ROOT_PERM + ".edit";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    MenuDefinitionData menu = plugin.getConfigManager().get(menuName).orElse(null);
    if (menu == null) {
      sender.sendMessage(plugin.getLocalization().legacy(
          HoloMessages.MENU_UNAVAILABLE,
          MessageArgs.builder().untrusted("menu", menuName).build()
      ));
      return;
    }
    String source = plugin.getConfigManager().getSource(menu.getId()).orElse(null);
    if (source == null) {
      sendEditorFailure(sender, menu.getId());
      return;
    }

    if (!HuiSettings.editorSyncEnabled() || plugin.getEditorSyncService() == null
        || !plugin.getEditorSyncService().isAvailable()
        || !sender.hasPermission(HoloSyncCommand.PERMISSION)) {
      deliverLegacyEditorLink(plugin, sender, menu.getId(), source, true);
      return;
    }

    sender.sendMessage(plugin.getLocalization().legacy(
        HoloMessages.SYNC_PREPARING,
        MessageArgs.builder().untrusted("subject", menu.getId()).build()
    ));
    plugin.getEditorSyncService().openMenu(menu.getId()).whenComplete((result, failure) ->
        runForSender(plugin, sender, () -> {
          if (failure == null) {
            deliverSyncLink(plugin, sender, result);
            return;
          }
          HoloUI.logExceptionStack(false, rootCause(failure),
              "Unable to create editor sync session for menu \"%s\"; using one-way handoff.",
              menu.getId());
          deliverLegacyEditorLink(plugin, sender, menu.getId(), source, true);
        }));
  }

  static void deliverLegacyEditorLink(HoloUI plugin, CommandSender sender, String menuId,
                                      String source, boolean fallback) {
    String url;
    try {
      url = EditorMenuHandoff.createUrl(HuiSettings.builderUrl(), menuId, source);
    } catch (EditorMenuHandoff.PayloadTooLargeException exception) {
      sender.sendMessage(plugin.getLocalization().legacy(
          HoloMessages.EDITOR_MENU_TOO_LARGE,
          MessageArgs.builder().untrusted("menu", menuId).build()
      ));
      return;
    } catch (RuntimeException exception) {
      HoloUI.logExceptionStack(true, exception,
          "Failed to prepare editor handoff for menu \"%s\".", menuId);
      sender.sendMessage(plugin.getLocalization().legacy(
          HoloMessages.EDITOR_MENU_FAILED,
          MessageArgs.builder().untrusted("menu", menuId).build()));
      return;
    }

    if (fallback) {
      sender.sendMessage(plugin.getLocalization().legacy(
          HoloMessages.SYNC_FALLBACK,
          MessageArgs.builder().untrusted("subject", menuId).build()));
    }

    if (!(sender instanceof Player)) {
      sender.sendMessage(plugin.getLocalization().legacy(
          HoloMessages.EDITOR_MENU_OPEN,
          MessageArgs.builder()
              .untrusted("menu", menuId)
              .untrusted("url", url)
              .build()
      ));
      return;
    }

    DirectorMiniMenu.Theme theme = DirectorMiniMenu.Theme.fromDirectorTheme(
        DirectorThemes.forProduct(DirectorProduct.HOLOUI));
    MessageArgs arguments = MessageArgs.builder().untrusted("menu", menuId).build();
    String label = plugin.getLocalization().text(HoloMessages.EDITOR_MENU_LINK, arguments);
    String hover = plugin.getLocalization().text(HoloMessages.EDITOR_MENU_HOVER, arguments);
    List<String> lines = new ArrayList<>();
    lines.add(DirectorMiniMenu.banner(plugin.getLocalization().text(HoloMessages.BUILDER_HEADER), theme));
    lines.add(editorEntryLine(url, label, hover, theme));
    lines.add(DirectorMiniMenu.bar(theme));
    DirectorMiniMenu.deliver(sender, lines);
  }

  static void deliverSyncLink(HoloUI plugin, CommandSender sender, EditorSyncOpenResult result) {
    MessageArgs linkArguments = MessageArgs.builder()
        .untrusted("subject", result.subjectId())
        .untrusted("session", art.arcane.holoui.editor.sync.EditorSyncService.abbreviate(result.sessionId()))
        .build();
    if (!(sender instanceof Player)) {
      sender.sendMessage(plugin.getLocalization().legacy(HoloMessages.SYNC_CAPABILITY_WARNING));
      sender.sendMessage(plugin.getLocalization().legacy(
          HoloMessages.SYNC_OPEN_CONSOLE,
          MessageArgs.builder()
              .untrusted("subject", result.subjectId())
              .untrusted("url", result.editorUrl())
              .build()
      ));
      return;
    }
    DirectorMiniMenu.Theme theme = DirectorMiniMenu.Theme.fromDirectorTheme(
        DirectorThemes.forProduct(DirectorProduct.HOLOUI));
    String open = plugin.getLocalization().text(HoloMessages.SYNC_OPEN_LABEL);
    String copy = plugin.getLocalization().text(HoloMessages.SYNC_COPY_LABEL);
    String revoke = plugin.getLocalization().text(HoloMessages.SYNC_REVOKE_LABEL);
    String hover = plugin.getLocalization().text(HoloMessages.SYNC_LINK_HOVER, linkArguments);
    String escapedHover = DirectorMiniMenu.escapeText(hover).replace("\\", "\\\\").replace("'", "\\'");
    String url = result.editorUrl();
    String line = "<hover:show_text:'" + escapedHover + "'>"
        + "<click:open_url:'" + url + "'><gradient:" + theme.primaryLeft() + ":"
        + theme.primaryRight() + ">[" + DirectorMiniMenu.escapeText(open) + "]</gradient></click> "
        + "<click:copy_to_clipboard:'" + url + "'><" + theme.muted() + ">["
        + DirectorMiniMenu.escapeText(copy) + "]</" + theme.muted() + "></click> "
        + "<click:run_command:'/holoui sync revoke session=" + result.sessionId() + "'><red>["
        + DirectorMiniMenu.escapeText(revoke) + "]</red></click></hover>";
    List<String> lines = new ArrayList<>();
    lines.add(DirectorMiniMenu.banner(plugin.getLocalization().text(HoloMessages.BUILDER_HEADER), theme));
    lines.add(line);
    lines.add(DirectorMiniMenu.bar(theme));
    DirectorMiniMenu.deliver(sender, lines);
  }

  private static void runForSender(HoloUI plugin, CommandSender sender, Runnable task) {
    boolean accepted = sender instanceof Player player
        ? SchedulerUtils.runEntity(plugin, player, task)
        : SchedulerUtils.runGlobal(plugin, task);
    if (!accepted) {
      HoloUI.log(java.util.logging.Level.WARNING,
          "Unable to schedule editor sync feedback for %s.", sender.getName());
    }
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable current = failure;
    while (current instanceof java.util.concurrent.CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  static String editorEntryLine(String url, String label, String hover, DirectorMiniMenu.Theme theme) {
    return "<hover:show_text:'" + DirectorMiniMenu.escapeText(hover).replace("\\", "\\\\").replace("'", "\\'") + "'>"
        + "<click:open_url:'" + url + "'>"
        + "<" + theme.muted() + ">⇀</" + theme.muted() + "> "
        + "<gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">"
        + DirectorMiniMenu.escapeText(label) + "</gradient>"
        + "</click></hover>";
  }

  static String menuEntryLine(String menu, String hover, DirectorMiniMenu.Theme theme) {
    return "<hover:show_text:'" + DirectorMiniMenu.escapeText(hover).replace("\\", "\\\\").replace("'", "\\'")
        + "'><click:run_command:/holoui open " + menu + ">"
        + "<" + theme.muted() + ">⇀</" + theme.muted() + "> "
        + "<gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">" + DirectorMiniMenu.escapeText(menu) + "</gradient>"
        + "</click></hover>";
  }

  private boolean openMenu(Player player, CommandSender feedback, String menuName, boolean includeRootPermission) {
    MenuDefinitionData ui = plugin.getConfigManager().get(menuName).orElse(null);
    if (ui == null) {
      feedback.sendMessage(plugin.getLocalization().legacy(
          HoloMessages.MENU_UNAVAILABLE,
          MessageArgs.builder().untrusted("menu", menuName).build()
      ));
      return false;
    }

    if (includeRootPermission && !player.hasPermission(ROOT_PERM + ".open")) {
      sendPermissionDenied(feedback, ROOT_PERM + ".open");
      return false;
    }

    if (!player.hasPermission("holoui.open." + ui.getId())) {
      feedback.sendMessage(plugin.getLocalization().legacy(
          HoloMessages.MENU_PERMISSION_DENIED,
          MessageArgs.builder().untrusted("menu", ui.getId()).build()
      ));
      return false;
    }

    try {
      plugin.getSessionManager().createNewSession(player, ui);
      return true;
    } catch (Throwable e) {
      HoloUI.logExceptionStack(true, e, "Error opening menu \"%s\".", ui.getId());
      feedback.sendMessage(plugin.getLocalization().legacy(
          HoloMessages.MENU_OPEN_FAILED,
          MessageArgs.builder().untrusted("menu", ui.getId()).build()
      ));
      return false;
    }
  }

  private void sendPermissionDenied(CommandSender sender, String permission) {
    sender.sendMessage(plugin.getLocalization().legacy(
        HoloMessages.PERMISSION_DENIED,
        MessageArgs.builder().untrusted("permission", permission).build()
    ));
  }

  private void sendCreateResult(CommandSender sender, String requestedId,
                                HologramCreationService.Creation creation, Throwable failure) {
    Runnable feedback = () -> {
      playCreateOutcome(sender, failure == null);
      if (failure == null) {
        sender.sendMessage(plugin.getLocalization().legacy(
            HoloMessages.HOLOGRAM_CREATED,
            MessageArgs.builder()
                .untrusted("hologram", creation.board().id())
                .untrusted("menu", creation.menu().id())
                .build()
        ));
        return;
      }
      Throwable cause = rootCause(failure);
      if (cause instanceof FileAlreadyExistsException) {
        sender.sendMessage(plugin.getLocalization().legacy(
            HoloMessages.HOLOGRAM_ALREADY_EXISTS,
            MessageArgs.builder().untrusted("hologram", requestedId).build()
        ));
        return;
      }
      if (cause instanceof HologramCreationService.DurabilityUncertainException) {
        sender.sendMessage(plugin.getLocalization().legacy(
            HoloMessages.HOLOGRAM_DURABILITY_UNCERTAIN,
            MessageArgs.builder().untrusted("hologram", requestedId).build()
        ));
        return;
      }
      if (!(cause instanceof IllegalArgumentException)
          && !(cause instanceof CancellationException)) {
        HoloUI.logExceptionStack(true, cause,
            "Persistent hologram creation failed for \"%s\".", requestedId);
      }
      sender.sendMessage(plugin.getLocalization().legacy(
          HoloMessages.HOLOGRAM_CREATE_FAILED,
          MessageArgs.builder().untrusted("hologram", requestedId).build()
      ));
    };
    boolean accepted = sender instanceof Player player
        ? SchedulerUtils.runEntity(plugin, player, feedback)
        : SchedulerUtils.runGlobal(plugin, feedback);
    if (!accepted) {
      HoloUI.log(Level.WARNING, "Unable to schedule hologram creation feedback for %s.",
          sender.getName());
    }
  }

  private static void playCreateOutcome(CommandSender sender, boolean successful) {
    if (!(sender instanceof Player player) || !player.isOnline()) {
      return;
    }
    DirectorTheme theme = DirectorThemes.forProduct(DirectorProduct.HOLOUI);
    player.playSound(player.getLocation(),
        successful ? theme.getSuccessSound() : theme.getErrorSound(),
        SoundCategory.MASTER, 0.8F, successful ? 1.3F : 0.85F);
  }

  private void sendEditorFailure(CommandSender sender, String menuId) {
    sender.sendMessage(plugin.getLocalization().legacy(
        HoloMessages.EDITOR_MENU_FAILED,
        MessageArgs.builder().untrusted("menu", menuId).build()
    ));
  }

  public static class ExistingMenuHandler implements DirectorParameterHandler<String> {
    @Override
    public KList<String> getPossibilities() {
      KList<String> out = new KList<>();
      if (HoloUI.INSTANCE == null || HoloUI.INSTANCE.getConfigManager() == null) {
        return out;
      }

      out.addAll(HoloUI.INSTANCE.getConfigManager().keys());
      out.removeDuplicates();
      return out;
    }

    @Override
    public String toString(String value) {
      return value == null ? "" : value;
    }

    @Override
    public String parse(String in, boolean force) throws DirectorParsingException {
      if (in == null || in.trim().isEmpty()) {
        throw new DirectorParsingException(HoloLocalization.globalText(HoloMessages.ERROR_MENU_NAME_REQUIRED));
      }

      String value = in.trim();
      for (String candidate : getPossibilities()) {
        if (candidate.equalsIgnoreCase(value)) {
          return candidate;
        }
      }

      return value;
    }

    @Override
    public boolean supports(Class<?> type) {
      return type == String.class;
    }
  }

  public static final class MenuNameHandler extends ExistingMenuHandler {
    @Override
    public KList<String> getPossibilities() {
      KList<String> menus = super.getPossibilities();
      menus.add(0, "*");
      return menus;
    }
  }

  public static final class HologramIdHandler implements DirectorParameterHandler<String> {
    @Override
    public KList<String> getPossibilities() {
      return new KList<>();
    }

    @Override
    public String toString(String value) {
      return value == null ? "" : value;
    }

    @Override
    public String parse(String in, boolean force) throws DirectorParsingException {
      if (in == null || in.isBlank()) {
        throw new DirectorParsingException(
            HoloLocalization.globalText(HoloMessages.ERROR_HOLOGRAM_ID_REQUIRED));
      }
      try {
        return BoardIds.canonicalize(in);
      } catch (IllegalArgumentException failure) {
        throw new DirectorParsingException(failure.getMessage());
      }
    }

    @Override
    public boolean supports(Class<?> type) {
      return type == String.class;
    }
  }

}
