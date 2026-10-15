/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy.velocity;

import com.google.inject.Inject;
import com.siberanka.twilight.protocol.PackChannel;
import com.siberanka.twilight.proxy.ProxyCore;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Velocity entry point (velocity-plugin.json). {@code @Subscribe(order)} is used instead of the newer
 * {@code priority} so the plugin also runs on Velocity releases before 3.3.
 */
@SuppressWarnings("deprecation")
public final class VelocityPlugin {
    static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(PackChannel.CHANNEL);

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private ProxyCore core;

    @Inject
    public VelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    /** After every other plugin's start-up, so Geyser (which loads in the same event) is usually ready. */
    @Subscribe(order = PostOrder.LAST)
    public void onInitialize(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(CHANNEL);
        ProxyCore created = new ProxyCore(new VelocityPlatform(this), this);
        try {
            created.enable();
        } catch (java.io.IOException failure) {
            logger.error("twilight-proxy is disabled: {}", failure.getMessage());
            return;
        }
        core = created;
        try {
            created.checkLoginLimit("login-ratelimit in velocity.toml", proxy.getConfiguration().getLoginRatelimit());
        } catch (RuntimeException | LinkageError unavailable) {
            // older Velocity
        }
        proxy.getCommandManager().register(proxy.getCommandManager().metaBuilder("twilightproxy").aliases("twproxy").build(),
                (SimpleCommand) invocation -> {
                    if (!invocation.source().hasPermission("twilight.proxy.admin")) return;
                    String[] args = invocation.arguments();
                    String result = args.length > 0 && args[0].equalsIgnoreCase("reload")
                            ? (created.reload() ? "twilight-proxy reloaded." : "Reload failed; see the console.")
                            : created.statusText();
                    invocation.source().sendMessage(Component.text(result));
                });
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (core != null) core.disable();
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().getId().equals(PackChannel.CHANNEL)) return;
        // Consumed in both directions: clients never see it and cannot send it to a backend.
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection server) || core == null) return;
        for (byte[] reply : core.serverMessage(server.getServerInfo().getName(), event.getData())) {
            server.sendPluginMessage(CHANNEL, reply);
        }
    }

    /** The first server Velocity picked for each joining player, before other plugins changed it. */
    private final Map<UUID, String> originals = new ConcurrentHashMap<>();

    @Subscribe(order = PostOrder.FIRST)
    public void onChooseInitialServerFirst(PlayerChooseInitialServerEvent event) {
        if (core == null || originals.size() > 100_000) return;
        originals.put(event.getPlayer().getUniqueId(), event.getInitialServer().map(server -> server.getServerInfo().getName()).orElse(""));
    }

    @Subscribe(order = PostOrder.LAST)
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        if (core == null) return;
        Player player = event.getPlayer();
        String chosen = event.getInitialServer().map(server -> server.getServerInfo().getName()).orElse(null);
        core.initial(player.getUniqueId(), player.getUsername(), chosen, originals.get(player.getUniqueId()))
                .flatMap(proxy::getServer)
                .ifPresent(event::setInitialServer);
    }

    @Subscribe(order = PostOrder.LAST)
    public void onPreConnect(ServerPreConnectEvent event) {
        if (core == null) return;
        Player player = event.getPlayer();
        boolean first = player.getCurrentServer().isEmpty();
        if (!event.getResult().isAllowed()) {
            // A login plugin refused the server a reconnected player came back for: join where Velocity would have.
            String original = originals.remove(player.getUniqueId());
            if (first && original != null && !original.isEmpty()
                    && core.refusedFirst(player.getUniqueId(), player.getUsername(), event.getOriginalServer().getServerInfo().getName())) {
                proxy.getServer(original).ifPresent(server -> later(() -> player.createConnectionRequest(server).fireAndForget()));
            }
            return;
        }
        if (first) originals.remove(player.getUniqueId());
        RegisteredServer target = event.getResult().getServer().orElse(event.getOriginalServer());
        ProxyCore.Route route = core.beforeConnect(player.getUniqueId(), player.getUsername(), target.getServerInfo().getName(), first);
        if (route instanceof ProxyCore.Route.Transferred) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
        } else if (route instanceof ProxyCore.Route.Redirect redirect) {
            Optional<RegisteredServer> destination = proxy.getServer(redirect.server());
            if (destination.isEmpty()) return;
            // A new request, so every plugin checks the destination as well.
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            later(() -> player.createConnectionRequest(destination.get()).connect().thenAccept(result -> {
                if (!result.isSuccessful()) {
                    core.redirectFailed(player.getUsername(), redirect.server(), target.getServerInfo().getName());
                    player.createConnectionRequest(target).fireAndForget();
                }
            }));
        }
    }

    @Subscribe
    public void onConnected(ServerConnectedEvent event) {
        if (core == null) return;
        core.connected(event.getPlayer().getUniqueId(), event.getPlayer().getUsername(),
                event.getServer().getServerInfo().getName());
        Player player = event.getPlayer();
        String server = event.getServer().getServerInfo().getName();
        // Once the player plays on the server (plugin messages before that may be dropped).
        proxy.getScheduler().buildTask(this, () -> player.getCurrentServer()
                .filter(current -> current.getServerInfo().getName().equals(server))
                .ifPresent(current -> {
                    byte[] statement = core == null ? null : core.capabilitiesStatement();
                    if (statement != null) current.sendPluginMessage(CHANNEL, statement);
                })).delay(3, java.util.concurrent.TimeUnit.SECONDS).schedule();
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        if (core == null) return;
        Player player = event.getPlayer();
        core.updateNotice().filter(release -> admin(player)).ifPresent(release -> later(() -> {
            if (player.isActive()) tell(player, core.updateText(release), release.page());
        }, 3000));
        core.adminNotice().filter(notice -> admin(player)).ifPresent(notice -> later(() -> {
            if (player.isActive()) tell(player, notice, null);
        }, 3500));
    }

    static boolean admin(Player player) {
        return player.hasPermission(ProxyCore.UPDATE_PERMISSION) || player.hasPermission(ProxyCore.ADMIN_PERMISSION);
    }

    static void tell(Player player, String text, String url) {
        if (url == null) {
            player.sendMessage(Component.text(text));
            return;
        }
        player.sendMessage(Component.text(text).append(Component.text(url)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url))));
    }

    String version() {
        return proxy.getPluginManager().fromInstance(this).flatMap(container -> container.getDescription().getVersion()).orElse("unknown");
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        originals.remove(event.getPlayer().getUniqueId());
        if (core != null) core.disconnect(event.getPlayer().getUniqueId(), event.getPlayer().getUsername());
    }

    /** Runs a connection request after the current event has finished. */
    private void later(Runnable task) {
        later(task, 100);
    }

    void later(Runnable task, long millis) {
        proxy.getScheduler().buildTask(this, task).delay(millis, TimeUnit.MILLISECONDS).schedule();
    }

    ProxyServer proxy() { return proxy; }

    Logger logger() { return logger; }

    Path dataDirectory() { return dataDirectory; }

    Collection<String> serverNames() {
        return proxy.getAllServers().stream().map(server -> server.getServerInfo().getName()).toList();
    }

    String defaultServer() {
        List<String> order = proxy.getConfiguration().getAttemptConnectionOrder();
        return order.isEmpty() ? "" : order.get(0);
    }

    void repeat(Runnable task, long seconds) {
        proxy.getScheduler().buildTask(this, task).repeat(seconds, TimeUnit.SECONDS).schedule();
    }

    void async(Runnable task) {
        proxy.getScheduler().buildTask(this, task).schedule();
    }
}
