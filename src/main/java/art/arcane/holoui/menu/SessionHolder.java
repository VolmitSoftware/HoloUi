package art.arcane.holoui.menu;

import art.arcane.holoui.config.MenuDefinitionData;
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
                if (!player.isSneaking() || !preview.shouldRender(preview.getPlayer().getTargetBlock(null, 10))) {
                    preview.close();
                    preview = null;
                } else {
                    Vector dir = player.getEyeLocation().getDirection();
                    preview.rotate(-(float) MathHelper.getRotationFromDirection(dir).getY());
                    preview.move(player.getEyeLocation().clone().add(dir.multiply(2F)), false);
                    preview.getComponents().forEach(MenuComponent::tick);
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

    @Synchronized("sessionLock")
    void closePreview() {
        if (preview == null) return;
        preview.close();
        preview = null;
    }

    void close() {
        closeSession(false);
        closePreview();
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
}
