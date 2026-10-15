package com.siberanka.twilight.api.event;

import com.siberanka.twilight.deploy.DeploymentResult;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class TwilightDeployCompleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DeploymentResult result;

    public TwilightDeployCompleteEvent(DeploymentResult result) { this.result = result; }
    public DeploymentResult result() { return result; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
