package art.arcane.holoui;

import art.arcane.holoui.board.BoardDefinition;
import art.arcane.holoui.board.BoardTransform;

import java.util.Objects;

final class BoardEditSession {
  private final BoardDefinition base;
  private BoardDefinition staged;
  private BoardTransform effectiveTransform;
  private boolean saving;

  BoardEditSession(BoardDefinition base, BoardTransform effectiveTransform) {
    this.base = Objects.requireNonNull(base, "base");
    this.staged = base;
    this.effectiveTransform = Objects.requireNonNull(effectiveTransform, "effectiveTransform");
  }

  synchronized String id() {
    return base.id();
  }

  synchronized long expectedRevision() {
    return base.revision();
  }

  synchronized Snapshot snapshot() {
    return new Snapshot(staged, effectiveTransform);
  }

  synchronized Snapshot synchronizeEffective(BoardTransform effective) {
    if (saving || staged.follow().targetPlayerUuid() == null) {
      return snapshot();
    }
    effectiveTransform = Objects.requireNonNull(effective, "effective");
    return snapshot();
  }

  synchronized Snapshot stage(Snapshot expected, BoardDefinition changed,
                              BoardTransform changedEffectiveTransform) {
    if (saving) {
      return null;
    }
    Snapshot requiredExpected = Objects.requireNonNull(expected, "expected");
    if (!staged.equals(requiredExpected.definition())
        || !effectiveTransform.equals(requiredExpected.effectiveTransform())) {
      throw new IllegalStateException("staged board changed while the operation was being prepared");
    }
    BoardDefinition requiredChanged = Objects.requireNonNull(changed, "changed");
    BoardTransform requiredEffective = Objects.requireNonNull(
        changedEffectiveTransform, "changedEffectiveTransform");
    if (!requiredChanged.id().equals(base.id()) || !requiredChanged.uuid().equals(base.uuid())
        || requiredChanged.revision() != base.revision()) {
      throw new IllegalArgumentException("staged edits cannot change board identity or revision");
    }
    staged = requiredChanged;
    effectiveTransform = requiredEffective;
    return new Snapshot(staged, effectiveTransform);
  }

  synchronized BoardDefinition beginSave() {
    if (saving) {
      return null;
    }
    saving = true;
    return staged;
  }

  synchronized BoardDefinition cancel() {
    if (saving) {
      return null;
    }
    saving = true;
    return staged;
  }

  synchronized void retrySave() {
    saving = false;
  }

  record Snapshot(BoardDefinition definition, BoardTransform effectiveTransform) {
    Snapshot {
      definition = Objects.requireNonNull(definition, "definition");
      effectiveTransform = Objects.requireNonNull(effectiveTransform, "effectiveTransform");
    }
  }
}
