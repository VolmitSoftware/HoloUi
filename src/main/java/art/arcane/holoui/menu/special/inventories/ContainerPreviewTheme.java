package art.arcane.holoui.menu.special.inventories;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import org.bukkit.Material;
import org.bukkit.block.Container;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public record ContainerPreviewTheme(
        String trimColorCode,
        String panelColorCode,
        String slotColorCode,
        String headerText,
        String arrowText,
        Style progressStyle
) {

    public static ContainerPreviewTheme resolve(Container container) {
        Material type = container.getType();
        if (type.name().endsWith("_SHULKER_BOX") || type == Material.SHULKER_BOX) {
            return shulkerTheme(type);
        }
        return switch (type) {
            case CHEST -> new ContainerPreviewTheme("&6", "&8", "&0", "&6[ Chest ]", "&6>>", Style.style(NamedTextColor.WHITE));
            case TRAPPED_CHEST -> new ContainerPreviewTheme("&c", "&8", "&0", "&c[ Trapped Chest ]", "&c>>", Style.style(NamedTextColor.RED));
            case BARREL -> new ContainerPreviewTheme("&6", "&8", "&0", "&6[ Barrel ]", "&6>>", Style.style(NamedTextColor.GOLD));
            case DISPENSER -> new ContainerPreviewTheme("&7", "&8", "&0", "&7[ Dispenser ]", "&7>>", Style.style(NamedTextColor.WHITE));
            case DROPPER -> new ContainerPreviewTheme("&8", "&7", "&0", "&8[ Dropper ]", "&8>>", Style.style(NamedTextColor.GRAY));
            case HOPPER -> new ContainerPreviewTheme("&8", "&7", "&0", "&8[ Hopper ]", "&8>>", Style.style(NamedTextColor.GRAY));
            case FURNACE -> new ContainerPreviewTheme("&6", "&8", "&0", "&6[ Furnace ]", "&6>>", Style.style(NamedTextColor.WHITE));
            case SMOKER -> new ContainerPreviewTheme("&e", "&6", "&0", "&e[ Smoker ]", "&e>>", Style.style(NamedTextColor.GOLD));
            case BLAST_FURNACE -> new ContainerPreviewTheme("&b", "&3", "&0", "&b[ Blast Furnace ]", "&b>>", Style.style(NamedTextColor.AQUA));
            default -> new ContainerPreviewTheme("&6", "&8", "&0", "&6[ Container ]", "&6>>", Style.style(NamedTextColor.WHITE));
        };
    }

    private static ContainerPreviewTheme shulkerTheme(Material type) {
        String colorName = type == Material.SHULKER_BOX
                ? "PURPLE"
                : type.name().replace("_SHULKER_BOX", "");
        String code = legacyColorCode(colorName);
        String readableColor = toReadableColor(colorName);
        String header = code + "[ " + readableColor + " Shulker ]";
        String arrow = code + ">>";
        return new ContainerPreviewTheme(code, "&8", "&0", header, arrow, Style.style(NamedTextColor.LIGHT_PURPLE));
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
}
