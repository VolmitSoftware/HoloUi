package art.arcane.holoui.board;

import art.arcane.holoui.api.HoloClickTrigger;
import art.arcane.holoui.menu.components.ClickableComponent;

import java.util.Objects;

public record BoardClickTarget(BoardViewSession view, ClickableComponent<?> component, double distance) {
  public BoardClickTarget {
    view = Objects.requireNonNull(view, "view");
    component = Objects.requireNonNull(component, "component");
    if (!Double.isFinite(distance) || distance < 0.0D) {
      throw new IllegalArgumentException("distance must be finite and non-negative");
    }
  }

  public void dispatch(HoloClickTrigger trigger) {
    view.dispatchClick(component, trigger);
  }
}
