package com.example.airehabpostureapp.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// M2: 설정값이 비어 있어도 앱이 꺼지지 않음
public class SupabaseClientConfigTest {

    @Test
    public void isConfigured_needsBothUrlAndKey() {
        assertTrue(SupabaseClient.isConfigured("https://abc.supabase.co", "sb_publishable_x"));
        assertFalse(SupabaseClient.isConfigured("", "sb_publishable_x"));
        assertFalse(SupabaseClient.isConfigured("https://abc.supabase.co", ""));
        assertFalse(SupabaseClient.isConfigured("  ", "  "));
        assertFalse(SupabaseClient.isConfigured(null, null));
    }

    @Test
    public void baseUrl_emptyUrl_doesNotThrow() {
        assertEquals(SupabaseClient.UNCONFIGURED_BASE_URL, SupabaseClient.baseUrl(""));
        assertEquals(SupabaseClient.UNCONFIGURED_BASE_URL, SupabaseClient.baseUrl(null));
    }

    @Test
    public void baseUrl_addsTrailingSlash() {
        assertEquals("https://abc.supabase.co/", SupabaseClient.baseUrl("https://abc.supabase.co"));
        assertEquals("https://abc.supabase.co/", SupabaseClient.baseUrl("https://abc.supabase.co/"));
    }
}
