/*
 * HoloUI is a holographic user interface for Minecraft Bukkit Servers
 * Copyright (c) 2025 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.volmit.holoui.utils;

import lombok.NonNull;
import lombok.extern.java.Log;

import java.util.function.LongSupplier;
import java.util.logging.Level;

@Log
public class Looper extends Thread {
    private final LongSupplier supplier;

    public Looper(@NonNull LongSupplier supplier) {
        this.supplier = supplier;
    }

    protected Looper() {
        this.supplier = () -> Long.MAX_VALUE;
    }

    public static Looper fixed(@NonNull Runnable runnable, long interval) {
        if (interval < 0) throw new IllegalArgumentException("interval cannot be negative");
        return new Looper(() -> {
            long time = System.currentTimeMillis();
            try {
                runnable.run();
            } catch (Throwable e) {
                log.log(Level.SEVERE, "Error in fixed looper", e);
            }
            return Math.max(System.currentTimeMillis() - time - interval, 0);
        });
    }

    protected long loop() {
        return supplier.getAsLong();
    }

    @Override
    public final void run() {
        while (!isInterrupted()) {
            try {
                long sleep = loop();
                if (sleep < 0) return;
                try {
                    sleep(sleep);
                } catch (InterruptedException e) {
                    return;
                }
            } catch (Throwable e) {
                log.log(Level.SEVERE, "Error in Looper " + getName(), e);
                return;
            }
        }
    }
}
