package com.siberanka.twilight.api.event;

import com.siberanka.twilight.compiler.BuildResult;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class TwilightBuildCompleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final BuildResult result;

    public TwilightBuildCompleteEvent(BuildResult result) { this.result = result; }
    public BuildResult result() { return result; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
