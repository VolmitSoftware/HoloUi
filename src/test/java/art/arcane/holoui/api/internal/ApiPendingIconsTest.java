package art.arcane.holoui.api.internal;

import art.arcane.holoui.api.HoloIcon;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ApiPendingIconsTest {

  @Test
  public void twoHundredStagesForOneComponentCoalesceIntoASingleDrainedUpdate() {
    ApiPendingIcons pending = new ApiPendingIcons();

    for (int index = 0; index < 200; index++) {
      pending.stage("stock", HoloIcon.text("In stock: " + index));
    }

    Map<String, HoloIcon> applied = new LinkedHashMap<>();
    assertEquals(1, pending.drain(applied::put));
    assertEquals(1, applied.size());
    assertEquals(new HoloIcon.Text("In stock: 199"), applied.get("stock"));
  }

  @Test
  public void drainIsANoOpUntilSomethingIsStagedAndDoesNotRepeatWork() {
    ApiPendingIcons pending = new ApiPendingIcons();
    List<String> applied = new ArrayList<>();

    assertFalse(pending.dirty());
    assertEquals(0, pending.drain((id, icon) -> applied.add(id)));

    pending.stage("a", HoloIcon.text("a"));
    pending.stage("b", HoloIcon.text("b"));
    assertTrue(pending.dirty());
    assertEquals(2, pending.drain((id, icon) -> applied.add(id)));
    assertEquals(2, applied.size());

    assertFalse(pending.dirty());
    assertEquals(0, pending.drain((id, icon) -> applied.add(id)));
    assertEquals(2, applied.size());
  }

  @Test
  public void aSinkThatThrowsStrandsNothingAndLeavesTheRemainderArmedForTheNextDrain() {
    ApiPendingIcons pending = new ApiPendingIcons();
    pending.stage("a", HoloIcon.text("a"));
    pending.stage("b", HoloIcon.text("b"));

    List<String> applied = new ArrayList<>();

    try {
      pending.drain((id, icon) -> {
        applied.add(id);
        throw new StackOverflowError("hostile sink");
      });
      fail("expected the sink failure to propagate");
    } catch (StackOverflowError expected) {
      assertEquals("hostile sink", expected.getMessage());
    }

    assertEquals(1, applied.size());
    assertTrue(pending.dirty());

    Map<String, HoloIcon> recovered = new LinkedHashMap<>();
    assertEquals(2, pending.drain(recovered::put));
    assertEquals(new HoloIcon.Text("a"), recovered.get("a"));
    assertEquals(new HoloIcon.Text("b"), recovered.get("b"));
    assertFalse(pending.dirty());
  }

  @Test
  public void discardDropsEverythingStagedSoATerminatedHandleFlushesNothing() {
    ApiPendingIcons pending = new ApiPendingIcons();
    pending.stage("stock", HoloIcon.text("x"));

    pending.discard();

    List<String> applied = new ArrayList<>();
    assertFalse(pending.dirty());
    assertEquals(0, pending.drain((id, icon) -> applied.add(id)));
    assertTrue(applied.isEmpty());
  }
}
