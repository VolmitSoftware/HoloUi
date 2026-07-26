package art.arcane.holoui.api;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

public sealed interface HoloIcon permits HoloIcon.Text, HoloIcon.Item, HoloIcon.Image, HoloIcon.AnimatedImage {

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

  static HoloIcon text(String miniMessage) {
    return new Text(miniMessage);
  }

  static HoloIcon item(ItemStack stack) {
    return new Item(stack);
  }

  static HoloIcon image(String relativePath) {
    return new Image(relativePath);
  }

  static HoloIcon animatedImage(List<String> relativePaths, int tickSpeed) {
    return new AnimatedImage(relativePaths, tickSpeed);
  }
}
