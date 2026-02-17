package art.arcane.holoui.menu;

import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.HoloUI;
import art.arcane.holoui.menu.components.MenuComponent;
import art.arcane.holoui.menu.special.BlockMenuSession;
import art.arcane.volmlib.util.math.MathHelper;
import lombok.Synchronized;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Predicate;

class SessionHolder {
    private final Object sessionLock = new Object();
    private final Object previewLock = new Object();

    private final Player player;
    private transient MenuSession session;
    private transient BlockMenuSession preview;
    private transient String lastSession;

    SessionHolder(Player player) {
        this.player = player;
    }

    @Synchronized("sessionLock")
    void openSession(MenuDefinitionData data) {
        if (!player.isOnline()) return;
        closeSession(true);
        session = new MenuSession(data, player);
        session.open();
    }

    @Synchronized("previewLock")
    void openPreview(BlockMenuSession session) {
        closePreview();
        preview = session;
        preview.open();
    }

    boolean tick() {
        if (!player.isOnline()) {
            closeSession(false);
            closePreview();
            return true;
        }


        synchronized (sessionLock) {
            if (session != null) {
                session.getComponents().forEach(MenuComponent::tick);
            }
        }

        synchronized (previewLock) {
            if (preview != null) {
                try {
                    Vector dir = player.getEyeLocation().getDirection();
                    preview.rotate(-(float) MathHelper.getRotationFromDirection(dir).getY());
                    preview.move(player.getEyeLocation().clone().add(dir.multiply(2F)), false);
                    preview.getComponents().forEach(MenuComponent::tick);
                } catch (Exception ex) {
                    HoloUI.logExceptionStack(false, ex, "Failed to tick preview for %s. Closing preview.", player.getName());
                    safelyClosePreview();
                }
            }
        }
        return false;
    }

    @Synchronized("sessionLock")
    boolean closeSession(boolean history) {
        if (session == null) return false;
        lastSession = history ? session.getId() : null;
        session.close();
        session = null;
        return true;
    }

    @Synchronized("previewLock")
    void closePreview() {
        if (preview == null) return;
        safelyClosePreview();
    }

    void close() {
        closeSession(false);
        closePreview();
    }

    private void safelyClosePreview() {
        BlockMenuSession current = preview;
        preview = null;
        if (current == null) {
            return;
        }

        try {
            current.close();
        } catch (Exception ex) {
            HoloUI.logExceptionStack(false, ex, "Failed to close preview cleanly for %s.", player.getName());
        }
    }

    @Synchronized("sessionLock")
    void onSession(Consumer<@Nullable MenuSession> action) {
        action.accept(session);
    }

    @Synchronized("previewLock")
    void onPreview(Consumer<@Nullable BlockMenuSession> action) {
        action.accept(preview);
    }

    @Synchronized("sessionLock")
    boolean onLastSession(Predicate<@Nullable String> predicate) {
        return predicate.test(lastSession);
    }

    void refreshVisuals() {
        synchronized (sessionLock) {
            if (session != null) {
                session.getComponents().forEach(component -> {
                    if (!component.isOpen()) return;
                    component.close();
                    component.open(true);
                });
            }
        }
        synchronized (previewLock) {
            if (preview != null) {
                preview.getComponents().forEach(component -> {
                    if (!component.isOpen()) return;
                    component.close();
                    component.open(false);
                });
            }
        }
    }
}
