/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy;

import java.util.regex.Pattern;

/** The hosts a Bedrock transfer may name. A client's join address comes from the client itself. */
final class TransferHosts {
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.-]{1,253}|\\[?[0-9A-Fa-f:.]{2,45}]?");

    private TransferHosts() {}

    /** True for a plain host name or IP address and a port from 1 to 65535. */
    static boolean valid(String host, int port) {
        return host != null && port > 0 && port <= 65_535 && HOST.matcher(host).matches();
    }
}
