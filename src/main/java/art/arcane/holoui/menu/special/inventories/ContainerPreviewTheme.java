package art.arcane.holoui.menu.special.inventories;

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
          theme("&6", "&6&lChest", "&7Storage grid", "&8No stored items");
      case TRAPPED_CHEST ->
          theme("&c", "&c&lTrapped Chest", "&7Redstone-linked storage", "&8No stored items");
      case ENDER_CHEST ->
          theme("&5", "&5&lEnder Chest", "&7Personal void storage", "&8No stored items");
      case BARREL ->
          theme("&e", "&e&lBarrel", "&7Compact storage", "&8No stored items");
      case DISPENSER ->
          theme("&7", "&7&lDispenser", "&7Powered launcher matrix", "&8No launch items");
      case DROPPER ->
          theme("&8", "&8&lDropper", "&7Powered dropper matrix", "&8No drop items");
      case HOPPER ->
          theme("&8", "&8&lHopper", "&7Transfer queue", "&8Queue empty");
      case FURNACE ->
          theme("&6", "&6&lFurnace", "&7Smelting chamber", "&8No active recipe");
      case SMOKER ->
          theme("&e", "&e&lSmoker", "&7Food cooking chamber", "&8No active recipe");
      case BLAST_FURNACE ->
          theme("&b", "&b&lBlast Furnace", "&7Ore processing chamber", "&8No active recipe");
      case BEEHIVE ->
          theme("&e", "&e&lBeehive", "&7Honey and bee occupancy", "&8No bees inside");
      case BEE_NEST ->
          theme("&e", "&e&lBee Nest", "&7Honey and bee occupancy", "&8No bees inside");
      case CAULDRON ->
          theme("&7", "&7&lCauldron", "&7Empty vessel", "&8No fluid");
      case WATER_CAULDRON ->
          theme("&9", "&9&lWater Cauldron", "&7Water level", "&8Empty");
      case LAVA_CAULDRON ->
          theme("&6", "&6&lLava Cauldron", "&7Lava level", "&8Empty");
      case POWDER_SNOW_CAULDRON ->
          theme("&f", "&f&lPowder Snow Cauldron", "&7Powder snow level", "&8Empty");
      case JUKEBOX ->
          theme("&d", "&d&lJukebox", "&7Disc player", "&8No disc");
      case BREWING_STAND ->
          theme("&d", "&d&lBrewing Stand", "&7Potion brewing", "&8No potions");
      case CHISELED_BOOKSHELF ->
          theme("&6", "&6&lChiseled Bookshelf", "&7Six book slots", "&8No books");
      default ->
          fallbackTheme(type);
    };
  }

  public static ContainerPreviewTheme mobileInventory(Material type, int size) {
    if (type == Material.HOPPER_MINECART) {
      return theme("&8", "&8&lHopper Minecart", "&7Mobile transfer queue", "&8Queue empty");
    }
    if (type == Material.CHEST_MINECART) {
      return theme("&6", "&6&lChest Minecart", "&7Mobile storage grid", "&8No stored items");
    }
    String readable = toReadableName(type.name());
    return theme("&7", "&7&l" + readable, "&7" + size + " mobile slots", "&8No stored items");
  }

  public ContainerPreviewTheme withText(String header, String detail, String empty) {
    return new ContainerPreviewTheme(trimColorCode, header, detail, empty, panelColor, slotColor);
  }

  private static ContainerPreviewTheme theme(String trim, String header, String detail, String empty) {
    return new ContainerPreviewTheme(trim, header, detail, empty, 0x99222222, 0xBB111111);
  }

  private static ContainerPreviewTheme shulkerTheme(Material type) {
    String colorName = type == Material.SHULKER_BOX
        ? "PURPLE"
        : type.name().replace("_SHULKER_BOX", "");
    String code = legacyColorCode(colorName);
    String readableColor = toReadableColor(colorName);
    String header = code + "&l" + readableColor + " Shulker";
    int panel = shulkerPanelColor(colorName);
    int slot = shulkerSlotColor(colorName);
    return new ContainerPreviewTheme(code, header, "&7Portable storage", "&8Box empty", panel, slot);
  }

  private static ContainerPreviewTheme copperChestTheme(Material type) {
    String readable = toReadableName(type.name());
    return new ContainerPreviewTheme("&6", "&6&l" + readable, "&7Copper storage grid", "&8No stored items", 0x99332118, 0xBB1A120E);
  }

  private static ContainerPreviewTheme fallbackTheme(Material type) {
    String name = type.name();
    if (name.endsWith("_SHELF")) {
      return theme("&6", "&6&l" + toReadableName(name), "&7Shelf state", "&8Empty shelf");
    }
    return theme("&6", "&6&lContainer", "&7Inventory", "&8No items");
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
