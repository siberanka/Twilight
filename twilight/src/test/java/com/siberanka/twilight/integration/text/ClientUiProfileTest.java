package com.siberanka.twilight.integration.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientUiProfileTest {
    public enum Profile { CLASSIC, POCKET }

    public static final class Data {
        public Profile getUiProfile() { return Profile.POCKET; }
    }

    public static final class Session {
        private final Object data;
        Session(Object data) { this.data = data; }
        public Object getClientData() { return data; }
    }

    public static final class ClassicData {
        public Profile getUiProfile() { return Profile.CLASSIC; }
    }

    public static final class TextData {
        public String getUiProfile() { return "pocket"; }
    }

    public static final class ThrowingData {
        public Profile getUiProfile() { throw new IllegalStateException("not ready"); }
    }

    public static final class NoAccessorData {
        public int other() { return 1; }
    }

    public static final class NoClientDataSession { }

    @Test void pocketProfileIsRead() {
        assertEquals(ClientUiProfile.POCKET, ClientUiProfile.of(new Session(new Data())));
        assertEquals(ClientUiProfile.POCKET, ClientUiProfile.of(new Session(new TextData())));
    }

    @Test void classicProfileIsRead() {
        assertEquals(ClientUiProfile.CLASSIC, ClientUiProfile.of(new Session(new ClassicData())));
    }

    @Test void anythingUnreadableFallsBackToClassic() {
        assertEquals(ClientUiProfile.CLASSIC, ClientUiProfile.of(null));
        assertEquals(ClientUiProfile.CLASSIC, ClientUiProfile.of(new Session(null)));
        assertEquals(ClientUiProfile.CLASSIC, ClientUiProfile.of(new Session(new ThrowingData())));
        assertEquals(ClientUiProfile.CLASSIC, ClientUiProfile.of(new Session(new NoAccessorData())));
        assertEquals(ClientUiProfile.CLASSIC, ClientUiProfile.of(new NoClientDataSession()));
        assertEquals(ClientUiProfile.CLASSIC, ClientUiProfile.of("not a session"));
    }

    @Test void namesAreMatchedLeniently() {
        assertEquals(ClientUiProfile.POCKET, ClientUiProfile.fromName(" Pocket "));
        assertEquals(ClientUiProfile.CLASSIC, ClientUiProfile.fromName("TABLET"));
        assertEquals(ClientUiProfile.CLASSIC, ClientUiProfile.fromName(null));
    }
}
