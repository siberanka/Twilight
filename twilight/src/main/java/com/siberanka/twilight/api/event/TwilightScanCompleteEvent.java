package com.siberanka.twilight.api.event;

import com.siberanka.twilight.source.ContentReport;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class TwilightScanCompleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ContentReport report;

    public TwilightScanCompleteEvent(ContentReport report) { this.report = report; }
    public ContentReport report() { return report; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
