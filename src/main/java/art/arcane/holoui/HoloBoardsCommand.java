package art.arcane.holoui;

import art.arcane.holoui.board.BoardDefinition;
import art.arcane.holoui.board.BoardFollow;
import art.arcane.holoui.board.BoardFollowMode;
import art.arcane.holoui.board.BoardFollowPose;
import art.arcane.holoui.board.BoardFollowRotation;
import art.arcane.holoui.board.BoardFollowTransform;
import art.arcane.holoui.board.BoardIds;
import art.arcane.holoui.board.BoardRevisionConflictException;
import art.arcane.holoui.board.BoardRuntimeManager;
import art.arcane.holoui.board.BoardService;
import art.arcane.holoui.board.BoardTransform;
import art.arcane.holoui.board.BoardVisibility;
import art.arcane.holoui.board.BoardVisibilityMode;
import art.arcane.holoui.config.MenuDefinitionData;
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
import art.arcane.volmlib.util.bukkit.Events;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.nio.file.FileAlreadyExistsException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Level;

@Director(name = "boards", description = "Persistent world board tools", descriptionKey = "holoui.command.boards.root")
public final class HoloBoardsCommand {
  public static final String PERMISSION = HoloCommand.ROOT_PERM + ".boards";
  static final int LIST_PAGE_SIZE = 10;

  private final Map<UUID, BoardEditSession> editSessions = new ConcurrentHashMap<>();

  public HoloBoardsCommand() {
    HoloUI plugin = HoloUI.INSTANCE;
    if (plugin != null) {
      Events.listen(plugin, PlayerQuitEvent.class, event -> discardEdit(event.getPlayer()));
    }
  }

  public void shutdown() {
    editSessions.clear();
  }

  @Director(name = "list", description = "List persistent boards by page", descriptionKey = "holoui.command.boards.list")
  public void list(
      @Param(name = "page", description = "One-based board list page", descriptionKey = "holoui.parameter.page", defaultValue = "1")
      int page,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    List<BoardDefinition> boards = service().list();
    PageWindow window;
    try {
      window = pageWindow(boards.size(), page);
    } catch (IllegalArgumentException exception) {
      send(sender, HoloMessages.BOARDS_LIST_PAGE_INVALID,
          MessageArgs.builder()
              .untrusted("page", page)
              .untrusted("pages", pageCount(boards.size()))
              .build());
      return;
    }
    send(sender, HoloMessages.BOARDS_LIST_HEADER,
        MessageArgs.builder().untrusted("count", boards.size()).build());
    if (boards.isEmpty()) {
      send(sender, HoloMessages.BOARDS_LIST_EMPTY);
      return;
    }
    send(sender, HoloMessages.BOARDS_LIST_PAGE,
        MessageArgs.builder()
            .untrusted("page", window.page())
            .untrusted("pages", window.pages())
            .untrusted("from", window.startIndex() + 1)
            .untrusted("to", window.endIndex())
            .build());
    for (BoardDefinition board : boards.subList(window.startIndex(), window.endIndex())) {
      send(sender, HoloMessages.BOARDS_LIST_ENTRY, boardSummary(board));
    }
  }

  @Director(name = "reload", description = "Reload persistent board files", descriptionKey = "holoui.command.boards.reload")
  public void reload(
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    service().reload().whenComplete((result, failure) -> {
      if (failure != null) {
        reportFailure(sender, "reload", failure);
        return;
      }
      sendLater(sender, HoloMessages.BOARDS_RELOADED,
          MessageArgs.builder()
              .untrusted("loaded", result.loaded())
              .untrusted("retained", result.retained())
              .untrusted("removed", result.removed())
              .untrusted("failures", result.failures().size())
              .build());
    });
  }

  @Director(name = "near", description = "List boards near your current position", descriptionKey = "holoui.command.boards.near")
  public void near(
      @Param(name = "radius", description = "Horizontal search radius", descriptionKey = "holoui.parameter.board_radius", defaultValue = "64")
      double radius,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    Player player = requirePlayer(sender);
    if (player == null) {
      return;
    }

    try {
      BoardCommandSupport.nonNegativeFinite(radius, "radius");
    } catch (IllegalArgumentException exception) {
      invalid(sender, exception);
      return;
    }

    Location location = player.getLocation();
    World world = location.getWorld();
    if (world == null) {
      send(sender, HoloMessages.BOARDS_WORLD_UNAVAILABLE,
          MessageArgs.builder().untrusted("world", "unknown").build());
      return;
    }
    List<BoardDefinition> boards = runtime().queryEffective(
        world.getUID(), location.getX(), location.getZ(), radius);
    send(sender, HoloMessages.BOARDS_NEAR_HEADER,
        MessageArgs.builder().untrusted("radius", radius).untrusted("count", boards.size()).build());
    if (boards.isEmpty()) {
      send(sender, HoloMessages.BOARDS_NEAR_EMPTY);
      return;
    }
    for (BoardDefinition board : boards) {
      BoardTransform transform = board.transform();
      double distance = Math.hypot(transform.x() - location.getX(), transform.z() - location.getZ());
      send(sender, HoloMessages.BOARDS_NEAR_ENTRY,
          MessageArgs.builder()
              .untrusted("board", board.id())
              .untrusted("distance", format(distance))
              .untrusted("menu", board.rootMenuId())
              .build());
    }
  }

  @Director(name = "info", description = "Show one board's complete state", descriptionKey = "holoui.command.boards.info")
  public void info(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    CommandBoardState state = commandState(sender, id);
    if (state == null) {
      return;
    }
    sendInfo(sender, state.definition(), state.effectiveTransform(), state.edit() != null);
  }

  @Director(name = "create", description = "Create a board at your current position", descriptionKey = "holoui.command.boards.create")
  public void create(
      @Param(name = "board", description = "New persistent board id", descriptionKey = "holoui.parameter.board_id")
      String id,
      @Param(name = "menu", description = "Root menu id", descriptionKey = "holoui.parameter.board_menu", customHandler = HoloCommand.ExistingMenuHandler.class)
      String menuId,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    Player player = requirePlayer(sender);
    if (player == null) {
      return;
    }
    String rootMenu = resolveMenu(sender, menuId);
    if (rootMenu == null) {
      return;
    }

    try {
      Location location = player.getLocation().clone();
      World world = location.getWorld();
      if (world == null) {
        send(sender, HoloMessages.BOARDS_WORLD_UNAVAILABLE,
            MessageArgs.builder().untrusted("world", "unknown").build());
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
      BoardDefinition definition = BoardDefinition.create(id, rootMenu, transform);
      service().create(definition).whenComplete((created, failure) -> {
        if (failure != null) {
          reportFailure(sender, definition.id(), failure);
          return;
        }
        sendLater(sender, HoloMessages.BOARDS_CREATED,
            MessageArgs.builder()
                .untrusted("board", created.id())
                .untrusted("revision", created.revision())
                .build());
      });
    } catch (IllegalArgumentException exception) {
      invalid(sender, exception);
    }
  }

  @Director(name = "delete", aliases = {"remove"}, description = "Delete a persistent board", descriptionKey = "holoui.command.boards.delete")
  public void delete(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    BoardDefinition board = publishedBoard(sender, id);
    if (board == null || rejectIdentityMutation(sender, board.id())) {
      return;
    }
    service().delete(board.id(), board.revision()).whenComplete((deleted, failure) -> {
      if (failure != null) {
        reportFailure(sender, board.id(), failure);
        return;
      }
      sendLater(sender, HoloMessages.BOARDS_DELETED,
          MessageArgs.builder().untrusted("board", deleted.id()).build());
    });
  }

  @Director(name = "rename", description = "Rename a persistent board", descriptionKey = "holoui.command.boards.rename")
  public void rename(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "newBoard", description = "New persistent board id", descriptionKey = "holoui.parameter.board_new_id")
      String newId,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    BoardDefinition board = publishedBoard(sender, id);
    if (board == null || rejectIdentityMutation(sender, board.id())) {
      return;
    }
    try {
      String canonicalNewId = BoardIds.canonicalize(newId);
      service().rename(board.id(), canonicalNewId, board.revision()).whenComplete((renamed, failure) -> {
        if (failure != null) {
          reportFailure(sender, board.id(), failure);
          return;
        }
        sendLater(sender, HoloMessages.BOARDS_RENAMED,
            MessageArgs.builder()
                .untrusted("oldBoard", board.id())
                .untrusted("board", renamed.id())
                .untrusted("revision", renamed.revision())
                .build());
      });
    } catch (IllegalArgumentException exception) {
      invalid(sender, exception);
    }
  }

  @Director(name = "copy", description = "Copy a persistent board under a new id", descriptionKey = "holoui.command.boards.copy")
  public void copy(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "newBoard", description = "New persistent board id", descriptionKey = "holoui.parameter.board_new_id")
      String newId,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    BoardDefinition board = visibleBoard(sender, id);
    if (board == null) {
      return;
    }
    try {
      BoardDefinition copy = BoardCommandSupport.copy(board, newId);
      service().create(copy).whenComplete((created, failure) -> {
        if (failure != null) {
          reportFailure(sender, copy.id(), failure);
          return;
        }
        sendLater(sender, HoloMessages.BOARDS_COPIED,
            MessageArgs.builder()
                .untrusted("source", board.id())
                .untrusted("board", created.id())
                .build());
      });
    } catch (IllegalArgumentException exception) {
      invalid(sender, exception);
    }
  }

  @Director(name = "move", description = "Move a board to absolute or ~relative coordinates", descriptionKey = "holoui.command.boards.move")
  public void move(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "x", description = "Absolute or ~relative X", descriptionKey = "holoui.parameter.board_x")
      String x,
      @Param(name = "y", description = "Absolute or ~relative Y", descriptionKey = "holoui.parameter.board_y")
      String y,
      @Param(name = "z", description = "Absolute or ~relative Z", descriptionKey = "holoui.parameter.board_z")
      String z,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    mutateEffectiveTransform(sender, id, "move",
        transform -> BoardCommandSupport.move(transform, x, y, z));
  }

  @Director(name = "movehere", aliases = {"tphere"}, description = "Move a board to your current position", descriptionKey = "holoui.command.boards.movehere")
  public void moveHere(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    Player player = requirePlayer(sender);
    if (player == null) {
      return;
    }
    Location location = player.getLocation().clone();
    World world = location.getWorld();
    if (world == null) {
      send(sender, HoloMessages.BOARDS_WORLD_UNAVAILABLE,
          MessageArgs.builder().untrusted("world", "unknown").build());
      return;
    }
    mutateEffectiveTransform(sender, id, "movehere",
        transform -> BoardCommandSupport.moveTo(
            transform,
            world.getKey().toString(),
            world.getUID(),
            location.getX(),
            location.getY(),
            location.getZ()
        ));
  }

  @Director(name = "tp", description = "Teleport to a persistent board", descriptionKey = "holoui.command.boards.tp")
  public void teleport(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    Player player = requirePlayer(sender);
    if (player == null) {
      return;
    }
    CommandBoardState state = commandState(sender, id);
    if (state == null) {
      return;
    }
    BoardDefinition board = state.definition();
    BoardTransform transform = state.effectiveTransform();
    World world = Bukkit.getWorld(transform.worldUuid());
    if (world == null || !world.getKey().toString().equals(transform.worldKey())) {
      send(sender, HoloMessages.BOARDS_WORLD_UNAVAILABLE,
          MessageArgs.builder().untrusted("world", transform.worldKey()).build());
      return;
    }

    Location destination = new Location(world, transform.x(), transform.y(), transform.z(),
        (float) transform.yaw(), (float) transform.pitch());
    player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)
        .whenComplete((success, failure) -> {
          if (failure != null || !Boolean.TRUE.equals(success)) {
            Throwable cause = failure == null ? new IllegalStateException("teleport was rejected") : failure;
            reportFailure(sender, board.id(), cause);
            return;
          }
          sendLater(sender, HoloMessages.BOARDS_TELEPORTED,
              MessageArgs.builder().untrusted("board", board.id()).build());
        });
  }

  @Director(name = "rotate", description = "Set absolute or ~relative yaw, pitch, and roll", descriptionKey = "holoui.command.boards.rotate")
  public void rotate(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "yaw", description = "Absolute or ~relative yaw", descriptionKey = "holoui.parameter.board_yaw")
      String yaw,
      @Param(name = "pitch", description = "Absolute or ~relative pitch", descriptionKey = "holoui.parameter.board_pitch")
      String pitch,
      @Param(name = "roll", description = "Absolute or ~relative roll", descriptionKey = "holoui.parameter.board_roll")
      String roll,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    mutateEffectiveTransform(sender, id, "rotate",
        transform -> BoardCommandSupport.rotate(transform, yaw, pitch, roll));
  }

  @Director(name = "scale", description = "Set an absolute or ~relative board scale", descriptionKey = "holoui.command.boards.scale")
  public void scale(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "scale", description = "Absolute or ~relative scale", descriptionKey = "holoui.parameter.board_scale")
      String scale,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    mutateScale(sender, id, scale);
  }

  @Director(name = "align", description = "Align board position axes to another board", descriptionKey = "holoui.command.boards.align")
  public void align(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "reference", description = "Reference board id", descriptionKey = "holoui.parameter.board_reference", customHandler = BoardIdHandler.class)
      String referenceId,
      @Param(name = "axes", description = "Axes: x, y, z, xy, xz, yz, or xyz", descriptionKey = "holoui.parameter.board_axes", customHandler = AxesHandler.class)
      String axes,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    CommandBoardState reference = commandState(sender, referenceId);
    if (reference == null) {
      return;
    }
    mutateEffectiveTransform(sender, id, "align",
        transform -> BoardCommandSupport.align(transform, reference.effectiveTransform(), axes));
  }

  @Director(name = "menu", aliases = {"root"}, description = "Set a board's root menu", descriptionKey = "holoui.command.boards.menu")
  public void menu(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "menu", description = "Root menu id", descriptionKey = "holoui.parameter.board_menu", customHandler = HoloCommand.ExistingMenuHandler.class)
      String menuId,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    String rootMenu = resolveMenu(sender, menuId);
    if (rootMenu == null) {
      return;
    }
    mutate(sender, id, "menu", board -> board.withRootMenu(rootMenu));
  }

  @Director(name = "addrow", description = "Append a text row to a board's root menu", descriptionKey = "holoui.command.boards.addrow")
  public void addRow(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "text", description = "MiniMessage row text", descriptionKey = "holoui.parameter.row_text")
      String text,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    mutateRootMenu(sender, id, "addrow", document -> MenuRowMutations.addTextRow(document, text));
  }

  @Director(name = "insertrow", description = "Insert a text row into a board's root menu", descriptionKey = "holoui.command.boards.insertrow")
  public void insertRow(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "row", description = "One-based component row", descriptionKey = "holoui.parameter.row_index")
      int row,
      @Param(name = "text", description = "MiniMessage row text", descriptionKey = "holoui.parameter.row_text")
      String text,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    mutateRootMenu(sender, id, "insertrow",
        document -> MenuRowMutations.insertTextRow(document, row, text));
  }

  @Director(name = "setrow", description = "Set text on a board root-menu row", descriptionKey = "holoui.command.boards.setrow")
  public void setRow(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "row", description = "One-based component row", descriptionKey = "holoui.parameter.row_index")
      int row,
      @Param(name = "text", description = "MiniMessage row text", descriptionKey = "holoui.parameter.row_text")
      String text,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    mutateRootMenu(sender, id, "setrow",
        document -> MenuRowMutations.setTextRow(document, row, text));
  }

  @Director(name = "removerow", description = "Remove a board root-menu row", descriptionKey = "holoui.command.boards.removerow")
  public void removeRow(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "row", description = "One-based component row", descriptionKey = "holoui.parameter.row_index")
      int row,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    mutateRootMenu(sender, id, "removerow", document -> MenuRowMutations.removeRow(document, row));
  }

  @Director(name = "offsetrow", description = "Move a board root-menu row with absolute or ~relative offsets", descriptionKey = "holoui.command.boards.offsetrow")
  public void offsetRow(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
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
    mutateRootMenu(sender, id, "offsetrow",
        document -> MenuRowMutations.offsetRow(document, row, x, y, z));
  }

  @Director(name = "seticon", description = "Replace a row icon in the board root menu", descriptionKey = "holoui.command.boards.seticon")
  public void setIcon(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "row", description = "One-based component row", descriptionKey = "holoui.parameter.row_index")
      int row,
      @Param(name = "type", description = "Icon type", descriptionKey = "holoui.parameter.row_icon_type", customHandler = HoloMenusCommand.IconTypeHandler.class)
      String type,
      @Param(name = "value", description = "Icon content or registry id", descriptionKey = "holoui.parameter.row_icon_value")
      String value,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    mutateRootMenu(sender, id, "seticon",
        document -> HoloMenusCommand.setIconMutation(document, row, type, value));
  }

  @Director(name = "style", description = "Set or clear one row display-style property", descriptionKey = "holoui.command.boards.style")
  public void style(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "row", description = "One-based component row", descriptionKey = "holoui.parameter.row_index")
      int row,
      @Param(name = "property", description = "Display-style property", descriptionKey = "holoui.parameter.row_style_property", customHandler = HoloMenusCommand.StylePropertyHandler.class)
      String property,
      @Param(name = "value", description = "Style value, or * to clear", descriptionKey = "holoui.parameter.row_style_value")
      String value,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    mutateRootMenu(sender, id, "style",
        document -> MenuRowMutations.setStyle(document, row, property, value));
  }

  @Director(name = "image", description = "Replace the board root menu with one centered image file", descriptionKey = "holoui.command.boards.image")
  public void image(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "path", description = "File beneath the HoloUI images folder", descriptionKey = "holoui.parameter.image_path")
      String path,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    mutateRootMenu(sender, id, "image",
        document -> HoloMenusCommand.replaceWithImageMutation(document, path));
  }

  @Director(name = "ranges", description = "Set board view and interaction ranges", descriptionKey = "holoui.command.boards.ranges")
  public void ranges(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "viewRange", description = "Positive view range", descriptionKey = "holoui.parameter.board_view_range")
      double viewRange,
      @Param(name = "interactionRange", description = "Positive interaction range", descriptionKey = "holoui.parameter.board_interaction_range")
      double interactionRange,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    mutate(sender, id, "ranges", board -> board.withVisibility(
        board.visibility().withRanges(viewRange, interactionRange)));
  }

  @Director(name = "visibility", description = "Set visibility mode and permission nodes", descriptionKey = "holoui.command.boards.visibility")
  public void visibility(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "mode", description = "Visibility mode: public, permission, or hidden", descriptionKey = "holoui.parameter.board_visibility")
      BoardVisibilityMode mode,
      @Param(name = "viewPermission", description = "View permission, or -", descriptionKey = "holoui.parameter.board_view_permission")
      String viewPermission,
      @Param(name = "interactPermission", description = "Interaction permission, or -", descriptionKey = "holoui.parameter.board_interact_permission")
      String interactPermission,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    mutate(sender, id, "visibility", board -> board.withVisibility(
        BoardCommandSupport.visibility(board.visibility(), mode, viewPermission, interactPermission)));
  }

  @Director(name = "permissions", description = "Change board view and interaction permissions", descriptionKey = "holoui.command.boards.permissions")
  public void permissions(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "viewPermission", description = "View permission, or -", descriptionKey = "holoui.parameter.board_view_permission")
      String viewPermission,
      @Param(name = "interactPermission", description = "Interaction permission, or -", descriptionKey = "holoui.parameter.board_interact_permission")
      String interactPermission,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    mutate(sender, id, "permissions", board -> board.withVisibility(
        BoardCommandSupport.permissions(board.visibility(), viewPermission, interactPermission)));
  }

  @Director(name = "follow", description = "Follow an online player with fixed, yaw, or full rotation", descriptionKey = "holoui.command.boards.follow")
  public void follow(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "player", description = "Online target player", descriptionKey = "holoui.parameter.board_player", customHandler = OnlinePlayerHandler.class)
      String playerName,
      @Param(name = "rotation", description = "Follow rotation: fixed, yaw, or full", descriptionKey = "holoui.parameter.board_follow_rotation")
      BoardFollowRotation rotation,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    Player target = onlinePlayer(playerName);
    if (target == null) {
      send(sender, HoloMessages.BOARDS_PLAYER_UNAVAILABLE,
          MessageArgs.builder().untrusted("player", playerName).build());
      return;
    }
    CommandBoardState state = commandState(sender, id);
    if (state == null) {
      return;
    }
    captureLocation(sender, target, location -> {
      try {
        BoardDefinition changed = BoardCommandSupport.follow(
            state.definition(),
            state.effectiveTransform(),
            location,
            target.getUniqueId(),
            rotation
        );
        applyMutation(sender, state, "follow", changed, state.effectiveTransform());
      } catch (IllegalArgumentException exception) {
        invalid(sender, exception);
      }
    });
  }

  @Director(name = "unfollow", description = "Stop a board following a player", descriptionKey = "holoui.command.boards.unfollow")
  public void unfollow(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    CommandBoardState state = commandState(sender, id);
    if (state == null) {
      return;
    }
    BoardDefinition changed = BoardCommandSupport.unfollow(
        state.definition(), state.effectiveTransform());
    applyMutation(sender, state, "unfollow", changed, state.effectiveTransform());
  }

  @Director(name = "edit", description = "Start a staged board edit session", descriptionKey = "holoui.command.boards.edit")
  public void edit(
      @Param(name = "board", description = "Persistent board id", descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    Player player = requirePlayer(sender);
    if (player == null) {
      return;
    }
    BoardDefinition board = publishedBoard(sender, id);
    if (board == null) {
      return;
    }
    BoardTransform effectiveTransform = effectiveTransform(sender, board);
    if (effectiveTransform == null) {
      return;
    }
    BoardEditSession session = new BoardEditSession(board, effectiveTransform);
    BoardEditSession previous = editSessions.putIfAbsent(player.getUniqueId(), session);
    if (previous != null) {
      send(sender, HoloMessages.BOARDS_EDIT_ALREADY_ACTIVE,
          MessageArgs.builder().untrusted("board", previous.id()).build());
      return;
    }
    runtime().previewBoard(player, board, effectiveTransform);
    send(sender, HoloMessages.BOARDS_EDIT_STARTED,
        MessageArgs.builder()
            .untrusted("board", board.id())
            .untrusted("revision", board.revision())
            .build());
    sendInfo(sender, board, effectiveTransform, true);
  }

  @Director(name = "editweb", aliases = {"webedit"},
      description = "Open a board project in the web editor",
      descriptionKey = "holoui.command.boards.editweb")
  public void editWeb(
      @Param(name = "board", description = "Persistent board id",
          descriptionKey = "holoui.parameter.board_id", customHandler = BoardIdHandler.class)
      String id,
      @Param(name = "sender", contextual = true, description = "Command sender context",
          descriptionKey = "holoui.parameter.sender") CommandSender sender
  ) {
    String permission = PERMISSION + ".editweb";
    if (!sender.hasPermission(permission)) {
      send(sender, HoloMessages.PERMISSION_DENIED,
          MessageArgs.builder().untrusted("permission", permission).build());
      return;
    }
    BoardDefinition board = publishedBoard(sender, id);
    if (board == null) {
      return;
    }
    String source = plugin().getConfigManager().getSource(board.rootMenuId()).orElse(null);
    if (source == null) {
      send(sender, HoloMessages.BOARDS_MENU_UNAVAILABLE,
          MessageArgs.builder().untrusted("menu", board.rootMenuId()).build());
      return;
    }
    if (!art.arcane.holoui.config.HuiSettings.editorSyncEnabled()
        || plugin().getEditorSyncService() == null
        || !plugin().getEditorSyncService().isAvailable()
        || !sender.hasPermission(HoloSyncCommand.PERMISSION)) {
      HoloCommand.deliverLegacyEditorLink(plugin(), sender, board.rootMenuId(), source, true);
      return;
    }
    send(sender, HoloMessages.SYNC_PREPARING,
        MessageArgs.builder().untrusted("subject", board.id()).build());
    plugin().getEditorSyncService().openBoard(board.id()).whenComplete((result, failure) ->
        runForSender(sender, () -> {
          if (failure == null) {
            HoloCommand.deliverSyncLink(plugin(), sender, result);
            return;
          }
          HoloUI.logExceptionStack(false, BoardCommandSupport.rootCause(failure),
              "Unable to create editor sync session for board \"%s\"; using its root-menu handoff.",
              board.id());
          HoloCommand.deliverLegacyEditorLink(plugin(), sender, board.rootMenuId(), source, true);
        }));
  }

  @Director(name = "save", description = "Persist your staged board edit", descriptionKey = "holoui.command.boards.save")
  public void save(
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    Player player = requirePlayer(sender);
    if (player == null) {
      return;
    }
    UUID playerId = player.getUniqueId();
    BoardEditSession session = editSessions.get(playerId);
    if (session == null) {
      send(sender, HoloMessages.BOARDS_EDIT_NONE);
      return;
    }
    BoardDefinition staged = session.beginSave();
    if (staged == null) {
      send(sender, HoloMessages.BOARDS_EDIT_BUSY,
          MessageArgs.builder().untrusted("board", session.id()).build());
      return;
    }

    service().update(session.id(), session.expectedRevision(), ignored -> staged)
        .whenComplete((saved, failure) -> {
          if (failure != null) {
            session.retrySave();
            reportFailure(sender, session.id(), failure);
            return;
          }
          editSessions.remove(playerId, session);
          runForSender(sender, () -> {
            runtime().clearBoardPreview(player, saved.uuid());
            send(sender, HoloMessages.BOARDS_EDIT_SAVED,
                MessageArgs.builder()
                    .untrusted("board", saved.id())
                    .untrusted("revision", saved.revision())
                    .build());
          });
        });
  }

  @Director(name = "cancel", description = "Discard your staged board edit", descriptionKey = "holoui.command.boards.cancel")
  public void cancel(
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender)) {
      return;
    }
    Player player = requirePlayer(sender);
    if (player == null) {
      return;
    }
    BoardEditSession active = editSessions.get(player.getUniqueId());
    if (active != null && active.cancel() == null) {
      send(sender, HoloMessages.BOARDS_EDIT_BUSY,
          MessageArgs.builder().untrusted("board", active.id()).build());
      return;
    }
    BoardEditSession removed = editSessions.remove(player.getUniqueId());
    if (removed == null) {
      send(sender, HoloMessages.BOARDS_EDIT_NONE);
      return;
    }
    runtime().clearBoardPreview(player, removed.snapshot().definition().uuid());
    send(sender, HoloMessages.BOARDS_EDIT_CANCELLED,
        MessageArgs.builder().untrusted("board", removed.id()).build());
  }

  private void mutate(CommandSender sender, String id, String operation,
                      UnaryOperator<BoardDefinition> update) {
    BoardDefinition published = publishedBoard(sender, id);
    if (published == null) {
      return;
    }
    BoardEditSession edit = matchingEdit(sender, published.id());
    if (edit != null) {
      try {
        BoardEditSession.Snapshot snapshot = edit.snapshot();
        BoardDefinition changed = update.apply(snapshot.definition());
        applyMutation(
            sender,
            new CommandBoardState(
                snapshot.definition(), snapshot.effectiveTransform(), edit, snapshot),
            operation,
            changed,
            snapshot.effectiveTransform()
        );
      } catch (IllegalArgumentException | IllegalStateException exception) {
        invalid(sender, exception);
      }
      return;
    }

    BoardDefinition candidate;
    try {
      candidate = update.apply(published);
    } catch (IllegalArgumentException exception) {
      invalid(sender, exception);
      return;
    }
    persistMutation(sender, published, operation, candidate);
  }

  private void mutateRootMenu(CommandSender sender, String id, String operation,
                              UnaryOperator<JsonObject> mutation) {
    if (!checkPermission(sender)) {
      return;
    }
    BoardDefinition board = visibleBoard(sender, id);
    if (board == null) {
      return;
    }
    HoloMenusCommand.mutate(sender, board.rootMenuId(), operation, mutation, PERMISSION);
  }

  private void mutateScale(CommandSender sender, String id, String scale) {
    BoardDefinition published = publishedBoard(sender, id);
    if (published == null) {
      return;
    }
    BoardEditSession edit = matchingEdit(sender, published.id());
    if (edit == null) {
      mutate(sender, id, "scale", board -> board.withTransform(
          BoardCommandSupport.scale(board.transform(), scale)));
      return;
    }

    try {
      BoardEditSession.Snapshot snapshot = edit.snapshot();
      BoardDefinition changed = snapshot.definition().withTransform(
          BoardCommandSupport.scale(snapshot.definition().transform(), scale));
      BoardTransform changedEffective = BoardCommandSupport.scale(
          snapshot.effectiveTransform(), scale);
      applyMutation(
          sender,
          new CommandBoardState(
              snapshot.definition(), snapshot.effectiveTransform(), edit, snapshot),
          "scale",
          changed,
          changedEffective
      );
    } catch (IllegalArgumentException | IllegalStateException exception) {
      invalid(sender, exception);
    }
  }

  private void mutateEffectiveTransform(CommandSender sender, String id, String operation,
                                        UnaryOperator<BoardTransform> update) {
    CommandBoardState state = commandState(sender, id);
    if (state == null) {
      return;
    }

    BoardTransform changedEffective;
    try {
      changedEffective = update.apply(state.effectiveTransform());
    } catch (IllegalArgumentException exception) {
      invalid(sender, exception);
      return;
    }

    if (state.definition().follow().mode() == BoardFollowMode.NONE) {
      BoardDefinition changed = state.definition().withTransform(changedEffective);
      applyMutation(sender, state, operation, changed, changedEffective);
      return;
    }

    Player target = followTarget(sender, state.definition());
    if (target == null) {
      return;
    }
    captureLocation(sender, target, location -> {
      try {
        BoardDefinition changed = BoardCommandSupport.reencodeEffectiveTransform(
            state.definition(), changedEffective, location);
        applyMutation(sender, state, operation, changed, changedEffective);
      } catch (IllegalArgumentException exception) {
        invalid(sender, exception);
      }
    });
  }

  private void applyMutation(CommandSender sender, CommandBoardState state, String operation,
                             BoardDefinition changed, BoardTransform changedEffective) {
    BoardEditSession edit = state.edit();
    if (edit == null) {
      persistMutation(sender, state.definition(), operation, changed);
      return;
    }

    try {
      BoardEditSession.Snapshot staged = edit.stage(
          state.editSnapshot(), changed, changedEffective);
      if (staged == null) {
        send(sender, HoloMessages.BOARDS_EDIT_BUSY,
            MessageArgs.builder().untrusted("board", edit.id()).build());
        return;
      }
      if (sender instanceof Player player) {
        runtime().previewBoard(player, staged.definition(), staged.effectiveTransform());
      }
      send(sender, HoloMessages.BOARDS_EDIT_STAGED,
          MessageArgs.builder()
              .untrusted("operation", operation)
              .untrusted("board", staged.definition().id())
              .build());
    } catch (IllegalArgumentException | IllegalStateException exception) {
      invalid(sender, exception);
    }
  }

  private void persistMutation(CommandSender sender, BoardDefinition expected, String operation,
                               BoardDefinition changed) {
    service().update(expected.id(), expected.revision(), ignored -> changed)
        .whenComplete((updated, failure) -> {
          if (failure != null) {
            reportFailure(sender, expected.id(), failure);
            return;
          }
          sendLater(sender, HoloMessages.BOARDS_UPDATED,
              MessageArgs.builder()
                  .untrusted("operation", operation)
                  .untrusted("board", updated.id())
                  .untrusted("revision", updated.revision())
                  .build());
        });
  }

  private CommandBoardState commandState(CommandSender sender, String id) {
    BoardDefinition published = publishedBoard(sender, id);
    if (published == null) {
      return null;
    }
    BoardEditSession edit = matchingEdit(sender, published.id());
    if (edit != null) {
      BoardEditSession.Snapshot snapshot = edit.snapshot();
      if (snapshot.definition().follow().mode() == BoardFollowMode.PLAYER) {
        Optional<BoardFollowPose> pose = runtime().followPose(
            snapshot.definition().follow().targetPlayerUuid());
        if (pose.isEmpty()) {
          send(sender, HoloMessages.BOARDS_PLAYER_UNAVAILABLE,
              MessageArgs.builder()
                  .untrusted("player", snapshot.definition().follow().targetPlayerUuid())
                  .build());
          return null;
        }
        snapshot = edit.synchronizeEffective(BoardFollowTransform.resolve(
            snapshot.definition(), pose.get()));
      }
      return new CommandBoardState(
          snapshot.definition(), snapshot.effectiveTransform(), edit, snapshot);
    }
    BoardTransform effectiveTransform = effectiveTransform(sender, published);
    return effectiveTransform == null
        ? null
        : new CommandBoardState(published, effectiveTransform, null, null);
  }

  private BoardTransform effectiveTransform(CommandSender sender, BoardDefinition board) {
    if (board.follow().mode() == BoardFollowMode.NONE) {
      return board.transform();
    }
    Optional<BoardDefinition> effective = runtime().effectiveBoard(board.uuid());
    if (effective.isPresent()) {
      return effective.get().transform();
    }
    send(sender, HoloMessages.BOARDS_PLAYER_UNAVAILABLE,
        MessageArgs.builder().untrusted("player", board.follow().targetPlayerUuid()).build());
    return null;
  }

  private BoardDefinition visibleBoard(CommandSender sender, String id) {
    BoardDefinition published = publishedBoard(sender, id);
    if (published == null) {
      return null;
    }
    BoardEditSession edit = matchingEdit(sender, published.id());
    return edit == null ? published : edit.snapshot().definition();
  }

  private BoardDefinition publishedBoard(CommandSender sender, String id) {
    try {
      String canonicalId = BoardIds.canonicalize(id);
      BoardDefinition board = service().get(canonicalId).orElse(null);
      if (board == null) {
        send(sender, HoloMessages.BOARDS_UNKNOWN,
            MessageArgs.builder().untrusted("board", canonicalId).build());
      }
      return board;
    } catch (IllegalArgumentException exception) {
      invalid(sender, exception);
      return null;
    }
  }

  private void sendInfo(CommandSender sender, BoardDefinition board, BoardTransform effectiveTransform,
                        boolean staged) {
    BoardTransform transform = effectiveTransform;
    BoardVisibility visibility = board.visibility();
    BoardFollow follow = board.follow();
    send(sender, HoloMessages.BOARDS_INFO_HEADER,
        MessageArgs.builder()
            .untrusted("board", board.id())
            .untrusted("state", staged ? "staged" : "published")
            .build());
    send(sender, HoloMessages.BOARDS_INFO_IDENTITY,
        MessageArgs.builder()
            .untrusted("uuid", board.uuid())
            .untrusted("revision", board.revision())
            .untrusted("menu", board.rootMenuId())
            .build());
    send(sender, HoloMessages.BOARDS_INFO_TRANSFORM,
        MessageArgs.builder()
            .untrusted("world", transform.worldKey())
            .untrusted("x", format(transform.x()))
            .untrusted("y", format(transform.y()))
            .untrusted("z", format(transform.z()))
            .untrusted("yaw", format(transform.yaw()))
            .untrusted("pitch", format(transform.pitch()))
            .untrusted("roll", format(transform.roll()))
            .untrusted("scale", format(transform.scale()))
            .build());
    send(sender, HoloMessages.BOARDS_INFO_VISIBILITY,
        MessageArgs.builder()
            .untrusted("mode", visibility.mode().name().toLowerCase(Locale.ROOT))
            .untrusted("viewPermission", fallback(visibility.viewPermission()))
            .untrusted("interactPermission", fallback(visibility.interactPermission()))
            .untrusted("viewRange", format(visibility.viewRange()))
            .untrusted("interactionRange", format(visibility.interactionRange()))
            .build());
    send(sender, HoloMessages.BOARDS_INFO_FOLLOW,
        MessageArgs.builder()
            .untrusted("mode", follow.mode().name().toLowerCase(Locale.ROOT))
            .untrusted("player", follow.targetPlayerUuid() == null ? "-" : follow.targetPlayerUuid())
            .untrusted("rotation", follow.rotation().name().toLowerCase(Locale.ROOT))
            .build());
  }

  private boolean rejectIdentityMutation(CommandSender sender, String id) {
    BoardEditSession edit = matchingEdit(sender, id);
    if (edit == null) {
      return false;
    }
    send(sender, HoloMessages.BOARDS_EDIT_IDENTITY_BLOCKED,
        MessageArgs.builder().untrusted("board", edit.id()).build());
    return true;
  }

  private BoardEditSession matchingEdit(CommandSender sender, String id) {
    if (!(sender instanceof Player player)) {
      return null;
    }
    BoardEditSession edit = editSessions.get(player.getUniqueId());
    return edit != null && edit.id().equals(id) ? edit : null;
  }

  private Player followTarget(CommandSender sender, BoardDefinition board) {
    UUID targetId = board.follow().targetPlayerUuid();
    Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
    if (target != null && target.isOnline()) {
      return target;
    }
    send(sender, HoloMessages.BOARDS_PLAYER_UNAVAILABLE,
        MessageArgs.builder().untrusted("player", targetId == null ? "-" : targetId).build());
    return null;
  }

  private void captureLocation(CommandSender sender, Player target, Consumer<Location> callback) {
    Runnable capture = () -> {
      if (!target.isOnline()) {
        runForSender(sender, () -> send(sender, HoloMessages.BOARDS_PLAYER_UNAVAILABLE,
            MessageArgs.builder().untrusted("player", target.getName()).build()));
        return;
      }
      Location location = target.getLocation().clone();
      runForSender(sender, () -> callback.accept(location));
    };
    if (!SchedulerUtils.runEntity(plugin(), target, capture)) {
      send(sender, HoloMessages.BOARDS_PLAYER_UNAVAILABLE,
          MessageArgs.builder().untrusted("player", target.getName()).build());
    }
  }

  private void runForSender(CommandSender sender, Runnable task) {
    boolean accepted;
    if (sender instanceof Player player) {
      accepted = SchedulerUtils.runEntity(plugin(), player, task);
    } else {
      accepted = SchedulerUtils.runGlobal(plugin(), task);
    }
    if (!accepted) {
      HoloUI.log(Level.WARNING,
          "Unable to schedule persistent board command work for %s.", sender.getName());
    }
  }

  private void discardEdit(Player player) {
    BoardEditSession removed = editSessions.remove(player.getUniqueId());
    if (removed == null) {
      return;
    }
    HoloUI plugin = HoloUI.INSTANCE;
    if (plugin != null && plugin.getBoardRuntime() != null) {
      plugin.getBoardRuntime().clearBoardPreview(
          player, removed.snapshot().definition().uuid());
    }
  }

  private String resolveMenu(CommandSender sender, String menuId) {
    MenuDefinitionData menu = plugin().getConfigManager().get(menuId).orElse(null);
    if (menu == null) {
      send(sender, HoloMessages.BOARDS_MENU_UNAVAILABLE,
          MessageArgs.builder().untrusted("menu", menuId).build());
      return null;
    }
    return menu.getId();
  }

  private Player onlinePlayer(String playerName) {
    if (playerName == null || playerName.isBlank()) {
      return null;
    }
    Player player = Bukkit.getPlayerExact(playerName.strip());
    if (player != null) {
      return player;
    }
    try {
      return Bukkit.getPlayer(UUID.fromString(playerName.strip()));
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private Player requirePlayer(CommandSender sender) {
    if (sender instanceof Player player) {
      return player;
    }
    send(sender, HoloMessages.COMMAND_PLAYER_ONLY);
    return null;
  }

  private boolean checkPermission(CommandSender sender) {
    if (sender.hasPermission(PERMISSION)) {
      return true;
    }
    send(sender, HoloMessages.PERMISSION_DENIED,
        MessageArgs.builder().untrusted("permission", PERMISSION).build());
    return false;
  }

  private void reportFailure(CommandSender sender, String id, Throwable failure) {
    Throwable cause = BoardCommandSupport.rootCause(failure);
    if (cause instanceof BoardRevisionConflictException conflict) {
      sendLater(sender, HoloMessages.BOARDS_REVISION_CONFLICT,
          MessageArgs.builder()
              .untrusted("board", conflict.boardId())
              .untrusted("expected", conflict.expectedRevision())
              .untrusted("actual", conflict.actualRevision())
              .build());
      return;
    }
    if (cause instanceof FileAlreadyExistsException) {
      sendLater(sender, HoloMessages.BOARDS_ALREADY_EXISTS,
          MessageArgs.builder().untrusted("board", id).build());
      return;
    }
    if (cause instanceof NoSuchElementException) {
      sendLater(sender, HoloMessages.BOARDS_UNKNOWN,
          MessageArgs.builder().untrusted("board", id).build());
      return;
    }
    if (cause instanceof IllegalArgumentException) {
      sendLater(sender, HoloMessages.BOARDS_INVALID,
          MessageArgs.builder().untrusted("reason", safeReason(cause)).build());
      return;
    }
    if (!(cause instanceof CancellationException)) {
      HoloUI.logExceptionStack(true, cause, "Persistent board command failed for board \"%s\".", id);
    }
    sendLater(sender, HoloMessages.BOARDS_FAILED,
        MessageArgs.builder()
            .untrusted("board", id)
            .untrusted("reason", safeReason(cause))
            .build());
  }

  private void invalid(CommandSender sender, RuntimeException exception) {
    send(sender, HoloMessages.BOARDS_INVALID,
        MessageArgs.builder().untrusted("reason", safeReason(exception)).build());
  }

  private void sendLater(CommandSender sender, TextKey key,
                         MessageArgs arguments) {
    Runnable feedback = () -> send(sender, key, arguments);
    boolean accepted;
    if (sender instanceof Player player) {
      accepted = SchedulerUtils.runEntity(plugin(), player, feedback);
    } else {
      accepted = SchedulerUtils.runGlobal(plugin(), feedback);
    }
    if (!accepted) {
      HoloUI.log(Level.WARNING,
          "Unable to schedule persistent board command feedback for %s.", sender.getName());
    }
  }

  private void send(CommandSender sender, TextKey key) {
    send(sender, key, MessageArgs.empty());
  }

  private void send(CommandSender sender, TextKey key,
                    MessageArgs arguments) {
    sender.sendMessage(plugin().getLocalization().legacy(key, arguments));
  }

  private MessageArgs boardSummary(BoardDefinition board) {
    return MessageArgs.builder()
        .untrusted("board", board.id())
        .untrusted("menu", board.rootMenuId())
        .untrusted("revision", board.revision())
        .build();
  }

  static PageWindow pageWindow(int itemCount, int page) {
    int pages = pageCount(itemCount);
    if (page < 1 || page > pages) {
      throw new IllegalArgumentException("page must be between 1 and " + pages);
    }
    int startIndex = (page - 1) * LIST_PAGE_SIZE;
    int endIndex = Math.min(startIndex + LIST_PAGE_SIZE, itemCount);
    return new PageWindow(page, pages, startIndex, endIndex);
  }

  static int pageCount(int itemCount) {
    if (itemCount < 0) {
      throw new IllegalArgumentException("item count must not be negative");
    }
    return itemCount == 0 ? 1 : ((itemCount - 1) / LIST_PAGE_SIZE) + 1;
  }

  private BoardService service() {
    return plugin().getBoardService();
  }

  private BoardRuntimeManager runtime() {
    return plugin().getBoardRuntime();
  }

  private HoloUI plugin() {
    return HoloUI.INSTANCE;
  }

  private static String fallback(String value) {
    return value == null ? "-" : value;
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.3f", value);
  }

  private static String safeReason(Throwable failure) {
    String message = failure == null ? null : failure.getMessage();
    return message == null || message.isBlank()
        ? failure == null ? "unknown failure" : failure.getClass().getSimpleName()
        : message;
  }

  private record CommandBoardState(BoardDefinition definition, BoardTransform effectiveTransform,
                                   BoardEditSession edit,
                                   BoardEditSession.Snapshot editSnapshot) {
  }

  record PageWindow(int page, int pages, int startIndex, int endIndex) {
  }

  public static final class BoardIdHandler implements DirectorParameterHandler<String> {
    @Override
    public KList<String> getPossibilities() {
      KList<String> ids = new KList<>();
      HoloUI plugin = HoloUI.INSTANCE;
      if (plugin == null || plugin.getBoardService() == null) {
        return ids;
      }
      for (BoardDefinition board : plugin.getBoardService().list()) {
        ids.add(board.id());
      }
      return ids;
    }

    @Override
    public String toString(String value) {
      return value == null ? "" : value;
    }

    @Override
    public String parse(String in, boolean force) throws DirectorParsingException {
      if (in == null || in.isBlank()) {
        throw new DirectorParsingException(HoloLocalization.globalText(HoloMessages.ERROR_BOARD_ID_REQUIRED));
      }
      String value = in.strip();
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

  public static final class AxesHandler implements DirectorParameterHandler<String> {
    @Override
    public KList<String> getPossibilities() {
      return new KList<>(List.of("x", "y", "z", "xy", "xz", "yz", "xyz"));
    }

    @Override
    public String toString(String value) {
      return value == null ? "" : value;
    }

    @Override
    public String parse(String in, boolean force) throws DirectorParsingException {
      try {
        return BoardCommandSupport.axes(in);
      } catch (IllegalArgumentException exception) {
        throw new DirectorParsingException(exception.getMessage());
      }
    }

    @Override
    public boolean supports(Class<?> type) {
      return type == String.class;
    }
  }

  public static final class OnlinePlayerHandler implements DirectorParameterHandler<String> {
    @Override
    public KList<String> getPossibilities() {
      KList<String> players = new KList<>();
      for (Player player : Bukkit.getOnlinePlayers()) {
        players.add(player.getName());
      }
      return players;
    }

    @Override
    public String toString(String value) {
      return value == null ? "" : value;
    }

    @Override
    public String parse(String in, boolean force) throws DirectorParsingException {
      if (in == null || in.isBlank()) {
        throw new DirectorParsingException(HoloLocalization.globalText(HoloMessages.ERROR_BOARD_PLAYER_REQUIRED));
      }
      return in.strip();
    }

    @Override
    public boolean supports(Class<?> type) {
      return type == String.class;
    }
  }
}
