package art.arcane.holoui.api;

import org.bukkit.event.block.Action;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HoloClickTriggerTest {

  @Test
  public void bukkitClickAndSneakStateMapToExactTriggers() {
    assertEquals(HoloClickTrigger.LEFT_CLICK,
        HoloClickTrigger.fromInteraction(Action.LEFT_CLICK_AIR, false));
    assertEquals(HoloClickTrigger.SHIFT_LEFT_CLICK,
        HoloClickTrigger.fromInteraction(Action.LEFT_CLICK_BLOCK, true));
    assertEquals(HoloClickTrigger.RIGHT_CLICK,
        HoloClickTrigger.fromInteraction(Action.RIGHT_CLICK_BLOCK, false));
    assertEquals(HoloClickTrigger.SHIFT_RIGHT_CLICK,
        HoloClickTrigger.fromInteraction(Action.RIGHT_CLICK_AIR, true));
    assertThrows(IllegalArgumentException.class,
        () -> HoloClickTrigger.fromInteraction(Action.PHYSICAL, false));
  }

  @Test
  public void anyMatchesEveryInteractionAndExactTriggersMatchOnlyThemselves() {
    for (HoloClickTrigger trigger : HoloClickTrigger.values()) {
      assertTrue(HoloClickTrigger.ANY.matches(trigger));
    }
    assertTrue(HoloClickTrigger.RIGHT_CLICK.matches(HoloClickTrigger.RIGHT_CLICK));
    assertFalse(HoloClickTrigger.RIGHT_CLICK.matches(HoloClickTrigger.SHIFT_RIGHT_CLICK));
    assertFalse(HoloClickTrigger.LEFT_CLICK.matches(HoloClickTrigger.RIGHT_CLICK));
  }
}
