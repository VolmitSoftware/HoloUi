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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipUtils {

    public static void unzipFile(File zipFile, File target) throws IOException {
        byte[] buffer = new byte[1024];
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                File newFile = zipSlipProtect(entry, target);
                if (entry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs())
                        throw new IOException("Failed to create zip directory for entry \"" + newFile + "\"!");
                } else {
                    try (FileOutputStream out = new FileOutputStream(newFile)) {
                        int length;
                        while ((length = zip.read(buffer)) > 0)
                            out.write(buffer, 0, length);
                    }
                }
            }
            zip.closeEntry();
        }
    }

    private static File zipSlipProtect(ZipEntry entry, File dir) throws IOException {
        File target = new File(dir, entry.getName());
        if (!target.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator))
            throw new IOException("Entry is outside of the target dir: " + entry.getName());
        return target;
    }
}
