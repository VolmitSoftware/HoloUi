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
package com.volmit.holoui;

import com.github.zafarkhaja.semver.Version;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.volmit.holoui.utils.WebUtils;
import com.volmit.holoui.utils.ZipUtils;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import org.apache.commons.io.FileUtils;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public final class BuilderServer {

    private static final String URL = "https://api.github.com/repos/VolmitSoftware/HUI-Web-Editor/releases/latest";
    private static final String BUILT_NAME = "builder_static.zip";

    private final File serverDir, versionFile;

    private String version;
    private ServerRunnable serverRunnable;

    public BuilderServer(File pluginDir) {
        serverDir = new File(pluginDir, "builder");
        versionFile = new File(serverDir, "version");
    }

    public boolean prepareServer() {
        try {
            JsonElement latestManifest = WebUtils.getJson(URL);
            HoloUI.log(Level.INFO, "Preparing Builder server...");
            if (shouldRedownload(latestManifest)) {
                JsonArray assets = latestManifest.getAsJsonObject().getAsJsonArray("assets");
                downloadServer(getZipUrl(assets));
            }
            HoloUI.log(Level.INFO, "Server ready!");
            return true;
        } catch (IOException e) {
            HoloUI.logExceptionStack(true, e, "Failed to setup builder server:");
            return false;
        }
    }

    private String getZipUrl(JsonArray assets) throws IOException {
        for (JsonElement asset : assets) {
            JsonObject entry = asset.getAsJsonObject();
            if (entry.get("name").getAsString().equalsIgnoreCase(BUILT_NAME))
                return entry.get("browser_download_url").getAsString();
        }
        throw new IOException("Invalid release manifest: No server build available!");
    }

    private void prepareFolder() throws IOException {
        if (serverDir.exists()) {
            if (serverDir.isDirectory())
                FileUtils.deleteDirectory(serverDir);
            else
                FileUtils.deleteQuietly(serverDir);
        }
        serverDir.mkdirs();
    }

    private void downloadServer(String contentUrl) throws IOException {
        prepareFolder();
        File archive = new File(serverDir, "server.zip");
        HoloUI.log(Level.INFO, "\tDownloading latest builder...");
        WebUtils.downloadFile(contentUrl, archive);
        HoloUI.log(Level.INFO, "\tExtracting archive...");
        ZipUtils.unzipFile(archive, serverDir);
        HoloUI.log(Level.INFO, "\tRemoving archive...");
        archive.delete();
        FileUtils.writeStringToFile(versionFile, version, "UTF-8");
        HoloUI.log(Level.INFO, "\tDone!");
    }

    private boolean shouldRedownload(JsonElement fetchedMeta) throws IOException {
        Version remote = Version.valueOf(fetchedMeta.getAsJsonObject().get("tag_name").getAsString());
        if (!versionFile.exists() || versionFile.isDirectory()) {
            this.version = remote.toString();
            return true;
        } else {
            Version local = Version.valueOf(FileUtils.readFileToString(versionFile, "UTF-8"));
            if (remote.greaterThan(local)) {
                HoloUI.log(Level.INFO, "Newer version found! [%s -> %s]", local, remote);
                this.version = remote.toString();
                return true;
            }
            HoloUI.log(Level.INFO, "No newer version found. [%s]", local);
            this.version = local.toString();
            return false;
        }
    }

    public boolean isServerRunning() {
        return this.serverRunnable != null && !this.serverRunnable.isCancelled();
    }

    public boolean stopServer() {
        if (isServerRunning()) {
            this.serverRunnable.cancel();
            this.serverRunnable = null;
            HoloUI.log(Level.INFO, "Server stopped.");
            return true;
        }
        return false;
    }

    public void startServer(String host, int port) {
        stopServer();
        this.serverRunnable = new ServerRunnable(host, port);
        this.serverRunnable.runTaskAsynchronously(HoloUI.INSTANCE);
        HoloUI.log(Level.INFO, "Server started at \"%s:%d\"", host, port);
    }

    private final class ServerRunnable extends BukkitRunnable {

        private final Undertow server;

        public ServerRunnable(String host, int port) {
            this.server = Undertow.builder()
                    .addHttpListener(port, host)
                    .setHandler(Handlers.path()
                            .addPrefixPath("/", new ResourceHandler(new PathResourceManager(serverDir.toPath()))
                                    .addWelcomeFiles("index.html")))
                    .build();
        }

        @Override
        public void run() {
            server.start();
        }

        @Override
        public synchronized void cancel() throws IllegalStateException {
            server.stop();
            super.cancel();
        }
    }
}
