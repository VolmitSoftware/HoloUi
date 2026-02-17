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
package art.arcane.holoui.config;

import art.arcane.holoui.HoloUI;
import art.arcane.volmlib.util.bukkit.json.BukkitJson;
import art.arcane.volmlib.util.io.FolderWatcher;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import lombok.Getter;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.imaging.ImageFormat;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Sound;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class ConfigManager {

    private final Map<String, MenuDefinitionData> menuRegistry = new ConcurrentHashMap<>();

    private final File menuDir, imageDir;
    private final FolderWatcher menuDefinitionFolder;

    @Getter
    private final HuiSettings settings;

    public ConfigManager(File configDir) {
        this.imageDir = new File(configDir, "images");
        if (!imageDir.exists())
            imageDir.mkdirs();
        this.menuDir = new File(configDir, "menus");
        if (!menuDir.exists())
            menuDir.mkdirs();

        menuDefinitionFolder = new FolderWatcher(menuDir);
        settings = new HuiSettings(configDir);

        menuDefinitionFolder.getWatchers().keySet().forEach(f -> {
            if (f.getPath().contains("menus")) {
                registerMenu(f);
            }
        });

        SchedulerUtils.scheduleSyncTask(HoloUI.INSTANCE, 5L, () -> {
            if (menuDefinitionFolder.checkModifiedFast()) {
                menuDefinitionFolder.getChanged().forEach(f -> {
                    String name = FilenameUtils.getBaseName(f.getName());
                    Optional<MenuDefinitionData> data = loadConfig(name, f);
                    data.ifPresent(d -> {
                        HoloUI.INSTANCE.getSessionManager().destroyAllType(name, p -> {
                            SchedulerUtils.runEntity(HoloUI.INSTANCE, p, () -> {
                                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§2Config \"" + name + "\" reloaded."));
                                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, .5F, 1);
                            });
                        });
                        menuRegistry.put(name, d);
                        HoloUI.log(Level.INFO, "Menu config \"%s\" has been changed and re-registered.", name);
                    });
                });
            }
            settings.update();
        }, true);
        SchedulerUtils.scheduleSyncTask(HoloUI.INSTANCE, 20L, () -> {
            if (menuDefinitionFolder.checkModified()) {
                menuDefinitionFolder.getCreated().forEach(this::registerMenu);
                menuDefinitionFolder.getDeleted().forEach(this::unregisterMenu);
            }
        }, true);
    }

    private void registerMenu(File f) {
        String name = FilenameUtils.getBaseName(f.getName());
        Optional<MenuDefinitionData> data = loadConfig(name, f);
        data.ifPresent(d -> {
            menuRegistry.put(name, d);
            HoloUI.log(Level.INFO, "New menu config \"%s\" detected and registered.", name);
        });
    }

    private void unregisterMenu(File f) {
        String name = FilenameUtils.getBaseName(f.getName());
        if (menuRegistry.containsKey(name)) {
            HoloUI.INSTANCE.getSessionManager().destroyAllType(name, p -> {});
            menuRegistry.remove(name);
            HoloUI.log(Level.INFO, "Menu config \"%s\" has been deleted and unregistered.", name);
        }
    }

    public void shutdown() {
        settings.write();
    }

    public Set<String> keys() {
        return menuRegistry.keySet();
    }

    public Optional<MenuDefinitionData> get(String key) {
        return exists(key) ? Optional.of(menuRegistry.get(key)) : Optional.empty();
    }

    public boolean exists(String key) {
        return menuRegistry.containsKey(key);
    }

    public Pair<ImageFormat, BufferedImage> getImage(String relative) throws IOException {
        File f = new File(imageDir, relative);
        if (!f.exists() || f.isDirectory())
            throw new FileNotFoundException();
        ImageFormat format = Imaging.guessFormat(f);
        return Pair.of(format, Imaging.getBufferedImage(f));
    }

    public List<BufferedImage> getImages(String relative) throws IOException {
        File f = new File(imageDir, relative);
        if (!f.exists() || f.isDirectory())
            throw new FileNotFoundException();
        return Imaging.getAllBufferedImages(f);
    }

    private Optional<MenuDefinitionData> loadConfig(String menuName, File f) {
        try (FileReader reader = new FileReader(f)) {
            if (FileUtils.sizeOf(f) == 0) {
                HoloUI.log(Level.WARNING, "Menu config \"%s.json\" is empty, ignoring.", menuName);
                return Optional.empty();
            }

            MenuDefinitionData data = BukkitJson.parse(reader, MenuDefinitionData.class);
            if (data != null) data.setId(menuName);
            else HoloUI.log(Level.WARNING, "An unknown error occurred while parsing menu config \"%s.json\"! Skipping.", menuName);
            return Optional.ofNullable(data);
        } catch (Throwable ex) {
            HoloUI.logExceptionStack(false, ex, "An error occurred while parsing menu config \"%s.json\":", menuName);
        }
        return Optional.empty();
    }
}
