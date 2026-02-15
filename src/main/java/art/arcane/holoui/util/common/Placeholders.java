package art.arcane.holoui.util.common;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class Placeholders {
    private static final boolean inactive = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null;

    public static String setPlaceholders(Player player, String text) {
        if (inactive) return text;
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
