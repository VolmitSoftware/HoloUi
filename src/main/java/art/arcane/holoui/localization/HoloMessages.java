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
  public static final TextKey COMMAND_BUILDER = TextKey.of("holoui.command.builder.root", "HoloUI builder server controls");
  public static final TextKey COMMAND_BUILDER_STATUS = TextKey.of("holoui.command.builder.status", "Show whether the HoloUI builder service is running");
  public static final TextKey COMMAND_BUILDER_START = TextKey.of("holoui.command.builder.start", "Start the HoloUI builder service");
  public static final TextKey COMMAND_BUILDER_STOP = TextKey.of("holoui.command.builder.stop", "Stop the HoloUI builder service");
  public static final TextKey PARAMETER_SENDER = TextKey.of("holoui.parameter.sender", "Command sender context");
  public static final TextKey PARAMETER_MENU = TextKey.of("holoui.parameter.menu", "Menu id to open (* shows all menus)");
  public static final TextKey ERROR_MENU_NAME_REQUIRED = TextKey.of("holoui.error.menu_name_required", "Menu name cannot be empty");
  public static final TextKey PERMISSION_DENIED = TextKey.of("holoui.message.permission_denied", "&7[&bHoloUI&7]: &cYou lack permission &f{permission}&c.");
  public static final TextKey UNKNOWN_COMMAND = TextKey.of("holoui.message.unknown_command", "&7[&bHoloUI&7]: &cUnknown command \"&f{command}&c\".");
  public static final TextKey NO_MENUS = TextKey.of("holoui.message.menu.none", "&7[&bHoloUI&7]: &7No menus are available.");
  public static final TextKey MENU_LIST_HEADER = TextKey.of("holoui.message.menu.list.header", "&7----------+=== Menus ===+----------");
  public static final TextKey MENU_LIST_ENTRY = TextKey.of("holoui.message.menu.list.entry", "&7  - &f{menu}");
  public static final TextKey MENU_LIST_FOOTER = TextKey.of("holoui.message.menu.list.footer", "&7----------------------------------");
  public static final TextKey MENUS_PLAYER_ONLY = TextKey.of("holoui.message.menu.player_only", "&7[&bHoloUI&7]: &cMenus can only be opened by players.");
  public static final TextKey COMMAND_PLAYER_ONLY = TextKey.of("holoui.message.command.player_only", "&7[&bHoloUI&7]: &cThis command is only available to players.");
  public static final TextKey NO_PREVIOUS_MENU = TextKey.of("holoui.message.menu.no_previous", "&7[&bHoloUI&7]: &cNo previous menu is available.");
  public static final TextKey MENU_CLOSED = TextKey.of("holoui.message.menu.closed", "&7[&bHoloUI&7]: &aMenu closed.");
  public static final TextKey NO_OPEN_MENU = TextKey.of("holoui.message.menu.none_open", "&7[&bHoloUI&7]: &cNo menu is currently open.");
  public static final TextKey MENU_UNAVAILABLE = TextKey.of("holoui.message.menu.unavailable", "&7[&bHoloUI&7]: &c\"{menu}\" is not available.");
  public static final TextKey MENU_PERMISSION_DENIED = TextKey.of("holoui.message.menu.permission_denied", "&7[&bHoloUI&7]: &cYou lack permission to open \"{menu}\".");
  public static final TextKey MENU_OPEN_FAILED = TextKey.of("holoui.message.menu.open_failed", "&7[&bHoloUI&7]: &cFailed to open menu \"{menu}\".");
  public static final TextKey BUILDER_RUNNING = TextKey.of("holoui.message.builder.running", "&7[&bHoloUI&7]: &aBuilder is running at &f{url}&a.");
  public static final TextKey BUILDER_NOT_RUNNING = TextKey.of("holoui.message.builder.not_running", "&7[&bHoloUI&7]: &cBuilder is not running.");
  public static final TextKey BUILDER_START_HINT = TextKey.of("holoui.message.builder.start_hint", "&7[&bHoloUI&7]: &7Use &f/holoui builder start&7 or visit &fhttps://holoui.volmit.com/&7.");
  public static final TextKey BUILDER_ALREADY_RUNNING = TextKey.of("holoui.message.builder.already_running", "&7[&bHoloUI&7]: &cBuilder is already running.");
  public static final TextKey BUILDER_STARTING = TextKey.of("holoui.message.builder.starting", "&7[&bHoloUI&7]: &aStarting builder...");
  public static final TextKey BUILDER_SETUP_FAILED = TextKey.of("holoui.message.builder.setup_failed", "&7[&bHoloUI&7]: &cAn error occurred while setting up the builder. Check the logs.");
  public static final TextKey BUILDER_STOPPED = TextKey.of("holoui.message.builder.stopped", "&7[&bHoloUI&7]: &aBuilder has been stopped.");
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
  public static final TextKey THEME_DETAIL_STORAGE_GRID = TextKey.of("holoui.preview.theme.detail.storage_grid", "&7Storage grid");
  public static final TextKey THEME_DETAIL_REDSTONE_STORAGE = TextKey.of("holoui.preview.theme.detail.redstone_storage", "&7Redstone-linked storage");
  public static final TextKey THEME_DETAIL_VOID_STORAGE = TextKey.of("holoui.preview.theme.detail.void_storage", "&7Personal void storage");
  public static final TextKey THEME_DETAIL_COMPACT_STORAGE = TextKey.of("holoui.preview.theme.detail.compact_storage", "&7Compact storage");
  public static final TextKey THEME_DETAIL_LAUNCHER = TextKey.of("holoui.preview.theme.detail.launcher", "&7Powered launcher matrix");
  public static final TextKey THEME_DETAIL_DROPPER = TextKey.of("holoui.preview.theme.detail.dropper", "&7Powered dropper matrix");
  public static final TextKey THEME_DETAIL_TRANSFER_QUEUE = TextKey.of("holoui.preview.theme.detail.transfer_queue", "&7Transfer queue");
  public static final TextKey THEME_DETAIL_SMELTING = TextKey.of("holoui.preview.theme.detail.smelting", "&7Smelting chamber");
  public static final TextKey THEME_DETAIL_COOKING = TextKey.of("holoui.preview.theme.detail.cooking", "&7Food cooking chamber");
  public static final TextKey THEME_DETAIL_ORE_PROCESSING = TextKey.of("holoui.preview.theme.detail.ore_processing", "&7Ore processing chamber");
  public static final TextKey THEME_DETAIL_BEES = TextKey.of("holoui.preview.theme.detail.bees", "&7Honey and bee occupancy");
  public static final TextKey THEME_DETAIL_EMPTY_VESSEL = TextKey.of("holoui.preview.theme.detail.empty_vessel", "&7Empty vessel");
  public static final TextKey THEME_DETAIL_WATER_LEVEL = TextKey.of("holoui.preview.theme.detail.water_level", "&7Water level");
  public static final TextKey THEME_DETAIL_LAVA_LEVEL = TextKey.of("holoui.preview.theme.detail.lava_level", "&7Lava level");
  public static final TextKey THEME_DETAIL_SNOW_LEVEL = TextKey.of("holoui.preview.theme.detail.snow_level", "&7Powder snow level");
  public static final TextKey THEME_DETAIL_DISC_PLAYER = TextKey.of("holoui.preview.theme.detail.disc_player", "&7Disc player");
  public static final TextKey THEME_DETAIL_BREWING = TextKey.of("holoui.preview.theme.detail.brewing", "&7Potion brewing");
  public static final TextKey THEME_DETAIL_BOOK_SLOTS = TextKey.of("holoui.preview.theme.detail.book_slots", "&7Six book slots");
  public static final TextKey THEME_DETAIL_MOBILE_TRANSFER = TextKey.of("holoui.preview.theme.detail.mobile_transfer", "&7Mobile transfer queue");
  public static final TextKey THEME_DETAIL_MOBILE_STORAGE = TextKey.of("holoui.preview.theme.detail.mobile_storage", "&7Mobile storage grid");
  public static final TextKey THEME_DETAIL_MOBILE_SLOTS = TextKey.of("holoui.preview.theme.detail.mobile_slots", "&7{size} mobile slots");
  public static final TextKey THEME_DETAIL_PORTABLE_STORAGE = TextKey.of("holoui.preview.theme.detail.portable_storage", "&7Portable storage");
  public static final TextKey THEME_DETAIL_COPPER_STORAGE = TextKey.of("holoui.preview.theme.detail.copper_storage", "&7Copper storage grid");
  public static final TextKey THEME_DETAIL_SHELF = TextKey.of("holoui.preview.theme.detail.shelf", "&7Shelf state");
  public static final TextKey THEME_DETAIL_INVENTORY = TextKey.of("holoui.preview.theme.detail.inventory", "&7Inventory");
  public static final TextKey THEME_EMPTY_STORED_ITEMS = TextKey.of("holoui.preview.theme.empty.stored_items", "&8No stored items");
  public static final TextKey THEME_EMPTY_LAUNCH_ITEMS = TextKey.of("holoui.preview.theme.empty.launch_items", "&8No launch items");
  public static final TextKey THEME_EMPTY_DROP_ITEMS = TextKey.of("holoui.preview.theme.empty.drop_items", "&8No drop items");
  public static final TextKey THEME_EMPTY_QUEUE = TextKey.of("holoui.preview.theme.empty.queue", "&8Queue empty");
  public static final TextKey THEME_EMPTY_ACTIVE_RECIPE = TextKey.of("holoui.preview.theme.empty.active_recipe", "&8No active recipe");
  public static final TextKey THEME_EMPTY_BEES = TextKey.of("holoui.preview.theme.empty.bees", "&8No bees inside");
  public static final TextKey THEME_EMPTY_NO_FLUID = TextKey.of("holoui.preview.theme.empty.no_fluid", "&8No fluid");
  public static final TextKey THEME_EMPTY_EMPTY = TextKey.of("holoui.preview.theme.empty.empty", "&8Empty");
  public static final TextKey THEME_EMPTY_NO_DISC = TextKey.of("holoui.preview.theme.empty.no_disc", "&8No disc");
  public static final TextKey THEME_EMPTY_NO_POTIONS = TextKey.of("holoui.preview.theme.empty.no_potions", "&8No potions");
  public static final TextKey THEME_EMPTY_NO_BOOKS = TextKey.of("holoui.preview.theme.empty.no_books", "&8No books");
  public static final TextKey THEME_EMPTY_BOX = TextKey.of("holoui.preview.theme.empty.box", "&8Box empty");
  public static final TextKey THEME_EMPTY_SHELF = TextKey.of("holoui.preview.theme.empty.shelf", "&8Empty shelf");
  public static final TextKey THEME_EMPTY_NO_ITEMS = TextKey.of("holoui.preview.theme.empty.no_items", "&8No items");

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
        COMMAND_BUILDER_STATUS, COMMAND_BUILDER_START, COMMAND_BUILDER_STOP, PARAMETER_SENDER,
        PARAMETER_MENU, ERROR_MENU_NAME_REQUIRED, PERMISSION_DENIED, UNKNOWN_COMMAND, NO_MENUS,
        MENU_LIST_HEADER, MENU_LIST_ENTRY, MENU_LIST_FOOTER, MENUS_PLAYER_ONLY, COMMAND_PLAYER_ONLY,
        NO_PREVIOUS_MENU, MENU_CLOSED, NO_OPEN_MENU, MENU_UNAVAILABLE, MENU_PERMISSION_DENIED,
        MENU_OPEN_FAILED, BUILDER_RUNNING, BUILDER_NOT_RUNNING, BUILDER_START_HINT,
        BUILDER_ALREADY_RUNNING, BUILDER_STARTING, BUILDER_SETUP_FAILED, BUILDER_STOPPED,
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
        THEME_TITLE_CONTAINER, THEME_DETAIL_STORAGE_GRID, THEME_DETAIL_REDSTONE_STORAGE,
        THEME_DETAIL_VOID_STORAGE, THEME_DETAIL_COMPACT_STORAGE, THEME_DETAIL_LAUNCHER,
        THEME_DETAIL_DROPPER, THEME_DETAIL_TRANSFER_QUEUE, THEME_DETAIL_SMELTING,
        THEME_DETAIL_COOKING, THEME_DETAIL_ORE_PROCESSING, THEME_DETAIL_BEES,
        THEME_DETAIL_EMPTY_VESSEL, THEME_DETAIL_WATER_LEVEL, THEME_DETAIL_LAVA_LEVEL,
        THEME_DETAIL_SNOW_LEVEL, THEME_DETAIL_DISC_PLAYER, THEME_DETAIL_BREWING,
        THEME_DETAIL_BOOK_SLOTS, THEME_DETAIL_MOBILE_TRANSFER, THEME_DETAIL_MOBILE_STORAGE,
        THEME_DETAIL_MOBILE_SLOTS, THEME_DETAIL_PORTABLE_STORAGE, THEME_DETAIL_COPPER_STORAGE,
        THEME_DETAIL_SHELF, THEME_DETAIL_INVENTORY, THEME_EMPTY_STORED_ITEMS,
        THEME_EMPTY_LAUNCH_ITEMS, THEME_EMPTY_DROP_ITEMS, THEME_EMPTY_QUEUE,
        THEME_EMPTY_ACTIVE_RECIPE, THEME_EMPTY_BEES, THEME_EMPTY_NO_FLUID, THEME_EMPTY_EMPTY,
        THEME_EMPTY_NO_DISC, THEME_EMPTY_NO_POTIONS, THEME_EMPTY_NO_BOOKS, THEME_EMPTY_BOX,
        THEME_EMPTY_SHELF, THEME_EMPTY_NO_ITEMS
    ));
    return builder.build();
  }
}
