package com.volmit.holoui.menu;

import com.volmit.holoui.config.MenuDefinitionData;
import com.volmit.holoui.menu.components.MenuComponent;
import com.volmit.holoui.menu.special.BlockMenuSession;
import com.volmit.holoui.utils.math.MathHelper;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

class SessionHolder {
    final Player player;
    transient MenuSession session;
    transient BlockMenuSession preview;
    transient String lastSession;

    SessionHolder(Player player) {
        this.player = player;
    }

    synchronized void openSession(MenuDefinitionData data) {
        if (!player.isOnline()) return;
        closeSession(true);
        session = new MenuSession(data, player);
        session.open();
    }

    synchronized void openPreview(BlockMenuSession session) {
        closePreview();
        preview = session;
    }

    synchronized boolean tick() {
        if (!player.isOnline()) {
            closeSession(false);
            closePreview();
            return true;
        }

        if (session != null) {
            session.getComponents().forEach(MenuComponent::tick);
        }

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
        return false;
    }

    synchronized boolean closeSession(boolean history) {
        if (session == null) return false;
        lastSession = history ? session.getId() : null;
        session.close();
        session = null;
        return true;
    }

    synchronized void closePreview() {
        if (preview == null) return;
        preview.close();
        preview = null;
    }

    synchronized void close() {
        closeSession(false);
        closePreview();
    }
}
