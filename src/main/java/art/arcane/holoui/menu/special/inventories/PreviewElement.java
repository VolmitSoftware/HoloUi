package art.arcane.holoui.menu.special.inventories;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.Inventory;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public sealed interface PreviewElement
    permits PreviewElement.Panel, PreviewElement.Cell, PreviewElement.Slot, PreviewElement.Label {

  int x();

  int y();

  int z();

  record Panel(int x, int y, int z, int width, int height, int color) implements PreviewElement {
  }

  record Cell(int x, int y, int z, int size, IntSupplier color) implements PreviewElement {
  }

  record Slot(int x, int y, int z, int size, int wellColor, Inventory inventory, int slot) implements PreviewElement {
  }

  record Label(int x, int y, int z, Supplier<Component> text, int backgroundColor) implements PreviewElement {
  }
}
