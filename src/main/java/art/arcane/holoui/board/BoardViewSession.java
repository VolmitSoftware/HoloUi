package art.arcane.holoui.board;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.api.HoloClickTrigger;
import art.arcane.holoui.api.internal.ApiEvents;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.enums.NavigationMode;
import art.arcane.holoui.localization.HoloMessages;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.MenuSessionOptions;
import art.arcane.holoui.menu.MenuTransform;
import art.arcane.holoui.menu.action.MenuNavigator;
import art.arcane.holoui.menu.action.NavigationRequest;
import art.arcane.holoui.menu.action.NavigationResult;
import art.arcane.holoui.menu.components.ClickableComponent;
import art.arcane.volmlib.util.localization.MessageArgs;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class BoardViewSession implements MenuNavigator {
  private final Player viewer;
  private final Function<String, MenuDefinitionData> menuLookup;
  private final Consumer<BoardViewSession> closeRequester;
  private final Deque<String> history = new ArrayDeque<>();

  private BoardDefinition definition;
  private BoardTransform effectiveTransform;
  private MenuSession session;
  private String currentMenuId;
  private boolean closed;

  public BoardViewSession(BoardViewOptions options) {
    this(
        Objects.requireNonNull(options, "options").definition(),
        options.effectiveTransform(),
        options.viewer(),
        menuId -> options.menus().get(menuId).orElse(null),
        options.closeRequester()
    );
  }

  BoardViewSession(BoardDefinition definition, BoardTransform effectiveTransform, Player viewer,
                   Function<String, MenuDefinitionData> menuLookup,
                   Consumer<BoardViewSession> closeRequester) {
    this.definition = Objects.requireNonNull(definition, "definition");
    this.effectiveTransform = Objects.requireNonNull(effectiveTransform, "effectiveTransform");
    this.viewer = Objects.requireNonNull(viewer, "viewer");
    this.menuLookup = Objects.requireNonNull(menuLookup, "menuLookup");
    this.closeRequester = Objects.requireNonNull(closeRequester, "closeRequester");
  }

  public NavigationResult open() {
    return openMenu(definition.rootMenuId(), NavigationMode.REPLACE, false, true);
  }

  public void tick() {
    if (!closed && session != null) {
      session.tick();
    }
  }

  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    try {
      if (session != null) {
        session.close();
      }
    } finally {
      session = null;
      currentMenuId = null;
      history.clear();
    }
  }

  public NavigationResult update(BoardDefinition nextDefinition, BoardTransform nextEffectiveTransform) {
    if (closed) {
      return NavigationResult.DENIED;
    }
    BoardDefinition requiredDefinition = Objects.requireNonNull(nextDefinition, "nextDefinition");
    BoardTransform requiredTransform = Objects.requireNonNull(nextEffectiveTransform, "nextEffectiveTransform");
    boolean rootChanged = !definition.rootMenuId().equals(requiredDefinition.rootMenuId());
    boolean transformChanged = !effectiveTransform.equals(requiredTransform);
    boolean definitionChanged = !definition.equals(requiredDefinition);
    if (!rootChanged && !transformChanged && !definitionChanged) {
      return NavigationResult.APPLIED;
    }
    if (rootChanged) {
      BoardDefinition previousDefinition = definition;
      BoardTransform previousTransform = effectiveTransform;
      definition = requiredDefinition;
      effectiveTransform = requiredTransform;
      NavigationResult result = openMenu(requiredDefinition.rootMenuId(), NavigationMode.REPLACE, false, true);
      if (result != NavigationResult.APPLIED) {
        definition = previousDefinition;
        effectiveTransform = previousTransform;
        close();
      } else {
        history.clear();
      }
      return result;
    }
    definition = requiredDefinition;
    effectiveTransform = requiredTransform;
    if (transformChanged) {
      applyTransform();
    }
    return NavigationResult.APPLIED;
  }

  public BoardDefinition definition() {
    return definition;
  }

  public Player viewer() {
    return viewer;
  }

  public MenuSession session() {
    return session;
  }

  public String currentMenuId() {
    return currentMenuId;
  }

  public BoardTransform effectiveTransform() {
    return effectiveTransform;
  }

  public boolean closed() {
    return closed;
  }

  public boolean reloadCurrent() {
    return !closed && currentMenuId != null
        && openMenu(currentMenuId, NavigationMode.REPLACE, false, isAtRoot(currentMenuId)) == NavigationResult.APPLIED;
  }

  public boolean returnHome() {
    if (closed || definition.rootMenuId().equals(currentMenuId)) {
      return false;
    }
    history.clear();
    return openMenu(definition.rootMenuId(), NavigationMode.HOME, false, true) == NavigationResult.APPLIED;
  }

  public void dispatchClick(ClickableComponent<?> component, HoloClickTrigger trigger) {
    if (closed || session == null || component == null || !component.isOpen()) {
      return;
    }
    if (ApiEvents.fireClick(viewer, currentMenuId, component.getId(), null, trigger)) {
      component.onClick(trigger);
    }
  }

  @Override
  public NavigationResult navigate(NavigationRequest request) {
    if (closed) {
      return NavigationResult.DENIED;
    }
    NavigationRequest requiredRequest = Objects.requireNonNull(request, "request");
    if (requiredRequest.mode() == NavigationMode.CLOSE) {
      closeRequester.accept(this);
      return NavigationResult.APPLIED;
    }

    String target = switch (requiredRequest.mode()) {
      case PUSH, REPLACE -> requiredRequest.target();
      case BACK -> history.peekFirst();
      case HOME -> definition.rootMenuId();
      case CLOSE -> null;
    };
    if (target == null) {
      return NavigationResult.NO_HISTORY;
    }
    return openMenu(target, requiredRequest.mode(), true, target.equals(definition.rootMenuId()));
  }

  private NavigationResult openMenu(String menuId, NavigationMode mode, boolean notifyFailure,
                                    boolean boardRootAdmission) {
    MenuDefinitionData menu = menuLookup.apply(menuId);
    if (menu == null) {
      if (notifyFailure) {
        viewer.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(
            HoloMessages.MENU_UNAVAILABLE,
            MessageArgs.builder().untrusted("menu", menuId).build()
        ));
      }
      return NavigationResult.NOT_FOUND;
    }
    if (!boardRootAdmission && !viewer.hasPermission("holoui.open." + menu.getId())) {
      if (notifyFailure) {
        viewer.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(
            HoloMessages.MENU_PERMISSION_DENIED,
            MessageArgs.builder().untrusted("menu", menu.getId()).build()
        ));
      }
      return NavigationResult.DENIED;
    }
    if (!ApiEvents.fireOpen(viewer, menu.getId(), null)) {
      return NavigationResult.DENIED;
    }
    MenuTransform transform = BoardPlacement.menuTransform(effectiveTransform, menu);
    if (transform == null) {
      return NavigationResult.NOT_FOUND;
    }

    MenuSession previous = session;
    MenuSession replacement = new MenuSession(
        menu,
        viewer,
        MenuSessionOptions.positioned(transform, this, (float) effectiveTransform.scale())
    );
    try {
      replacement.open();
    } catch (RuntimeException | Error failure) {
      try {
        replacement.close();
      } catch (RuntimeException closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      HoloUI.logExceptionStack(false, failure, "Failed to open menu %s on board %s for %s.",
          menuId, definition.id(), viewer.getName());
      return NavigationResult.DENIED;
    }

    updateHistory(mode);
    session = replacement;
    currentMenuId = menu.getId();
    if (previous != null) {
      previous.close();
    }
    return NavigationResult.APPLIED;
  }

  private boolean isAtRoot(String menuId) {
    return definition.rootMenuId().equals(menuId);
  }

  private void updateHistory(NavigationMode mode) {
    if (mode == NavigationMode.PUSH && currentMenuId != null) {
      history.addFirst(currentMenuId);
    } else if (mode == NavigationMode.BACK) {
      history.pollFirst();
    } else if (mode == NavigationMode.HOME) {
      history.clear();
    }
  }

  private void applyTransform() {
    if (session == null || currentMenuId == null) {
      return;
    }
    MenuDefinitionData menu = menuLookup.apply(currentMenuId);
    MenuTransform transform = menu == null ? null : BoardPlacement.menuTransform(effectiveTransform, menu);
    if (transform != null) {
      session.applyTransform(transform, (float) effectiveTransform.scale());
    }
  }
}
