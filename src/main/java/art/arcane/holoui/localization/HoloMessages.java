package art.arcane.holoui.localization;

import art.arcane.volmlib.util.director.DirectorMessages;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.VolmitLocales;

import java.util.List;

public final class HoloMessages {
  public static final TextKey COMMAND_ROOT = TextKey.of("holoui.command.root", "HoloUI command root");
  public static final TextKey COMMAND_LIST = TextKey.of("holoui.command.list", "List all configured menus you can open");
  public static final TextKey COMMAND_OPEN = TextKey.of("holoui.command.open", "Open a menu by id, or show the menu list when set to *");
  public static final TextKey COMMAND_BACK = TextKey.of("holoui.command.back", "Reopen your previous menu session");
  public static final TextKey COMMAND_CLOSE = TextKey.of("holoui.command.close", "Close your currently open menu session");
  public static final TextKey COMMAND_MOVE = TextKey.of("holoui.command.move", "Move your open menu to your current position");
  public static final TextKey COMMAND_BUILDER = TextKey.of("holoui.command.builder", "Link to the hosted HoloUI web editor");
  public static final TextKey COMMAND_EDIT = TextKey.of("holoui.command.edit", "Open a loaded menu in the web editor");
  public static final TextKey COMMAND_SYNC = TextKey.of("holoui.command.sync.root", "Manage active web editor sync sessions");
  public static final TextKey COMMAND_SYNC_LIST = TextKey.of("holoui.command.sync.list", "List active editor sync sessions");
  public static final TextKey COMMAND_SYNC_STATUS = TextKey.of("holoui.command.sync.status", "Show one editor sync session");
  public static final TextKey COMMAND_SYNC_REVOKE = TextKey.of("holoui.command.sync.revoke", "Revoke an editor sync capability");
  public static final TextKey COMMAND_SYNC_PULL = TextKey.of("holoui.command.sync.pull", "Poll an editor sync session now");
  public static final TextKey COMMAND_ITEMS = TextKey.of("holoui.command.items.root", "Custom item provider tools");
  public static final TextKey COMMAND_ITEMS_STATUS = TextKey.of("holoui.command.items.status", "Show which custom item providers are active");
  public static final TextKey COMMAND_ITEMS_EXPORT = TextKey.of("holoui.command.items.export", "Export the custom item catalog for the web editor");
  public static final TextKey COMMAND_PREVIEWS = TextKey.of("holoui.command.previews.root", "Preview document tools");
  public static final TextKey COMMAND_PREVIEWS_LIST = TextKey.of("holoui.command.previews.list", "List preview documents and their match rules");
  public static final TextKey COMMAND_PREVIEWS_RESET = TextKey.of("holoui.command.previews.reset", "Restore shipped preview defaults (does not remove extra user documents that may shadow them)");
  public static final TextKey COMMAND_PREVIEWS_DUMP = TextKey.of("holoui.command.previews.dump", "Build a preview document once and print its element counts");
  public static final TextKey COMMAND_MENUS = TextKey.of("holoui.command.menus.root", "Persistently edit loaded menu content");
  public static final TextKey COMMAND_MENUS_ADDROW = TextKey.of("holoui.command.menus.addrow", "Append a text decoration row");
  public static final TextKey COMMAND_MENUS_INSERTROW = TextKey.of("holoui.command.menus.insertrow", "Insert a text decoration row");
  public static final TextKey COMMAND_MENUS_SETROW = TextKey.of("holoui.command.menus.setrow", "Set a button or decoration row's text");
  public static final TextKey COMMAND_MENUS_REMOVEROW = TextKey.of("holoui.command.menus.removerow", "Remove a component row");
  public static final TextKey COMMAND_MENUS_OFFSETROW = TextKey.of("holoui.command.menus.offsetrow", "Move a row with absolute or ~relative offsets");
  public static final TextKey COMMAND_MENUS_SETICON = TextKey.of("holoui.command.menus.seticon", "Replace a button or decoration row icon");
  public static final TextKey COMMAND_MENUS_STYLE = TextKey.of("holoui.command.menus.style", "Set or clear one row display-style property");
  public static final TextKey COMMAND_MENUS_IMAGE = TextKey.of("holoui.command.menus.image", "Replace menu content with one centered image file");
  public static final TextKey COMMAND_MENUS_COPY = TextKey.of("holoui.command.menus.copy", "Copy a loaded menu to a new nested id");
  public static final TextKey COMMAND_BOARDS = TextKey.of("holoui.command.boards.root", "Persistent world board tools");
  public static final TextKey COMMAND_BOARDS_LIST = TextKey.of("holoui.command.boards.list", "List persistent boards by page");
  public static final TextKey COMMAND_BOARDS_RELOAD = TextKey.of("holoui.command.boards.reload", "Reload persistent board files");
  public static final TextKey COMMAND_BOARDS_NEAR = TextKey.of("holoui.command.boards.near", "List boards near your current position");
  public static final TextKey COMMAND_BOARDS_INFO = TextKey.of("holoui.command.boards.info", "Show one board's complete state");
  public static final TextKey COMMAND_BOARDS_CREATE = TextKey.of("holoui.command.boards.create", "Create a board at your current position");
  public static final TextKey COMMAND_BOARDS_DELETE = TextKey.of("holoui.command.boards.delete", "Delete a persistent board");
  public static final TextKey COMMAND_BOARDS_RENAME = TextKey.of("holoui.command.boards.rename", "Rename a persistent board");
  public static final TextKey COMMAND_BOARDS_COPY = TextKey.of("holoui.command.boards.copy", "Copy a persistent board under a new id");
  public static final TextKey COMMAND_BOARDS_MOVE = TextKey.of("holoui.command.boards.move", "Move a board to absolute or ~relative coordinates");
  public static final TextKey COMMAND_BOARDS_MOVEHERE = TextKey.of("holoui.command.boards.movehere", "Move a board to your current position");
  public static final TextKey COMMAND_BOARDS_TP = TextKey.of("holoui.command.boards.tp", "Teleport to a persistent board");
  public static final TextKey COMMAND_BOARDS_ROTATE = TextKey.of("holoui.command.boards.rotate", "Set absolute or ~relative yaw, pitch, and roll");
  public static final TextKey COMMAND_BOARDS_SCALE = TextKey.of("holoui.command.boards.scale", "Set an absolute or ~relative board scale");
  public static final TextKey COMMAND_BOARDS_ALIGN = TextKey.of("holoui.command.boards.align", "Align board position axes to another board");
  public static final TextKey COMMAND_BOARDS_MENU = TextKey.of("holoui.command.boards.menu", "Set a board's root menu");
  public static final TextKey COMMAND_BOARDS_ADDROW = TextKey.of("holoui.command.boards.addrow", "Append a text row to a board's root menu");
  public static final TextKey COMMAND_BOARDS_INSERTROW = TextKey.of("holoui.command.boards.insertrow", "Insert a text row into a board's root menu");
  public static final TextKey COMMAND_BOARDS_SETROW = TextKey.of("holoui.command.boards.setrow", "Set text on a board root-menu row");
  public static final TextKey COMMAND_BOARDS_REMOVEROW = TextKey.of("holoui.command.boards.removerow", "Remove a board root-menu row");
  public static final TextKey COMMAND_BOARDS_OFFSETROW = TextKey.of("holoui.command.boards.offsetrow", "Move a board root-menu row with absolute or ~relative offsets");
  public static final TextKey COMMAND_BOARDS_SETICON = TextKey.of("holoui.command.boards.seticon", "Replace a row icon in the board root menu");
  public static final TextKey COMMAND_BOARDS_STYLE = TextKey.of("holoui.command.boards.style", "Set or clear one board row display-style property");
  public static final TextKey COMMAND_BOARDS_IMAGE = TextKey.of("holoui.command.boards.image", "Replace the board root menu with one centered image file");
  public static final TextKey COMMAND_BOARDS_RANGES = TextKey.of("holoui.command.boards.ranges", "Set board view and interaction ranges");
  public static final TextKey COMMAND_BOARDS_VISIBILITY = TextKey.of("holoui.command.boards.visibility", "Set visibility mode and permission nodes");
  public static final TextKey COMMAND_BOARDS_PERMISSIONS = TextKey.of("holoui.command.boards.permissions", "Change board view and interaction permissions");
  public static final TextKey COMMAND_BOARDS_FOLLOW = TextKey.of("holoui.command.boards.follow", "Follow an online player with fixed, yaw, or full rotation");
  public static final TextKey COMMAND_BOARDS_UNFOLLOW = TextKey.of("holoui.command.boards.unfollow", "Stop a board following a player");
  public static final TextKey COMMAND_BOARDS_EDIT = TextKey.of("holoui.command.boards.edit", "Start a staged board edit session");
  public static final TextKey COMMAND_BOARDS_EDITWEB = TextKey.of("holoui.command.boards.editweb", "Open a board project in the web editor");
  public static final TextKey COMMAND_BOARDS_SAVE = TextKey.of("holoui.command.boards.save", "Persist your staged board edit");
  public static final TextKey COMMAND_BOARDS_CANCEL = TextKey.of("holoui.command.boards.cancel", "Discard your staged board edit");
  public static final TextKey COMMAND_IMPORT = TextKey.of("holoui.command.import.root", "Migrate legacy holograms without modifying source files");
  public static final TextKey COMMAND_IMPORT_PREVIEW = TextKey.of("holoui.command.import.preview", "Preview a non-destructive legacy hologram migration");
  public static final TextKey COMMAND_IMPORT_APPLY = TextKey.of("holoui.command.import.apply", "Apply a no-overwrite legacy hologram migration");
  public static final TextKey PARAMETER_SENDER = TextKey.of("holoui.parameter.sender", "Command sender context");
  public static final TextKey PARAMETER_MENU = TextKey.of("holoui.parameter.menu", "Menu id to open (* shows all menus)");
  public static final TextKey PARAMETER_EDIT_MENU = TextKey.of("holoui.parameter.edit_menu", "Loaded menu id");
  public static final TextKey PARAMETER_SYNC_SESSION = TextKey.of("holoui.parameter.sync_session", "Editor sync session id");
  public static final TextKey PARAMETER_PREVIEWS_NAME = TextKey.of("holoui.parameter.previews_name", "Preview document name");
  public static final TextKey PARAMETER_CONTENT_MENU = TextKey.of("holoui.parameter.content_menu", "Loaded menu id");
  public static final TextKey PARAMETER_NEW_MENU = TextKey.of("holoui.parameter.new_menu", "New nested menu id");
  public static final TextKey PARAMETER_ROW_INDEX = TextKey.of("holoui.parameter.row_index", "One-based component row");
  public static final TextKey PARAMETER_ROW_TEXT = TextKey.of("holoui.parameter.row_text", "MiniMessage row text");
  public static final TextKey PARAMETER_ROW_X = TextKey.of("holoui.parameter.row_x", "Absolute or ~relative row X");
  public static final TextKey PARAMETER_ROW_Y = TextKey.of("holoui.parameter.row_y", "Absolute or ~relative row Y");
  public static final TextKey PARAMETER_ROW_Z = TextKey.of("holoui.parameter.row_z", "Absolute or ~relative row Z");
  public static final TextKey PARAMETER_ROW_ICON_TYPE = TextKey.of("holoui.parameter.row_icon_type", "Icon type");
  public static final TextKey PARAMETER_ROW_ICON_VALUE = TextKey.of("holoui.parameter.row_icon_value", "Icon content or registry id");
  public static final TextKey PARAMETER_ROW_STYLE_PROPERTY = TextKey.of("holoui.parameter.row_style_property", "Display-style property");
  public static final TextKey PARAMETER_ROW_STYLE_VALUE = TextKey.of("holoui.parameter.row_style_value", "Style value, or * to clear");
  public static final TextKey PARAMETER_IMAGE_PATH = TextKey.of("holoui.parameter.image_path", "File beneath the HoloUI images folder");
  public static final TextKey PARAMETER_PAGE = TextKey.of("holoui.parameter.page", "One-based board list page");
  public static final TextKey PARAMETER_BOARD_ID = TextKey.of("holoui.parameter.board_id", "Persistent board id");
  public static final TextKey PARAMETER_BOARD_RADIUS = TextKey.of("holoui.parameter.board_radius", "Horizontal search radius");
  public static final TextKey PARAMETER_BOARD_MENU = TextKey.of("holoui.parameter.board_menu", "Root menu id");
  public static final TextKey PARAMETER_BOARD_NEW_ID = TextKey.of("holoui.parameter.board_new_id", "New persistent board id");
  public static final TextKey PARAMETER_BOARD_X = TextKey.of("holoui.parameter.board_x", "Absolute or ~relative X");
  public static final TextKey PARAMETER_BOARD_Y = TextKey.of("holoui.parameter.board_y", "Absolute or ~relative Y");
  public static final TextKey PARAMETER_BOARD_Z = TextKey.of("holoui.parameter.board_z", "Absolute or ~relative Z");
  public static final TextKey PARAMETER_BOARD_YAW = TextKey.of("holoui.parameter.board_yaw", "Absolute or ~relative yaw");
  public static final TextKey PARAMETER_BOARD_PITCH = TextKey.of("holoui.parameter.board_pitch", "Absolute or ~relative pitch");
  public static final TextKey PARAMETER_BOARD_ROLL = TextKey.of("holoui.parameter.board_roll", "Absolute or ~relative roll");
  public static final TextKey PARAMETER_BOARD_SCALE = TextKey.of("holoui.parameter.board_scale", "Absolute or ~relative scale");
  public static final TextKey PARAMETER_BOARD_REFERENCE = TextKey.of("holoui.parameter.board_reference", "Reference board id");
  public static final TextKey PARAMETER_BOARD_AXES = TextKey.of("holoui.parameter.board_axes", "Axes: x, y, z, xy, xz, yz, or xyz");
  public static final TextKey PARAMETER_BOARD_VIEW_RANGE = TextKey.of("holoui.parameter.board_view_range", "Positive view range");
  public static final TextKey PARAMETER_BOARD_INTERACTION_RANGE = TextKey.of("holoui.parameter.board_interaction_range", "Positive interaction range");
  public static final TextKey PARAMETER_BOARD_VISIBILITY = TextKey.of("holoui.parameter.board_visibility", "Visibility mode: public, permission, or hidden");
  public static final TextKey PARAMETER_BOARD_VIEW_PERMISSION = TextKey.of("holoui.parameter.board_view_permission", "View permission, or -");
  public static final TextKey PARAMETER_BOARD_INTERACT_PERMISSION = TextKey.of("holoui.parameter.board_interact_permission", "Interaction permission, or -");
  public static final TextKey PARAMETER_BOARD_PLAYER = TextKey.of("holoui.parameter.board_player", "Online target player");
  public static final TextKey PARAMETER_BOARD_FOLLOW_ROTATION = TextKey.of("holoui.parameter.board_follow_rotation", "Follow rotation: fixed, yaw, or full");
  public static final TextKey PARAMETER_IMPORT_SOURCE = TextKey.of("holoui.parameter.import_source", "Legacy source: gholo, decent-holograms, holographic-displays, or fancy-holograms");
  public static final TextKey ERROR_MENU_NAME_REQUIRED = TextKey.of("holoui.error.menu_name_required", "Menu name cannot be empty");
  public static final TextKey ERROR_SYNC_SESSION_REQUIRED = TextKey.of("holoui.error.sync_session_required", "Editor sync session id cannot be empty");
  public static final TextKey ERROR_BOARD_ID_REQUIRED = TextKey.of("holoui.error.board_id_required", "Board id cannot be empty");
  public static final TextKey ERROR_BOARD_PLAYER_REQUIRED = TextKey.of("holoui.error.board_player_required", "Target player cannot be empty");
  public static final TextKey ERROR_ROW_ICON_TYPE_REQUIRED = TextKey.of("holoui.error.row_icon_type_required", "Icon type cannot be empty");
  public static final TextKey ERROR_ROW_STYLE_PROPERTY_REQUIRED = TextKey.of("holoui.error.row_style_property_required", "Style property cannot be empty");
  public static final TextKey ERROR_IMPORT_SOURCE_REQUIRED = TextKey.of("holoui.error.import_source_required", "Legacy import source cannot be empty");
  public static final TextKey ERROR_IMPORT_SOURCE_UNKNOWN = TextKey.of("holoui.error.import_source_unknown", "Unknown legacy import source: {source}");
  public static final TextKey PERMISSION_DENIED = TextKey.of("holoui.message.permission_denied", "&7[&bHoloUI&7]: &cYou lack permission &f{permission}&c.");
  public static final TextKey UNKNOWN_COMMAND = TextKey.of("holoui.message.unknown_command", "&7[&bHoloUI&7]: &cUnknown command \"&f{command}&c\".");
  public static final TextKey NO_MENUS = TextKey.of("holoui.message.menu.none", "&7[&bHoloUI&7]: &7No menus are available.");
  public static final TextKey MENU_LIST_HEADER = TextKey.of("holoui.message.menu.list.header", "Menus");
  public static final TextKey MENU_LIST_ENTRY = TextKey.of("holoui.message.menu.list.entry", "Click to open {menu}.");
  public static final TextKey MENUS_PLAYER_ONLY = TextKey.of("holoui.message.menu.player_only", "&7[&bHoloUI&7]: &cMenus can only be opened by players.");
  public static final TextKey COMMAND_PLAYER_ONLY = TextKey.of("holoui.message.command.player_only", "&7[&bHoloUI&7]: &cThis command is only available to players.");
  public static final TextKey NO_PREVIOUS_MENU = TextKey.of("holoui.message.menu.no_previous", "&7[&bHoloUI&7]: &cNo previous menu is available.");
  public static final TextKey MENU_CLOSED = TextKey.of("holoui.message.menu.closed", "&7[&bHoloUI&7]: &aMenu closed.");
  public static final TextKey MENU_MOVED = TextKey.of("holoui.message.menu.moved", "&7[&bHoloUI&7]: &aMenu moved.");
  public static final TextKey NO_OPEN_MENU = TextKey.of("holoui.message.menu.none_open", "&7[&bHoloUI&7]: &cNo menu is currently open.");
  public static final TextKey MENU_UNAVAILABLE = TextKey.of("holoui.message.menu.unavailable", "&7[&bHoloUI&7]: &c\"{menu}\" is not available.");
  public static final TextKey MENU_PERMISSION_DENIED = TextKey.of("holoui.message.menu.permission_denied", "&7[&bHoloUI&7]: &cYou lack permission to open \"{menu}\".");
  public static final TextKey MENU_OPEN_FAILED = TextKey.of("holoui.message.menu.open_failed", "&7[&bHoloUI&7]: &cFailed to open menu \"{menu}\".");
  public static final TextKey MENU_CONTENT_UPDATED = TextKey.of("holoui.message.menu.content.updated", "&7[&bHoloUI&7]: &aApplied {operation} to menu &f{menu}&a at revision {revision}.");
  public static final TextKey MENU_CONTENT_COPIED = TextKey.of("holoui.message.menu.content.copied", "&7[&bHoloUI&7]: &aCopied menu &f{source}&a to &f{menu}&a at revision {revision}.");
  public static final TextKey MENU_CONTENT_REVISION_CONFLICT = TextKey.of("holoui.message.menu.content.revision_conflict", "&7[&bHoloUI&7]: &cMenu &f{menu}&c changed during the write (expected {expected}, current {actual}). Retry the command.");
  public static final TextKey MENU_CONTENT_ALREADY_EXISTS = TextKey.of("holoui.message.menu.content.already_exists", "&7[&bHoloUI&7]: &cMenu &f{menu}&c already exists.");
  public static final TextKey MENU_CONTENT_INVALID = TextKey.of("holoui.message.menu.content.invalid", "&7[&bHoloUI&7]: &cInvalid menu content value: &f{reason}&c.");
  public static final TextKey MENU_CONTENT_FAILED = TextKey.of("holoui.message.menu.content.failed", "&7[&bHoloUI&7]: &cMenu content operation failed for &f{menu}&c: {reason}");
  public static final TextKey BUILDER_HEADER = TextKey.of("holoui.message.builder.header", "Web Editor");
  public static final TextKey BUILDER_LINK_HOVER = TextKey.of("holoui.message.builder.hover", "Click to open the HoloUI web editor.");
  public static final TextKey BUILDER_OPEN = TextKey.of("holoui.message.builder.open", "&7[&bHoloUI&7]: &7Web editor: &f{url}");
  public static final TextKey EDITOR_MENU_OPEN = TextKey.of("holoui.message.editor_menu.open", "&7[&bHoloUI&7]: &7Open &f{menu}&7 in the editor: &f{url}");
  public static final TextKey EDITOR_MENU_LINK = TextKey.of("holoui.message.editor_menu.link", "Open {menu} in editor");
  public static final TextKey EDITOR_MENU_HOVER = TextKey.of("holoui.message.editor_menu.hover", "Open a confirmation-first copy of {menu} in the web editor.");
  public static final TextKey EDITOR_MENU_TOO_LARGE = TextKey.of("holoui.message.editor_menu.too_large", "&7[&bHoloUI&7]: &cMenu &f{menu}&c is too large for a secure editor link. Export it as a file instead.");
  public static final TextKey EDITOR_MENU_FAILED = TextKey.of("holoui.message.editor_menu.failed", "&7[&bHoloUI&7]: &cCould not prepare menu &f{menu}&c for the web editor. Check the console log.");
  public static final TextKey SYNC_PREPARING = TextKey.of("holoui.message.sync.preparing", "&7[&bHoloUI&7]: &7Preparing a secure editor sync session for &f{subject}&7...");
  public static final TextKey SYNC_FALLBACK = TextKey.of("holoui.message.sync.fallback", "&7[&bHoloUI&7]: &eLive sync is unavailable for &f{subject}&e. Opening a one-way editor copy instead; its saves do not return to this server.");
  public static final TextKey SYNC_OPEN_CONSOLE = TextKey.of("holoui.message.sync.open_console", "&7[&bHoloUI&7]: &7Editor capability for &f{subject}&7: &f{url}");
  public static final TextKey SYNC_CAPABILITY_WARNING = TextKey.of("holoui.message.sync.capability_warning", "&7[&bHoloUI&7]: &eThis URL can publish changes to the server until revoked or expired. Treat it as a secret.");
  public static final TextKey SYNC_OPEN_LABEL = TextKey.of("holoui.message.sync.open_label", "Open Editor");
  public static final TextKey SYNC_COPY_LABEL = TextKey.of("holoui.message.sync.copy_label", "Copy Link");
  public static final TextKey SYNC_REVOKE_LABEL = TextKey.of("holoui.message.sync.revoke_label", "Revoke");
  public static final TextKey SYNC_LINK_HOVER = TextKey.of("holoui.message.sync.link_hover", "Edit {subject}, copy its capability link, or revoke session {session}.");
  public static final TextKey SYNC_LIST_HEADER = TextKey.of("holoui.message.sync.list.header", "&7[&bHoloUI&7]: &fActive editor sync sessions: &b{count}");
  public static final TextKey SYNC_LIST_EMPTY = TextKey.of("holoui.message.sync.list.empty", "&7[&bHoloUI&7]: &7No editor sync sessions are active.");
  public static final TextKey SYNC_LIST_ENTRY = TextKey.of("holoui.message.sync.list.entry", "&8- &f{session} &7{kind}=&f{subject} &7expires=&f{seconds}s &7publication=&f{revision} &7pending=&f{pending}");
  public static final TextKey SYNC_STATUS = TextKey.of("holoui.message.sync.status", "&7[&bHoloUI&7]: &f{session} &7{kind}=&f{subject} &7expires=&f{seconds}s &7publication=&f{revision} &7pending=&f{pending}");
  public static final TextKey SYNC_REVOKED = TextKey.of("holoui.message.sync.revoked", "&7[&bHoloUI&7]: &aRevoked editor sync session &f{session}&a.");
  public static final TextKey SYNC_PULLED = TextKey.of("holoui.message.sync.pulled", "&7[&bHoloUI&7]: &aPolled editor sync session &f{session}&a.");
  public static final TextKey SYNC_UNKNOWN = TextKey.of("holoui.message.sync.unknown", "&7[&bHoloUI&7]: &cUnknown or expired editor sync session &f{session}&c.");
  public static final TextKey SYNC_FAILED = TextKey.of("holoui.message.sync.failed", "&7[&bHoloUI&7]: &cEditor sync operation failed for &f{session}&c: {reason}");
  public static final TextKey ITEMS_DISABLED = TextKey.of("holoui.message.items.disabled", "&7[&bHoloUI&7]: &7Custom items are disabled. Set &fcustomItems&7 to true in settings.json.");
  public static final TextKey ITEMS_STATUS_HEADER = TextKey.of("holoui.message.items.status.header", "Custom Items");
  public static final TextKey ITEMS_STATUS_SUMMARY = TextKey.of("holoui.message.items.status.summary", "{active}/{total} providers active");
  public static final TextKey ITEMS_STATUS_ENTRY = TextKey.of("holoui.message.items.status.entry", "Provider {provider} from plugin {plugin}.");
  public static final TextKey ITEMS_STATUS_HINT = TextKey.of("holoui.message.items.status.hint", "Click to export the catalog for the web editor.");
  public static final TextKey ITEMS_STATE_READY = TextKey.of("holoui.message.items.state.ready", "ready, {count} ids");
  public static final TextKey ITEMS_STATE_LOADING = TextKey.of("holoui.message.items.state.loading", "present, still loading");
  public static final TextKey ITEMS_STATE_INACTIVE = TextKey.of("holoui.message.items.state.inactive", "present, no adapter");
  public static final TextKey ITEMS_STATE_MISSING = TextKey.of("holoui.message.items.state.missing", "not installed");
  public static final TextKey ITEMS_EXPORT_STARTED = TextKey.of("holoui.message.items.export.started", "&7[&bHoloUI&7]: &7Exporting the custom item catalog...");
  public static final TextKey ITEMS_EXPORT_DONE = TextKey.of("holoui.message.items.export.done", "&7[&bHoloUI&7]: &aWrote {count} items from {providers} providers to &f{path}&a.");
  public static final TextKey ITEMS_EXPORT_EMPTY = TextKey.of("holoui.message.items.export.empty", "&7[&bHoloUI&7]: &7No custom items were found. An empty catalog was written to &f{path}&7.");
  public static final TextKey ITEMS_EXPORT_FAILED = TextKey.of("holoui.message.items.export.failed", "&7[&bHoloUI&7]: &cFailed to write the custom item catalog. Check the logs.");
  public static final TextKey ITEMS_EXPORT_BUSY = TextKey.of("holoui.message.items.export.busy", "&7[&bHoloUI&7]: &cA catalog export is already running.");
  public static final TextKey PREVIEWS_LIST_HEADER = TextKey.of("holoui.message.previews.list.header", "Preview Documents");
  public static final TextKey PREVIEWS_LIST_EMPTY = TextKey.of("holoui.message.previews.list.empty", "&7No preview documents are loaded.");
  public static final TextKey PREVIEWS_LIST_ENTRY = TextKey.of("holoui.message.previews.list.entry", "blocks={blocks} entities={entities} special={special} priority={priority}");
  public static final TextKey PREVIEWS_RESET_STARTED = TextKey.of("holoui.message.previews.reset.started", "&7[&bHoloUI&7]: &7Resetting preview document(s) \"&f{name}&7\"...");
  public static final TextKey PREVIEWS_RESET_DONE = TextKey.of("holoui.message.previews.reset.done", "&7[&bHoloUI&7]: &aRestored {count} shipped preview document(s). Extra user documents that shadow them were not removed.");
  public static final TextKey PREVIEWS_RESET_NONE = TextKey.of("holoui.message.previews.reset.none", "&7[&bHoloUI&7]: &c\"{name}\" is not a shipped preview document; nothing was reset.");
  public static final TextKey PREVIEWS_DUMP_UNKNOWN = TextKey.of("holoui.message.previews.dump.unknown", "&7[&bHoloUI&7]: &c\"{name}\" is not a loaded preview document.");
  public static final TextKey PREVIEWS_DUMP_RESULT = TextKey.of("holoui.message.previews.dump.result", "&7[&bHoloUI&7]: &a{name}&7: &f{total}&7 elements (panels={panels}, cells={cells}, slots={slots}, labels={labels}).");
  public static final TextKey PREVIEWS_DUMP_NO_ERRORS = TextKey.of("holoui.message.previews.dump.no_errors", "&7[&bHoloUI&7]: &aNo build errors.");
  public static final TextKey PREVIEWS_DUMP_ERROR_LINE = TextKey.of("holoui.message.previews.dump.error_line", "&7[&bHoloUI&7]: &c{message}");
  public static final TextKey PREVIEWS_DUMP_ERROR_MORE = TextKey.of("holoui.message.previews.dump.error_more", "&7[&bHoloUI&7]: &7+{count} more (see console log).");
  public static final TextKey BOARDS_LIST_HEADER = TextKey.of("holoui.message.boards.list.header", "&7[&bHoloUI&7]: &fPersistent boards: &b{count}");
  public static final TextKey BOARDS_LIST_EMPTY = TextKey.of("holoui.message.boards.list.empty", "&7[&bHoloUI&7]: &7No persistent boards are loaded.");
  public static final TextKey BOARDS_LIST_ENTRY = TextKey.of("holoui.message.boards.list.entry", "&8- &f{board} &7menu=&f{menu} &7revision=&f{revision}");
  public static final TextKey BOARDS_LIST_PAGE = TextKey.of("holoui.message.boards.list.page", "&7Page &f{page}&7/&f{pages} &8- &7showing &f{from}-{to}");
  public static final TextKey BOARDS_LIST_PAGE_INVALID = TextKey.of("holoui.message.boards.list.page_invalid", "&7[&bHoloUI&7]: &cPage &f{page}&c is outside the available range &f1-{pages}&c.");
  public static final TextKey BOARDS_RELOADED = TextKey.of("holoui.message.boards.reloaded", "&7[&bHoloUI&7]: &aReloaded boards: {loaded} loaded, {retained} retained, {removed} removed, {failures} failed.");
  public static final TextKey BOARDS_NEAR_HEADER = TextKey.of("holoui.message.boards.near.header", "&7[&bHoloUI&7]: &fBoards within &b{radius}&f blocks: &b{count}");
  public static final TextKey BOARDS_NEAR_EMPTY = TextKey.of("holoui.message.boards.near.empty", "&7[&bHoloUI&7]: &7No boards are within that radius.");
  public static final TextKey BOARDS_NEAR_ENTRY = TextKey.of("holoui.message.boards.near.entry", "&8- &f{board} &7distance=&f{distance} &7menu=&f{menu}");
  public static final TextKey BOARDS_INFO_HEADER = TextKey.of("holoui.message.boards.info.header", "&7[&bHoloUI&7]: &fBoard &b{board} &7({state})");
  public static final TextKey BOARDS_INFO_IDENTITY = TextKey.of("holoui.message.boards.info.identity", "&7uuid=&f{uuid} &7revision=&f{revision} &7menu=&f{menu}");
  public static final TextKey BOARDS_INFO_TRANSFORM = TextKey.of("holoui.message.boards.info.transform", "&7world=&f{world} &7position=&f{x}, {y}, {z} &7rotation=&f{yaw}, {pitch}, {roll} &7scale=&f{scale}");
  public static final TextKey BOARDS_INFO_VISIBILITY = TextKey.of("holoui.message.boards.info.visibility", "&7visibility=&f{mode} &7viewPermission=&f{viewPermission} &7interactPermission=&f{interactPermission} &7ranges=&f{viewRange}/{interactionRange}");
  public static final TextKey BOARDS_INFO_FOLLOW = TextKey.of("holoui.message.boards.info.follow", "&7follow=&f{mode} &7player=&f{player} &7rotation=&f{rotation}");
  public static final TextKey BOARDS_CREATED = TextKey.of("holoui.message.boards.created", "&7[&bHoloUI&7]: &aCreated board &f{board}&a at revision {revision}.");
  public static final TextKey BOARDS_DELETED = TextKey.of("holoui.message.boards.deleted", "&7[&bHoloUI&7]: &aDeleted board &f{board}&a.");
  public static final TextKey BOARDS_RENAMED = TextKey.of("holoui.message.boards.renamed", "&7[&bHoloUI&7]: &aRenamed &f{oldBoard}&a to &f{board}&a at revision {revision}.");
  public static final TextKey BOARDS_COPIED = TextKey.of("holoui.message.boards.copied", "&7[&bHoloUI&7]: &aCopied &f{source}&a to &f{board}&a.");
  public static final TextKey BOARDS_UPDATED = TextKey.of("holoui.message.boards.updated", "&7[&bHoloUI&7]: &aApplied {operation} to &f{board}&a at revision {revision}.");
  public static final TextKey BOARDS_TELEPORTED = TextKey.of("holoui.message.boards.teleported", "&7[&bHoloUI&7]: &aTeleported to &f{board}&a.");
  public static final TextKey BOARDS_UNKNOWN = TextKey.of("holoui.message.boards.unknown", "&7[&bHoloUI&7]: &cUnknown board &f{board}&c.");
  public static final TextKey BOARDS_ALREADY_EXISTS = TextKey.of("holoui.message.boards.already_exists", "&7[&bHoloUI&7]: &cBoard &f{board}&c already exists.");
  public static final TextKey BOARDS_INVALID = TextKey.of("holoui.message.boards.invalid", "&7[&bHoloUI&7]: &cInvalid board value: &f{reason}&c.");
  public static final TextKey BOARDS_MENU_UNAVAILABLE = TextKey.of("holoui.message.boards.menu_unavailable", "&7[&bHoloUI&7]: &cRoot menu &f{menu}&c is not loaded.");
  public static final TextKey BOARDS_PLAYER_UNAVAILABLE = TextKey.of("holoui.message.boards.player_unavailable", "&7[&bHoloUI&7]: &cTarget player &f{player}&c is not online.");
  public static final TextKey BOARDS_WORLD_UNAVAILABLE = TextKey.of("holoui.message.boards.world_unavailable", "&7[&bHoloUI&7]: &cBoard world &f{world}&c is not loaded with the stored UUID.");
  public static final TextKey BOARDS_REVISION_CONFLICT = TextKey.of("holoui.message.boards.revision_conflict", "&7[&bHoloUI&7]: &cBoard &f{board}&c changed while you edited it (expected {expected}, current {actual}). Reopen the edit session.");
  public static final TextKey BOARDS_FAILED = TextKey.of("holoui.message.boards.failed", "&7[&bHoloUI&7]: &cBoard operation failed for &f{board}&c: {reason}");
  public static final TextKey BOARDS_EDIT_STARTED = TextKey.of("holoui.message.boards.edit.started", "&7[&bHoloUI&7]: &aEditing &f{board}&a from revision {revision}. Mutations are staged until save.");
  public static final TextKey BOARDS_EDIT_ALREADY_ACTIVE = TextKey.of("holoui.message.boards.edit.already_active", "&7[&bHoloUI&7]: &cYou are already editing &f{board}&c. Save or cancel first.");
  public static final TextKey BOARDS_EDIT_NONE = TextKey.of("holoui.message.boards.edit.none", "&7[&bHoloUI&7]: &cYou do not have a staged board edit.");
  public static final TextKey BOARDS_EDIT_BUSY = TextKey.of("holoui.message.boards.edit.busy", "&7[&bHoloUI&7]: &cBoard &f{board}&c is currently being saved.");
  public static final TextKey BOARDS_EDIT_STAGED = TextKey.of("holoui.message.boards.edit.staged", "&7[&bHoloUI&7]: &aStaged {operation} for &f{board}&a. Use /holoui boards save to persist it.");
  public static final TextKey BOARDS_EDIT_SAVED = TextKey.of("holoui.message.boards.edit.saved", "&7[&bHoloUI&7]: &aSaved &f{board}&a at revision {revision}.");
  public static final TextKey BOARDS_EDIT_CANCELLED = TextKey.of("holoui.message.boards.edit.cancelled", "&7[&bHoloUI&7]: &aCancelled staged edits for &f{board}&a.");
  public static final TextKey BOARDS_EDIT_IDENTITY_BLOCKED = TextKey.of("holoui.message.boards.edit.identity_blocked", "&7[&bHoloUI&7]: &cSave or cancel the staged edit for &f{board}&c before renaming or deleting it.");
  public static final TextKey IMPORT_PREVIEW_STARTED = TextKey.of("holoui.message.import.preview_started", "&7[&bHoloUI&7]: &7Scanning &f{source}&7 for a non-destructive migration preview...");
  public static final TextKey IMPORT_APPLY_STARTED = TextKey.of("holoui.message.import.apply_started", "&7[&bHoloUI&7]: &7Scanning and applying no-overwrite migration from &f{source}&7...");
  public static final TextKey IMPORT_SOURCE_MISSING = TextKey.of("holoui.message.import.source_missing", "&7[&bHoloUI&7]: &7No &f{source}&7 source was found at &f{path}&7.");
  public static final TextKey IMPORT_PREVIEW_SUMMARY = TextKey.of("holoui.message.import.preview_summary", "&7[&bHoloUI&7]: &f{source}&7 preview: &a{ready} ready&7, &b{resume} resumable&7, &e{conflicts} conflicts&7, &c{errors} errors&7, {warnings} warnings. Source: &f{path}");
  public static final TextKey IMPORT_PREVIEW_ENTRY = TextKey.of("holoui.message.import.preview_entry", "&8- &f{legacy} &7-> &f{board} &7state=&f{state} &7warnings=&f{warnings}");
  public static final TextKey IMPORT_APPLY_SUMMARY = TextKey.of("holoui.message.import.apply_summary", "&7[&bHoloUI&7]: &f{source}&7 migration: &a{imported} imported&7, &e{skipped} skipped&7, &c{failed} failed&7; scan reported {errors} errors and {warnings} warnings.");
  public static final TextKey IMPORT_APPLY_ENTRY = TextKey.of("holoui.message.import.apply_entry", "&8- &f{legacy} &7state=&f{state} &7reason=&f{reason}");
  public static final TextKey IMPORT_ISSUE = TextKey.of("holoui.message.import.issue", "&8- &f{legacy} &7{severity}: &f{reason}");
  public static final TextKey IMPORT_BUSY = TextKey.of("holoui.message.import.busy", "&7[&bHoloUI&7]: &cAnother legacy hologram migration is already running.");
  public static final TextKey IMPORT_FAILED = TextKey.of("holoui.message.import.failed", "&7[&bHoloUI&7]: &cLegacy hologram migration from &f{source}&c failed: {reason}");
  public static final TextKey CONFIG_RELOADED = TextKey.of("holoui.message.config.reloaded", "&2Config \"{name}\" reloaded.");
  public static final TextKey PREVIEW_SCALE_ADJUSTING = TextKey.of("holoui.message.preview_scale.adjusting", "&7Preview size: &f{percent}% &8- &7hold sneak + scroll to resize, double-tap sneak to save");
  public static final TextKey PREVIEW_SCALE_HIDDEN = TextKey.of("holoui.message.preview_scale.hidden", "&7Preview &chidden &8- &7scroll up to restore");
  public static final TextKey PREVIEW_SCALE_SIZE = TextKey.of("holoui.message.preview_scale.size", "&7Preview size: &f{percent}%");
  public static final TextKey PREVIEW_SCALE_SAVED_HIDDEN = TextKey.of("holoui.message.preview_scale.saved_hidden", "&7Preview saved as &chidden &8- &7double-tap sneak + scroll up on a container to restore");
  public static final TextKey PREVIEW_SCALE_SAVED = TextKey.of("holoui.message.preview_scale.saved", "&7Preview size saved: &f{percent}%");
  public static final TextKey PREVIEW_IDLE = TextKey.of("holoui.preview.state.idle", "Idle");
  public static final TextKey PREVIEW_BREWING = TextKey.of("holoui.preview.state.brewing", "Brewing {percent}%");
  public static final TextKey PREVIEW_NEEDS_BLAZE_POWDER = TextKey.of("holoui.preview.state.needs_blaze_powder", "Needs blaze powder");
  public static final TextKey PREVIEW_WAITING = TextKey.of("holoui.preview.state.waiting", "Waiting");
  public static final TextKey PREVIEW_NO_INGREDIENT = TextKey.of("holoui.preview.state.no_ingredient", "No ingredient");
  public static final TextKey PREVIEW_EMPTY = TextKey.of("holoui.preview.state.empty", "Empty");
  public static final TextKey PREVIEW_FUEL_LEVEL = TextKey.of("holoui.preview.stat.fuel_level", "Fuel {fuel}/{maximum}");
  public static final TextKey PREVIEW_NO_FUEL = TextKey.of("holoui.preview.stat.no_fuel", "No fuel");
  public static final TextKey PREVIEW_BOTTLES = TextKey.of("holoui.preview.stat.bottles", "Bottles {bottles}/{maximum}");
  public static final TextKey PREVIEW_SMELTING_ITEM = TextKey.of("holoui.preview.state.smelting_item", "Smelting {item} {percent}%");
  public static final TextKey PREVIEW_SMELTING = TextKey.of("holoui.preview.state.smelting", "Smelting {percent}%");
  public static final TextKey PREVIEW_BLASTING_ITEM = TextKey.of("holoui.preview.state.blasting_item", "Blasting {item} {percent}%");
  public static final TextKey PREVIEW_BLASTING = TextKey.of("holoui.preview.state.blasting", "Blasting {percent}%");
  public static final TextKey PREVIEW_SMOKING_ITEM = TextKey.of("holoui.preview.state.smoking_item", "Smoking {item} {percent}%");
  public static final TextKey PREVIEW_SMOKING = TextKey.of("holoui.preview.state.smoking", "Smoking {percent}%");
  public static final TextKey PREVIEW_HEATING = TextKey.of("holoui.preview.state.heating", "Heating");
  public static final TextKey PREVIEW_NEEDS_FUEL = TextKey.of("holoui.preview.state.needs_fuel", "Needs fuel");
  public static final TextKey PREVIEW_NO_INPUT = TextKey.of("holoui.preview.state.no_input", "No input");
  public static final TextKey PREVIEW_SURGE_SUFFIX = TextKey.of("holoui.preview.state.surge_suffix", "  +{seconds}s");
  public static final TextKey PREVIEW_FUEL_SECONDS = TextKey.of("holoui.preview.stat.fuel_seconds", "Fuel {seconds}s");
  public static final TextKey PREVIEW_FUEL_READY = TextKey.of("holoui.preview.stat.fuel_ready", "Fuel ready");
  public static final TextKey PREVIEW_XP_GAIN = TextKey.of("holoui.preview.stat.xp_gain", "XP +{experience}");
  public static final TextKey PREVIEW_XP_ZERO = TextKey.of("holoui.preview.stat.xp_zero", "XP 0");
  public static final TextKey PREVIEW_DISC_PLAYING = TextKey.of("holoui.preview.state.disc_playing", "Playing {disc}");
  public static final TextKey PREVIEW_DISC_LOADED = TextKey.of("holoui.preview.state.disc_loaded", "Loaded {disc}");
  public static final TextKey PREVIEW_NO_DISC = TextKey.of("holoui.preview.state.no_disc", "No disc");
  public static final TextKey PREVIEW_BEES_AND_HONEY = TextKey.of("holoui.preview.stat.bees_and_honey", "Bees {bees}/{maximumBees}   Honey {honey}/{maximumHoney}");
  public static final TextKey PREVIEW_CAULDRON_EMPTY = TextKey.of("holoui.preview.stat.cauldron_empty", "Empty {level}/{maximum}");
  public static final TextKey PREVIEW_CAULDRON_LEVEL = TextKey.of("holoui.preview.stat.cauldron_level", "Level {level}/{maximum}");
  public static final TextKey THEME_TITLE_CHEST = TextKey.of("holoui.preview.theme.title.chest", "&6&lChest");
  public static final TextKey THEME_TITLE_TRAPPED_CHEST = TextKey.of("holoui.preview.theme.title.trapped_chest", "&c&lTrapped Chest");
  public static final TextKey THEME_TITLE_ENDER_CHEST = TextKey.of("holoui.preview.theme.title.ender_chest", "&5&lEnder Chest");
  public static final TextKey THEME_TITLE_BARREL = TextKey.of("holoui.preview.theme.title.barrel", "&e&lBarrel");
  public static final TextKey THEME_TITLE_DISPENSER = TextKey.of("holoui.preview.theme.title.dispenser", "&7&lDispenser");
  public static final TextKey THEME_TITLE_DROPPER = TextKey.of("holoui.preview.theme.title.dropper", "&8&lDropper");
  public static final TextKey THEME_TITLE_HOPPER = TextKey.of("holoui.preview.theme.title.hopper", "&8&lHopper");
  public static final TextKey THEME_TITLE_FURNACE = TextKey.of("holoui.preview.theme.title.furnace", "&6&lFurnace");
  public static final TextKey THEME_TITLE_SMOKER = TextKey.of("holoui.preview.theme.title.smoker", "&e&lSmoker");
  public static final TextKey THEME_TITLE_BLAST_FURNACE = TextKey.of("holoui.preview.theme.title.blast_furnace", "&b&lBlast Furnace");
  public static final TextKey THEME_TITLE_BEEHIVE = TextKey.of("holoui.preview.theme.title.beehive", "&e&lBeehive");
  public static final TextKey THEME_TITLE_BEE_NEST = TextKey.of("holoui.preview.theme.title.bee_nest", "&e&lBee Nest");
  public static final TextKey THEME_TITLE_CAULDRON = TextKey.of("holoui.preview.theme.title.cauldron", "&7&lCauldron");
  public static final TextKey THEME_TITLE_WATER_CAULDRON = TextKey.of("holoui.preview.theme.title.water_cauldron", "&9&lWater Cauldron");
  public static final TextKey THEME_TITLE_LAVA_CAULDRON = TextKey.of("holoui.preview.theme.title.lava_cauldron", "&6&lLava Cauldron");
  public static final TextKey THEME_TITLE_POWDER_SNOW_CAULDRON = TextKey.of("holoui.preview.theme.title.powder_snow_cauldron", "&f&lPowder Snow Cauldron");
  public static final TextKey THEME_TITLE_JUKEBOX = TextKey.of("holoui.preview.theme.title.jukebox", "&d&lJukebox");
  public static final TextKey THEME_TITLE_BREWING_STAND = TextKey.of("holoui.preview.theme.title.brewing_stand", "&d&lBrewing Stand");
  public static final TextKey THEME_TITLE_CHISELED_BOOKSHELF = TextKey.of("holoui.preview.theme.title.chiseled_bookshelf", "&6&lChiseled Bookshelf");
  public static final TextKey THEME_TITLE_HOPPER_MINECART = TextKey.of("holoui.preview.theme.title.hopper_minecart", "&8&lHopper Minecart");
  public static final TextKey THEME_TITLE_CHEST_MINECART = TextKey.of("holoui.preview.theme.title.chest_minecart", "&6&lChest Minecart");
  public static final TextKey THEME_TITLE_MOBILE = TextKey.of("holoui.preview.theme.title.mobile", "&7&l{name}");
  public static final TextKey THEME_TITLE_SHULKER = TextKey.of("holoui.preview.theme.title.shulker", "&l{color} Shulker");
  public static final TextKey THEME_TITLE_COPPER_CHEST = TextKey.of("holoui.preview.theme.title.copper_chest", "&6&l{name}");
  public static final TextKey THEME_TITLE_SHELF = TextKey.of("holoui.preview.theme.title.shelf", "&6&l{name}");
  public static final TextKey THEME_TITLE_CONTAINER = TextKey.of("holoui.preview.theme.title.container", "&6&lContainer");

  private static final MessageCatalog CATALOG = createCatalog();

  private HoloMessages() {
  }

  public static MessageCatalog catalog() {
    return CATALOG;
  }

  private static MessageCatalog createCatalog() {
    MessageCatalog.Builder builder = MessageCatalog.builder(VolmitLocales.ENGLISH);
    builder.addAll(DirectorMessages.keys());
    builder.addAll(List.of(
        COMMAND_ROOT, COMMAND_LIST, COMMAND_OPEN, COMMAND_BACK, COMMAND_CLOSE, COMMAND_MOVE, COMMAND_BUILDER,
        COMMAND_EDIT, COMMAND_SYNC, COMMAND_SYNC_LIST, COMMAND_SYNC_STATUS, COMMAND_SYNC_REVOKE,
        COMMAND_SYNC_PULL,
        COMMAND_ITEMS,
        COMMAND_ITEMS_STATUS, COMMAND_ITEMS_EXPORT, COMMAND_PREVIEWS, COMMAND_PREVIEWS_LIST,
        COMMAND_PREVIEWS_RESET, COMMAND_PREVIEWS_DUMP, COMMAND_MENUS, COMMAND_MENUS_ADDROW,
        COMMAND_MENUS_INSERTROW, COMMAND_MENUS_SETROW, COMMAND_MENUS_REMOVEROW,
        COMMAND_MENUS_OFFSETROW, COMMAND_MENUS_SETICON, COMMAND_MENUS_STYLE, COMMAND_MENUS_IMAGE,
        COMMAND_MENUS_COPY, COMMAND_BOARDS, COMMAND_BOARDS_LIST,
        COMMAND_BOARDS_RELOAD,
        COMMAND_BOARDS_NEAR, COMMAND_BOARDS_INFO, COMMAND_BOARDS_CREATE, COMMAND_BOARDS_DELETE,
        COMMAND_BOARDS_RENAME, COMMAND_BOARDS_COPY, COMMAND_BOARDS_MOVE, COMMAND_BOARDS_MOVEHERE,
        COMMAND_BOARDS_TP, COMMAND_BOARDS_ROTATE, COMMAND_BOARDS_SCALE, COMMAND_BOARDS_ALIGN,
        COMMAND_BOARDS_MENU, COMMAND_BOARDS_ADDROW, COMMAND_BOARDS_INSERTROW, COMMAND_BOARDS_SETROW,
        COMMAND_BOARDS_REMOVEROW, COMMAND_BOARDS_OFFSETROW, COMMAND_BOARDS_SETICON,
        COMMAND_BOARDS_STYLE, COMMAND_BOARDS_IMAGE, COMMAND_BOARDS_RANGES,
        COMMAND_BOARDS_VISIBILITY, COMMAND_BOARDS_PERMISSIONS,
        COMMAND_BOARDS_FOLLOW, COMMAND_BOARDS_UNFOLLOW, COMMAND_BOARDS_EDIT, COMMAND_BOARDS_EDITWEB,
        COMMAND_BOARDS_SAVE,
        COMMAND_BOARDS_CANCEL, COMMAND_IMPORT, COMMAND_IMPORT_PREVIEW, COMMAND_IMPORT_APPLY, PARAMETER_SENDER,
        PARAMETER_MENU, PARAMETER_EDIT_MENU, PARAMETER_SYNC_SESSION, PARAMETER_PREVIEWS_NAME,
        PARAMETER_CONTENT_MENU,
        PARAMETER_NEW_MENU, PARAMETER_ROW_INDEX, PARAMETER_ROW_TEXT, PARAMETER_ROW_X, PARAMETER_ROW_Y,
        PARAMETER_ROW_Z, PARAMETER_ROW_ICON_TYPE, PARAMETER_ROW_ICON_VALUE,
        PARAMETER_ROW_STYLE_PROPERTY, PARAMETER_ROW_STYLE_VALUE, PARAMETER_IMAGE_PATH, PARAMETER_PAGE,
        PARAMETER_BOARD_ID, PARAMETER_BOARD_RADIUS,
        PARAMETER_BOARD_MENU, PARAMETER_BOARD_NEW_ID, PARAMETER_BOARD_X, PARAMETER_BOARD_Y,
        PARAMETER_BOARD_Z, PARAMETER_BOARD_YAW, PARAMETER_BOARD_PITCH, PARAMETER_BOARD_ROLL,
        PARAMETER_BOARD_SCALE, PARAMETER_BOARD_REFERENCE, PARAMETER_BOARD_AXES, PARAMETER_BOARD_VIEW_RANGE,
        PARAMETER_BOARD_INTERACTION_RANGE, PARAMETER_BOARD_VISIBILITY, PARAMETER_BOARD_VIEW_PERMISSION,
        PARAMETER_BOARD_INTERACT_PERMISSION, PARAMETER_BOARD_PLAYER, PARAMETER_BOARD_FOLLOW_ROTATION,
        PARAMETER_IMPORT_SOURCE, ERROR_MENU_NAME_REQUIRED, ERROR_SYNC_SESSION_REQUIRED,
        ERROR_BOARD_ID_REQUIRED,
        ERROR_BOARD_PLAYER_REQUIRED, ERROR_ROW_ICON_TYPE_REQUIRED,
        ERROR_ROW_STYLE_PROPERTY_REQUIRED, ERROR_IMPORT_SOURCE_REQUIRED, ERROR_IMPORT_SOURCE_UNKNOWN,
        PERMISSION_DENIED, UNKNOWN_COMMAND, NO_MENUS,
        MENU_LIST_HEADER, MENU_LIST_ENTRY, MENUS_PLAYER_ONLY, COMMAND_PLAYER_ONLY,
        NO_PREVIOUS_MENU, MENU_CLOSED, MENU_MOVED, NO_OPEN_MENU, MENU_UNAVAILABLE, MENU_PERMISSION_DENIED,
        MENU_OPEN_FAILED, MENU_CONTENT_UPDATED, MENU_CONTENT_COPIED, MENU_CONTENT_REVISION_CONFLICT,
        MENU_CONTENT_ALREADY_EXISTS, MENU_CONTENT_INVALID, MENU_CONTENT_FAILED,
        BUILDER_HEADER, BUILDER_LINK_HOVER, BUILDER_OPEN,
        EDITOR_MENU_OPEN, EDITOR_MENU_LINK, EDITOR_MENU_HOVER, EDITOR_MENU_TOO_LARGE, EDITOR_MENU_FAILED,
        SYNC_PREPARING, SYNC_FALLBACK, SYNC_OPEN_CONSOLE, SYNC_CAPABILITY_WARNING,
        SYNC_OPEN_LABEL, SYNC_COPY_LABEL, SYNC_REVOKE_LABEL, SYNC_LINK_HOVER,
        SYNC_LIST_HEADER, SYNC_LIST_EMPTY, SYNC_LIST_ENTRY, SYNC_STATUS, SYNC_REVOKED,
        SYNC_PULLED, SYNC_UNKNOWN, SYNC_FAILED,
        ITEMS_DISABLED, ITEMS_STATUS_HEADER, ITEMS_STATUS_SUMMARY, ITEMS_STATUS_ENTRY,
        ITEMS_STATUS_HINT, ITEMS_STATE_READY, ITEMS_STATE_LOADING, ITEMS_STATE_INACTIVE,
        ITEMS_STATE_MISSING, ITEMS_EXPORT_STARTED, ITEMS_EXPORT_DONE, ITEMS_EXPORT_EMPTY,
        ITEMS_EXPORT_FAILED, ITEMS_EXPORT_BUSY,
        PREVIEWS_LIST_HEADER, PREVIEWS_LIST_EMPTY, PREVIEWS_LIST_ENTRY, PREVIEWS_RESET_STARTED,
        PREVIEWS_RESET_DONE, PREVIEWS_RESET_NONE, PREVIEWS_DUMP_UNKNOWN, PREVIEWS_DUMP_RESULT,
        PREVIEWS_DUMP_NO_ERRORS, PREVIEWS_DUMP_ERROR_LINE, PREVIEWS_DUMP_ERROR_MORE,
        BOARDS_LIST_HEADER, BOARDS_LIST_EMPTY, BOARDS_LIST_ENTRY, BOARDS_LIST_PAGE,
        BOARDS_LIST_PAGE_INVALID, BOARDS_RELOADED, BOARDS_NEAR_HEADER,
        BOARDS_NEAR_EMPTY, BOARDS_NEAR_ENTRY, BOARDS_INFO_HEADER, BOARDS_INFO_IDENTITY,
        BOARDS_INFO_TRANSFORM, BOARDS_INFO_VISIBILITY, BOARDS_INFO_FOLLOW, BOARDS_CREATED,
        BOARDS_DELETED, BOARDS_RENAMED, BOARDS_COPIED, BOARDS_UPDATED, BOARDS_TELEPORTED,
        BOARDS_UNKNOWN, BOARDS_ALREADY_EXISTS, BOARDS_INVALID, BOARDS_MENU_UNAVAILABLE,
        BOARDS_PLAYER_UNAVAILABLE, BOARDS_WORLD_UNAVAILABLE, BOARDS_REVISION_CONFLICT, BOARDS_FAILED,
        BOARDS_EDIT_STARTED, BOARDS_EDIT_ALREADY_ACTIVE, BOARDS_EDIT_NONE, BOARDS_EDIT_BUSY,
        BOARDS_EDIT_STAGED, BOARDS_EDIT_SAVED, BOARDS_EDIT_CANCELLED, BOARDS_EDIT_IDENTITY_BLOCKED,
        IMPORT_PREVIEW_STARTED, IMPORT_APPLY_STARTED, IMPORT_SOURCE_MISSING, IMPORT_PREVIEW_SUMMARY,
        IMPORT_PREVIEW_ENTRY, IMPORT_APPLY_SUMMARY, IMPORT_APPLY_ENTRY, IMPORT_ISSUE, IMPORT_BUSY,
        IMPORT_FAILED,
        CONFIG_RELOADED, PREVIEW_SCALE_ADJUSTING, PREVIEW_SCALE_HIDDEN, PREVIEW_SCALE_SIZE,
        PREVIEW_SCALE_SAVED_HIDDEN, PREVIEW_SCALE_SAVED, PREVIEW_IDLE, PREVIEW_BREWING,
        PREVIEW_NEEDS_BLAZE_POWDER, PREVIEW_WAITING, PREVIEW_NO_INGREDIENT, PREVIEW_EMPTY,
        PREVIEW_FUEL_LEVEL, PREVIEW_NO_FUEL, PREVIEW_BOTTLES, PREVIEW_SMELTING_ITEM,
        PREVIEW_SMELTING, PREVIEW_BLASTING_ITEM, PREVIEW_BLASTING, PREVIEW_SMOKING_ITEM,
        PREVIEW_SMOKING, PREVIEW_HEATING, PREVIEW_NEEDS_FUEL, PREVIEW_NO_INPUT,
        PREVIEW_SURGE_SUFFIX, PREVIEW_FUEL_SECONDS, PREVIEW_FUEL_READY, PREVIEW_XP_GAIN,
        PREVIEW_XP_ZERO, PREVIEW_DISC_PLAYING, PREVIEW_DISC_LOADED, PREVIEW_NO_DISC,
        PREVIEW_BEES_AND_HONEY, PREVIEW_CAULDRON_EMPTY, PREVIEW_CAULDRON_LEVEL,
        THEME_TITLE_CHEST, THEME_TITLE_TRAPPED_CHEST, THEME_TITLE_ENDER_CHEST, THEME_TITLE_BARREL,
        THEME_TITLE_DISPENSER, THEME_TITLE_DROPPER, THEME_TITLE_HOPPER, THEME_TITLE_FURNACE,
        THEME_TITLE_SMOKER, THEME_TITLE_BLAST_FURNACE, THEME_TITLE_BEEHIVE, THEME_TITLE_BEE_NEST,
        THEME_TITLE_CAULDRON, THEME_TITLE_WATER_CAULDRON, THEME_TITLE_LAVA_CAULDRON,
        THEME_TITLE_POWDER_SNOW_CAULDRON, THEME_TITLE_JUKEBOX, THEME_TITLE_BREWING_STAND,
        THEME_TITLE_CHISELED_BOOKSHELF, THEME_TITLE_HOPPER_MINECART, THEME_TITLE_CHEST_MINECART,
        THEME_TITLE_MOBILE, THEME_TITLE_SHULKER, THEME_TITLE_COPPER_CHEST, THEME_TITLE_SHELF,
        THEME_TITLE_CONTAINER
    ));
    return builder.build();
  }
}
