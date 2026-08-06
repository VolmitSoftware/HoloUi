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
  public static final TextKey COMMAND_BUILDER = TextKey.of("holoui.command.builder", "Link to the hosted HoloUI web editor");
  public static final TextKey COMMAND_ITEMS = TextKey.of("holoui.command.items.root", "Custom item provider tools");
  public static final TextKey COMMAND_ITEMS_STATUS = TextKey.of("holoui.command.items.status", "Show which custom item providers are active");
  public static final TextKey COMMAND_ITEMS_EXPORT = TextKey.of("holoui.command.items.export", "Export the custom item catalog for the web editor");
  public static final TextKey COMMAND_PREVIEWS = TextKey.of("holoui.command.previews.root", "Preview document tools");
  public static final TextKey COMMAND_PREVIEWS_LIST = TextKey.of("holoui.command.previews.list", "List preview documents and their match rules");
  public static final TextKey COMMAND_PREVIEWS_RESET = TextKey.of("holoui.command.previews.reset", "Restore shipped preview defaults (does not remove extra user documents that may shadow them)");
  public static final TextKey COMMAND_PREVIEWS_DUMP = TextKey.of("holoui.command.previews.dump", "Build a preview document once and print its element counts");
  public static final TextKey PARAMETER_SENDER = TextKey.of("holoui.parameter.sender", "Command sender context");
  public static final TextKey PARAMETER_MENU = TextKey.of("holoui.parameter.menu", "Menu id to open (* shows all menus)");
  public static final TextKey PARAMETER_PREVIEWS_NAME = TextKey.of("holoui.parameter.previews_name", "Preview document name");
  public static final TextKey ERROR_MENU_NAME_REQUIRED = TextKey.of("holoui.error.menu_name_required", "Menu name cannot be empty");
  public static final TextKey PERMISSION_DENIED = TextKey.of("holoui.message.permission_denied", "&7[&bHoloUI&7]: &cYou lack permission &f{permission}&c.");
  public static final TextKey UNKNOWN_COMMAND = TextKey.of("holoui.message.unknown_command", "&7[&bHoloUI&7]: &cUnknown command \"&f{command}&c\".");
  public static final TextKey NO_MENUS = TextKey.of("holoui.message.menu.none", "&7[&bHoloUI&7]: &7No menus are available.");
  public static final TextKey MENU_LIST_HEADER = TextKey.of("holoui.message.menu.list.header", "Menus");
  public static final TextKey MENU_LIST_ENTRY = TextKey.of("holoui.message.menu.list.entry", "Click to open {menu}.");
  public static final TextKey MENUS_PLAYER_ONLY = TextKey.of("holoui.message.menu.player_only", "&7[&bHoloUI&7]: &cMenus can only be opened by players.");
  public static final TextKey COMMAND_PLAYER_ONLY = TextKey.of("holoui.message.command.player_only", "&7[&bHoloUI&7]: &cThis command is only available to players.");
  public static final TextKey NO_PREVIOUS_MENU = TextKey.of("holoui.message.menu.no_previous", "&7[&bHoloUI&7]: &cNo previous menu is available.");
  public static final TextKey MENU_CLOSED = TextKey.of("holoui.message.menu.closed", "&7[&bHoloUI&7]: &aMenu closed.");
  public static final TextKey NO_OPEN_MENU = TextKey.of("holoui.message.menu.none_open", "&7[&bHoloUI&7]: &cNo menu is currently open.");
  public static final TextKey MENU_UNAVAILABLE = TextKey.of("holoui.message.menu.unavailable", "&7[&bHoloUI&7]: &c\"{menu}\" is not available.");
  public static final TextKey MENU_PERMISSION_DENIED = TextKey.of("holoui.message.menu.permission_denied", "&7[&bHoloUI&7]: &cYou lack permission to open \"{menu}\".");
  public static final TextKey MENU_OPEN_FAILED = TextKey.of("holoui.message.menu.open_failed", "&7[&bHoloUI&7]: &cFailed to open menu \"{menu}\".");
  public static final TextKey BUILDER_HEADER = TextKey.of("holoui.message.builder.header", "Web Editor");
  public static final TextKey BUILDER_LINK_HOVER = TextKey.of("holoui.message.builder.hover", "Click to open the HoloUI web editor.");
  public static final TextKey BUILDER_OPEN = TextKey.of("holoui.message.builder.open", "&7[&bHoloUI&7]: &7Web editor: &f{url}");
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
        COMMAND_ROOT, COMMAND_LIST, COMMAND_OPEN, COMMAND_BACK, COMMAND_CLOSE, COMMAND_BUILDER,
        COMMAND_ITEMS,
        COMMAND_ITEMS_STATUS, COMMAND_ITEMS_EXPORT, COMMAND_PREVIEWS, COMMAND_PREVIEWS_LIST,
        COMMAND_PREVIEWS_RESET, COMMAND_PREVIEWS_DUMP, PARAMETER_SENDER,
        PARAMETER_MENU, PARAMETER_PREVIEWS_NAME, ERROR_MENU_NAME_REQUIRED, PERMISSION_DENIED, UNKNOWN_COMMAND, NO_MENUS,
        MENU_LIST_HEADER, MENU_LIST_ENTRY, MENUS_PLAYER_ONLY, COMMAND_PLAYER_ONLY,
        NO_PREVIOUS_MENU, MENU_CLOSED, NO_OPEN_MENU, MENU_UNAVAILABLE, MENU_PERMISSION_DENIED,
        MENU_OPEN_FAILED, BUILDER_HEADER, BUILDER_LINK_HOVER, BUILDER_OPEN,
        ITEMS_DISABLED, ITEMS_STATUS_HEADER, ITEMS_STATUS_SUMMARY, ITEMS_STATUS_ENTRY,
        ITEMS_STATUS_HINT, ITEMS_STATE_READY, ITEMS_STATE_LOADING, ITEMS_STATE_INACTIVE,
        ITEMS_STATE_MISSING, ITEMS_EXPORT_STARTED, ITEMS_EXPORT_DONE, ITEMS_EXPORT_EMPTY,
        ITEMS_EXPORT_FAILED, ITEMS_EXPORT_BUSY,
        PREVIEWS_LIST_HEADER, PREVIEWS_LIST_EMPTY, PREVIEWS_LIST_ENTRY, PREVIEWS_RESET_STARTED,
        PREVIEWS_RESET_DONE, PREVIEWS_RESET_NONE, PREVIEWS_DUMP_UNKNOWN, PREVIEWS_DUMP_RESULT,
        PREVIEWS_DUMP_NO_ERRORS, PREVIEWS_DUMP_ERROR_LINE, PREVIEWS_DUMP_ERROR_MORE,
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
