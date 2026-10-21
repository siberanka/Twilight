package com.siberanka.twilight.integration;

import com.siberanka.twilight.integration.text.SafeUiProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TranslatorChainGuardTest {
    @Test
    void findsAnExistingHookAndStopsOnCycles() {
        Object original = new Object();
        Hook existing = new Hook(original);
        Hook head = new Hook(existing);
        assertTrue(TranslatorChainGuard.contains(head, existing));
        assertFalse(TranslatorChainGuard.contains(head, new Object()));
        existing.original = head;
        assertTrue(TranslatorChainGuard.contains(head, new Object()));
        assertFalse(TranslatorChainGuard.contains(null, head));
    }

    @Test
    void uiProfileHandlesAvailableMissingAndFailingMethods() {
        assertEquals("POCKET", SafeUiProfile.get(new ClientData()));
        assertNull(SafeUiProfile.get(null));
        assertNull(SafeUiProfile.get(new Object()));
        assertNull(SafeUiProfile.get(new FailingClientData()));
    }

    private static final class Hook {
        private Object original;
        private Hook(Object original) { this.original = original; }
    }

    public static final class ClientData {
        public String getUiProfile() { return "POCKET"; }
    }

    public static final class FailingClientData {
        public Object getUiProfile() { throw new IllegalStateException("unavailable"); }
    }
}
