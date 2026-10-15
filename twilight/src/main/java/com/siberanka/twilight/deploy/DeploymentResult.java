package com.siberanka.twilight.deploy;

import java.nio.file.Path;
import java.util.List;

public record DeploymentResult(boolean success, Path geyserDirectory, Path snapshot, List<String> deployedFiles,
                               String message, boolean restartRequired) {
    public DeploymentResult(boolean success, Path geyserDirectory, Path snapshot, List<String> deployedFiles, String message) {
        this(success, geyserDirectory, snapshot, deployedFiles, message, false);
    }
}
