package art.arcane.holoui.api;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

public sealed interface HoloIcon permits HoloIcon.Text, HoloIcon.Item, HoloIcon.Block, HoloIcon.Image, HoloIcon.AnimatedImage, HoloIcon.Entity {

  record Text(String miniMessage) implements HoloIcon {
    public Text {
      miniMessage = HoloText.sanitizeMarkup(miniMessage);
    }
  }

  record Item(ItemStack stack) implements HoloIcon {
    public Item {
      Objects.requireNonNull(stack, "stack");
      stack = stack.clone();
    }

    @Override
    public ItemStack stack() {
      return stack.clone();
    }
  }

  record Block(Material material) implements HoloIcon {
    public Block {
      Objects.requireNonNull(material, "material");
      if (Bukkit.getServer() != null && !material.isBlock()) {
        throw new IllegalArgumentException("material must be a block");
      }
    }
  }

  record Image(String relativePath) implements HoloIcon {
    public Image {
      relativePath = HoloText.sanitizePath(relativePath);
    }
  }

  record AnimatedImage(List<String> relativePaths, int tickSpeed) implements HoloIcon {
    public AnimatedImage {
      relativePaths = HoloText.sanitizePaths(relativePaths);
      tickSpeed = Math.max(1, tickSpeed);
    }
  }

  record Entity(EntityType entityType, float width, float height) implements HoloIcon {
    public Entity {
      Objects.requireNonNull(entityType, "entityType");
      if (!entityType.isSpawnable() || !entityType.isAlive()) {
        throw new IllegalArgumentException("entityType must be a spawnable living entity");
      }
      if (!Float.isFinite(width) || width <= 0F || width > 64F) {
        throw new IllegalArgumentException("width must be finite, greater than 0, and at most 64");
      }
      if (!Float.isFinite(height) || height <= 0F || height > 64F) {
        throw new IllegalArgumentException("height must be finite, greater than 0, and at most 64");
      }
    }
  }

  static HoloIcon text(String miniMessage) {
    return new Text(miniMessage);
  }

  static HoloIcon item(ItemStack stack) {
    return new Item(stack);
  }

  static HoloIcon block(Material material) {
    return new Block(material);
  }

  static HoloIcon image(String relativePath) {
    return new Image(relativePath);
  }

  static HoloIcon animatedImage(List<String> relativePaths, int tickSpeed) {
    return new AnimatedImage(relativePaths, tickSpeed);
  }

  static HoloIcon entity(EntityType entityType, float width, float height) {
    return new Entity(entityType, width, height);
  }
}
