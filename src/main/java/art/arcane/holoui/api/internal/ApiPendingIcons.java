package art.arcane.holoui.api.internal;

import art.arcane.holoui.api.HoloIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class ApiPendingIcons {
  private final Map<String, HoloIcon> staged = new ConcurrentHashMap<>();

  private volatile boolean dirty;

  public void stage(String componentId, HoloIcon icon) {
    staged.put(componentId, icon);
    dirty = true;
  }

  public boolean dirty() {
    return dirty;
  }

  public int drain(BiConsumer<String, HoloIcon> sink) {
    if (!dirty) {
      return 0;
    }

    dirty = false;
    List<String> keys = new ArrayList<>(staged.keySet());
    int applied = 0;

    try {
      for (String key : keys) {
        HoloIcon icon = staged.get(key);
        if (icon == null) {
          continue;
        }

        sink.accept(key, icon);
        staged.remove(key, icon);
        applied++;
      }
    } finally {
      if (!staged.isEmpty()) {
        dirty = true;
      }
    }

    return applied;
  }

  public void discard() {
    staged.clear();
    dirty = false;
  }
}
