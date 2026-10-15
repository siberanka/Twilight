package com.siberanka.twilight.api;

import com.siberanka.twilight.source.ContentReport;

import java.nio.file.Path;
import java.util.Optional;

/** Stable Bukkit service entry point for integrations. Calls are non-blocking. */
public interface TwilightApi {
    boolean isOperationRunning();
    boolean requestScan();
    boolean requestConvert();
    boolean requestDeploy();
    Optional<ContentReport> lastContentReport();
    Path dataDirectory();
}
