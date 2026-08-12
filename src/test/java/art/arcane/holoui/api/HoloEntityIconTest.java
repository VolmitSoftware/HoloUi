package art.arcane.holoui.api;

import org.bukkit.entity.EntityType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class HoloEntityIconTest {

  @Test
  public void acceptsSpawnableLivingEntityDimensions() {
    HoloIcon.Entity icon = (HoloIcon.Entity) HoloIcon.entity(EntityType.PARROT, 0.5F, 0.9F);

    assertEquals(EntityType.PARROT, icon.entityType());
    assertEquals(0.5F, icon.width(), 0F);
    assertEquals(0.9F, icon.height(), 0F);
  }

  @Test
  public void rejectsUnsafeEntityTypesAndDimensions() {
    assertThrows(IllegalArgumentException.class, () -> HoloIcon.entity(EntityType.PLAYER, 1F, 2F));
    assertThrows(IllegalArgumentException.class, () -> HoloIcon.entity(EntityType.ITEM, 1F, 1F));
    assertThrows(IllegalArgumentException.class, () -> HoloIcon.entity(EntityType.PARROT, 0F, 1F));
    assertThrows(IllegalArgumentException.class, () -> HoloIcon.entity(EntityType.PARROT, 1F, Float.NaN));
  }
}
