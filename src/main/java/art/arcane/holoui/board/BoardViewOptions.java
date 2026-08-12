package art.arcane.holoui.board;

import art.arcane.holoui.config.ConfigManager;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.Consumer;

public record BoardViewOptions(BoardDefinition definition, BoardTransform effectiveTransform,
                               Player viewer, ConfigManager menus,
                               Consumer<BoardViewSession> closeRequester) {
  public BoardViewOptions {
    definition = Objects.requireNonNull(definition, "definition");
    effectiveTransform = Objects.requireNonNull(effectiveTransform, "effectiveTransform");
    viewer = Objects.requireNonNull(viewer, "viewer");
    menus = Objects.requireNonNull(menus, "menus");
    closeRequester = Objects.requireNonNull(closeRequester, "closeRequester");
  }
}
