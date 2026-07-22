package art.arcane.holoui.menu.special.inventories;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.localization.HoloMessages;
import art.arcane.holoui.menu.MenuSessionManager;
import art.arcane.volmlib.util.bukkit.Events;
import art.arcane.volmlib.util.localization.MessageArgs;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PreviewScaleService {

  private static final double STEP = 1.10D;
  private static final double MAX_FACTOR = 2.50D;
  private static final double MIN_FACTOR = 0.25D;
  private static final double HIDE_BELOW = 0.30D;
  private static final long DOUBLE_TAP_MS = 400L;
  private static final long ADJUST_IDLE_TIMEOUT_MS = 20000L;

  private static final Map<UUID, Double> factors = new ConcurrentHashMap<>();
  private static final Map<UUID, Long> lastSneakPress = new ConcurrentHashMap<>();
  private static final Map<UUID, Long> adjusting = new ConcurrentHashMap<>();
  private static volatile File storeFile;

  private PreviewScaleService() {
  }

  public static void init(HoloUI plugin) {
    storeFile = new File(plugin.getDataFolder(), "preview-scales.json");
    load();
    Events.listen(plugin, PlayerToggleSneakEvent.class, PreviewScaleService::onSneak);
    Events.listen(plugin, PlayerItemHeldEvent.class, PreviewScaleService::onScroll);
    Events.listen(plugin, PlayerQuitEvent.class, e -> {
      UUID id = e.getPlayer().getUniqueId();
      lastSneakPress.remove(id);
      if (adjusting.remove(id) != null) {
        persist();
      }
    });
  }

  public static void shutdown() {
    persist();
    lastSneakPress.clear();
    adjusting.clear();
  }

  public static float factor(Player player) {
    return factors.getOrDefault(player.getUniqueId(), 1.0D).floatValue();
  }

  public static boolean isHidden(Player player) {
    return factors.getOrDefault(player.getUniqueId(), 1.0D) < HIDE_BELOW;
  }

  private static void onSneak(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      return;
    }
    Player player = e.getPlayer();
    UUID id = player.getUniqueId();
    long now = System.currentTimeMillis();
    Long previous = lastSneakPress.put(id, now);
    if (previous == null || now - previous > DOUBLE_TAP_MS) {
      return;
    }
    lastSneakPress.remove(id);
    if (!hasPreview(player)) {
      adjusting.remove(id);
      return;
    }
    if (adjusting.remove(id) != null) {
      persist();
      actionBar(player, saveMessage(player));
    } else {
      adjusting.put(id, now);
      actionBar(player, HoloUI.INSTANCE.getLocalization().legacy(
          HoloMessages.PREVIEW_SCALE_ADJUSTING,
          MessageArgs.builder().untrusted("percent", percent(player)).build()
      ));
    }
  }

  private static void onScroll(PlayerItemHeldEvent e) {
    Player player = e.getPlayer();
    UUID id = player.getUniqueId();
    Long activity = adjusting.get(id);
    if (activity == null) {
      return;
    }
    long now = System.currentTimeMillis();
    if (now - activity > ADJUST_IDLE_TIMEOUT_MS || !hasPreview(player)) {
      adjusting.remove(id);
      persist();
      return;
    }
    if (!player.isSneaking()) {
      return;
    }
    int diff = e.getNewSlot() - e.getPreviousSlot();
    if (diff > 4) {
      diff -= 9;
    }
    if (diff < -4) {
      diff += 9;
    }
    if (diff == 0) {
      return;
    }
    e.setCancelled(true);
    adjusting.put(id, now);
    double current = factors.getOrDefault(id, 1.0D);
    double updated = Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, current * Math.pow(STEP, -diff)));
    factors.put(id, updated);
    if (updated < HIDE_BELOW) {
      actionBar(player, HoloUI.INSTANCE.getLocalization().legacy(HoloMessages.PREVIEW_SCALE_HIDDEN));
    } else {
      actionBar(player, HoloUI.INSTANCE.getLocalization().legacy(
          HoloMessages.PREVIEW_SCALE_SIZE,
          MessageArgs.builder().untrusted("percent", percent(player)).build()
      ));
    }
  }

  private static String saveMessage(Player player) {
    if (isHidden(player)) {
      return HoloUI.INSTANCE.getLocalization().legacy(HoloMessages.PREVIEW_SCALE_SAVED_HIDDEN);
    }
    return HoloUI.INSTANCE.getLocalization().legacy(
        HoloMessages.PREVIEW_SCALE_SAVED,
        MessageArgs.builder().untrusted("percent", percent(player)).build()
    );
  }

  private static int percent(Player player) {
    return (int) Math.round(factors.getOrDefault(player.getUniqueId(), 1.0D) * 100.0D);
  }

  private static boolean hasPreview(Player player) {
    MenuSessionManager manager = HoloUI.INSTANCE == null ? null : HoloUI.INSTANCE.getSessionManager();
    return manager != null && manager.hasPreviewSession(player);
  }

  private static void actionBar(Player player, String legacy) {
    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(legacy));
  }

  private static void load() {
    File file = storeFile;
    if (file == null || !file.exists()) {
      return;
    }
    try {
      String json = Files.readString(file.toPath());
      Map<String, Double> raw = new Gson().fromJson(json, new TypeToken<Map<String, Double>>() {
      }.getType());
      if (raw == null) {
        return;
      }
      raw.forEach((key, value) -> {
        if (value == null || value.isNaN() || value.isInfinite()) {
          return;
        }
        try {
          factors.put(UUID.fromString(key), Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, value)));
        } catch (IllegalArgumentException ignored) {
        }
      });
    } catch (Exception ex) {
      HoloUI.logExceptionStack(false, ex, "Failed to load preview scales.");
    }
  }

  private static synchronized void persist() {
    File file = storeFile;
    if (file == null) {
      return;
    }
    try {
      Map<String, Double> raw = new TreeMap<>();
      factors.forEach((key, value) -> {
        if (value != 1.0D) {
          raw.put(key.toString(), Math.round(value * 100.0D) / 100.0D);
        }
      });
      File parent = file.getParentFile();
      if (parent != null) {
        parent.mkdirs();
      }
      Files.writeString(file.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(raw));
    } catch (Exception ex) {
      HoloUI.logExceptionStack(false, ex, "Failed to save preview scales.");
    }
  }
}
