package com.siberanka.twilight.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public final class TwilightOperationFailedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String operation;
    private final Path logFile;
    private final Throwable failure;

    public TwilightOperationFailedEvent(String operation, Path logFile, Throwable failure) {
        this.operation = operation; this.logFile = logFile; this.failure = failure;
    }
    public String operation() { return operation; }
    public Path logFile() { return logFile; }
    public Throwable failure() { return failure; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
