package art.arcane.holoui.menu.special.inventories;

import art.arcane.holoui.localization.HoloLocalization;
import art.arcane.holoui.localization.HoloMessages;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;
import org.bukkit.block.Container;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public record ContainerPreviewTheme(
    String trimColorCode,
    String headerText,
    String detailText,
    String emptyText,
    int panelColor,
    int slotColor
) {

  public int accentColor() {
    return legacyToRgb(trimColorCode);
  }

  public String plainTitle() {
    return headerText.replaceAll("&[0-9A-Fa-fK-Ok-oRr]", "");
  }

  private static int legacyToRgb(String code) {
    String normalized = code == null ? "" : code.replace("&", "").toLowerCase(Locale.ENGLISH);
    char token = normalized.isEmpty() ? 'f' : normalized.charAt(normalized.length() - 1);
    return switch (token) {
      case '0' -> 0x202028;
      case '1' -> 0x3A5BD0;
      case '2' -> 0x3FB84F;
      case '3' -> 0x3AC4C4;
      case '4' -> 0xCB4747;
      case '5' -> 0xB152DA;
      case '6' -> 0xF2A535;
      case '7' -> 0xA6ACB6;
      case '8' -> 0x6E747E;
      case '9' -> 0x5E82FF;
      case 'a' -> 0x6FE06F;
      case 'b' -> 0x6FEAEA;
      case 'c' -> 0xEC6464;
      case 'd' -> 0xEC88EC;
      case 'e' -> 0xF2D451;
      default -> 0xCBD0D9;
    };
  }

  public static ContainerPreviewTheme resolve(Container container) {
    return resolve(container.getType());
  }

  public static ContainerPreviewTheme resolve(Material type) {
    if (type.name().endsWith("_SHULKER_BOX") || type == Material.SHULKER_BOX) {
      return shulkerTheme(type);
    }
    if (isCopperChest(type)) {
      return copperChestTheme(type);
    }
    return switch (type) {
      case CHEST ->
          theme("&6", HoloMessages.THEME_TITLE_CHEST, HoloMessages.THEME_DETAIL_STORAGE_GRID, HoloMessages.THEME_EMPTY_STORED_ITEMS);
      case TRAPPED_CHEST ->
          theme("&c", HoloMessages.THEME_TITLE_TRAPPED_CHEST, HoloMessages.THEME_DETAIL_REDSTONE_STORAGE, HoloMessages.THEME_EMPTY_STORED_ITEMS);
      case ENDER_CHEST ->
          theme("&5", HoloMessages.THEME_TITLE_ENDER_CHEST, HoloMessages.THEME_DETAIL_VOID_STORAGE, HoloMessages.THEME_EMPTY_STORED_ITEMS);
      case BARREL ->
          theme("&e", HoloMessages.THEME_TITLE_BARREL, HoloMessages.THEME_DETAIL_COMPACT_STORAGE, HoloMessages.THEME_EMPTY_STORED_ITEMS);
      case DISPENSER ->
          theme("&7", HoloMessages.THEME_TITLE_DISPENSER, HoloMessages.THEME_DETAIL_LAUNCHER, HoloMessages.THEME_EMPTY_LAUNCH_ITEMS);
      case DROPPER ->
          theme("&8", HoloMessages.THEME_TITLE_DROPPER, HoloMessages.THEME_DETAIL_DROPPER, HoloMessages.THEME_EMPTY_DROP_ITEMS);
      case HOPPER ->
          theme("&8", HoloMessages.THEME_TITLE_HOPPER, HoloMessages.THEME_DETAIL_TRANSFER_QUEUE, HoloMessages.THEME_EMPTY_QUEUE);
      case FURNACE ->
          theme("&6", HoloMessages.THEME_TITLE_FURNACE, HoloMessages.THEME_DETAIL_SMELTING, HoloMessages.THEME_EMPTY_ACTIVE_RECIPE);
      case SMOKER ->
          theme("&e", HoloMessages.THEME_TITLE_SMOKER, HoloMessages.THEME_DETAIL_COOKING, HoloMessages.THEME_EMPTY_ACTIVE_RECIPE);
      case BLAST_FURNACE ->
          theme("&b", HoloMessages.THEME_TITLE_BLAST_FURNACE, HoloMessages.THEME_DETAIL_ORE_PROCESSING, HoloMessages.THEME_EMPTY_ACTIVE_RECIPE);
      case BEEHIVE ->
          theme("&e", HoloMessages.THEME_TITLE_BEEHIVE, HoloMessages.THEME_DETAIL_BEES, HoloMessages.THEME_EMPTY_BEES);
      case BEE_NEST ->
          theme("&e", HoloMessages.THEME_TITLE_BEE_NEST, HoloMessages.THEME_DETAIL_BEES, HoloMessages.THEME_EMPTY_BEES);
      case CAULDRON ->
          theme("&7", HoloMessages.THEME_TITLE_CAULDRON, HoloMessages.THEME_DETAIL_EMPTY_VESSEL, HoloMessages.THEME_EMPTY_NO_FLUID);
      case WATER_CAULDRON ->
          theme("&9", HoloMessages.THEME_TITLE_WATER_CAULDRON, HoloMessages.THEME_DETAIL_WATER_LEVEL, HoloMessages.THEME_EMPTY_EMPTY);
      case LAVA_CAULDRON ->
          theme("&6", HoloMessages.THEME_TITLE_LAVA_CAULDRON, HoloMessages.THEME_DETAIL_LAVA_LEVEL, HoloMessages.THEME_EMPTY_EMPTY);
      case POWDER_SNOW_CAULDRON ->
          theme("&f", HoloMessages.THEME_TITLE_POWDER_SNOW_CAULDRON, HoloMessages.THEME_DETAIL_SNOW_LEVEL, HoloMessages.THEME_EMPTY_EMPTY);
      case JUKEBOX ->
          theme("&d", HoloMessages.THEME_TITLE_JUKEBOX, HoloMessages.THEME_DETAIL_DISC_PLAYER, HoloMessages.THEME_EMPTY_NO_DISC);
      case BREWING_STAND ->
          theme("&d", HoloMessages.THEME_TITLE_BREWING_STAND, HoloMessages.THEME_DETAIL_BREWING, HoloMessages.THEME_EMPTY_NO_POTIONS);
      case CHISELED_BOOKSHELF ->
          theme("&6", HoloMessages.THEME_TITLE_CHISELED_BOOKSHELF, HoloMessages.THEME_DETAIL_BOOK_SLOTS, HoloMessages.THEME_EMPTY_NO_BOOKS);
      default ->
          fallbackTheme(type);
    };
  }

  public static ContainerPreviewTheme mobileInventory(Material type, int size) {
    if (type == Material.HOPPER_MINECART) {
      return theme("&8", HoloMessages.THEME_TITLE_HOPPER_MINECART, HoloMessages.THEME_DETAIL_MOBILE_TRANSFER, HoloMessages.THEME_EMPTY_QUEUE);
    }
    if (type == Material.CHEST_MINECART) {
      return theme("&6", HoloMessages.THEME_TITLE_CHEST_MINECART, HoloMessages.THEME_DETAIL_MOBILE_STORAGE, HoloMessages.THEME_EMPTY_STORED_ITEMS);
    }
    String readable = toReadableName(type.name());
    return new ContainerPreviewTheme(
        "&7",
        text(HoloMessages.THEME_TITLE_MOBILE, MessageArgs.builder().untrusted("name", readable).build()),
        text(HoloMessages.THEME_DETAIL_MOBILE_SLOTS, MessageArgs.builder().untrusted("size", size).build()),
        text(HoloMessages.THEME_EMPTY_STORED_ITEMS),
        0x99222222,
        0xBB111111
    );
  }

  public ContainerPreviewTheme withText(String header, String detail, String empty) {
    return new ContainerPreviewTheme(trimColorCode, header, detail, empty, panelColor, slotColor);
  }

  private static ContainerPreviewTheme theme(String trim, TextKey header, TextKey detail, TextKey empty) {
    return new ContainerPreviewTheme(trim, text(header), text(detail), text(empty), 0x99222222, 0xBB111111);
  }

  private static ContainerPreviewTheme shulkerTheme(Material type) {
    String colorName = type == Material.SHULKER_BOX
        ? "PURPLE"
        : type.name().replace("_SHULKER_BOX", "");
    String code = legacyColorCode(colorName);
    String readableColor = toReadableColor(colorName);
    String header = code + text(
        HoloMessages.THEME_TITLE_SHULKER,
        MessageArgs.builder().untrusted("color", readableColor).build()
    );
    int panel = shulkerPanelColor(colorName);
    int slot = shulkerSlotColor(colorName);
    return new ContainerPreviewTheme(
        code,
        header,
        text(HoloMessages.THEME_DETAIL_PORTABLE_STORAGE),
        text(HoloMessages.THEME_EMPTY_BOX),
        panel,
        slot
    );
  }

  private static ContainerPreviewTheme copperChestTheme(Material type) {
    String readable = toReadableName(type.name());
    return new ContainerPreviewTheme(
        "&6",
        text(HoloMessages.THEME_TITLE_COPPER_CHEST, MessageArgs.builder().untrusted("name", readable).build()),
        text(HoloMessages.THEME_DETAIL_COPPER_STORAGE),
        text(HoloMessages.THEME_EMPTY_STORED_ITEMS),
        0x99332118,
        0xBB1A120E
    );
  }

  private static ContainerPreviewTheme fallbackTheme(Material type) {
    String name = type.name();
    if (name.endsWith("_SHELF")) {
      return new ContainerPreviewTheme(
          "&6",
          text(HoloMessages.THEME_TITLE_SHELF, MessageArgs.builder().untrusted("name", toReadableName(name)).build()),
          text(HoloMessages.THEME_DETAIL_SHELF),
          text(HoloMessages.THEME_EMPTY_SHELF),
          0x99222222,
          0xBB111111
      );
    }
    return theme("&6", HoloMessages.THEME_TITLE_CONTAINER, HoloMessages.THEME_DETAIL_INVENTORY, HoloMessages.THEME_EMPTY_NO_ITEMS);
  }

  private static String text(TextKey key) {
    return HoloLocalization.globalText(key);
  }

  private static String text(TextKey key, MessageArgs arguments) {
    return HoloLocalization.globalText(key, arguments);
  }

  public static boolean isCopperChest(Material type) {
    return type != null && type.name().endsWith("COPPER_CHEST");
  }

  private static String legacyColorCode(String colorName) {
    return switch (colorName) {
      case "WHITE" -> "&f";
      case "ORANGE" -> "&6";
      case "MAGENTA" -> "&d";
      case "LIGHT_BLUE" -> "&b";
      case "YELLOW" -> "&e";
      case "LIME" -> "&a";
      case "PINK" -> "&d";
      case "GRAY" -> "&8";
      case "LIGHT_GRAY" -> "&7";
      case "CYAN" -> "&3";
      case "PURPLE" -> "&5";
      case "BLUE" -> "&9";
      case "BROWN" -> "&6";
      case "GREEN" -> "&2";
      case "RED" -> "&c";
      case "BLACK" -> "&0";
      default -> "&d";
    };
  }

  private static String toReadableColor(String colorName) {
    String normalized = colorName.toLowerCase(Locale.ENGLISH);
    String readable = Arrays.stream(normalized.split("_"))
        .map(s -> s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1))
        .collect(Collectors.joining(" "));
    return readable;
  }

  public static String toReadableName(String materialName) {
    String normalized = materialName.toLowerCase(Locale.ENGLISH);
    return Arrays.stream(normalized.split("_"))
        .map(s -> s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1))
        .collect(Collectors.joining(" "));
  }

  private static int shulkerPanelColor(String colorName) {
    return switch (colorName) {
      case "WHITE" -> 0x99EEEEEE;
      case "ORANGE" -> 0x99B85F14;
      case "MAGENTA" -> 0x998A2FA8;
      case "LIGHT_BLUE" -> 0x993A7FAE;
      case "YELLOW" -> 0x99B8A21A;
      case "LIME" -> 0x99659B22;
      case "PINK" -> 0x99B85C84;
      case "GRAY" -> 0x99484848;
      case "LIGHT_GRAY" -> 0x99737373;
      case "CYAN" -> 0x992D7F86;
      case "PURPLE" -> 0x9958248A;
      case "BLUE" -> 0x992E3A8C;
      case "BROWN" -> 0x99683F24;
      case "GREEN" -> 0x993C6B28;
      case "RED" -> 0x998E2C25;
      case "BLACK" -> 0x99202020;
      default -> 0x9958248A;
    };
  }

  private static int shulkerSlotColor(String colorName) {
    return switch (colorName) {
      case "WHITE" -> 0xBBD8D8D8;
      case "ORANGE" -> 0xBB7C3D0D;
      case "MAGENTA" -> 0xBB5D1F72;
      case "LIGHT_BLUE" -> 0xBB245B82;
      case "YELLOW" -> 0xBB7C6D10;
      case "LIME" -> 0xBB436A16;
      case "PINK" -> 0xBB843F5C;
      case "GRAY" -> 0xBB2E2E2E;
      case "LIGHT_GRAY" -> 0xBB555555;
      case "CYAN" -> 0xBB1E585D;
      case "PURPLE" -> 0xBB3D185F;
      case "BLUE" -> 0xBB202865;
      case "BROWN" -> 0xBB472B18;
      case "GREEN" -> 0xBB294A1C;
      case "RED" -> 0xBB651F1A;
      case "BLACK" -> 0xBB111111;
      default -> 0xBB3D185F;
    };
  }
}
