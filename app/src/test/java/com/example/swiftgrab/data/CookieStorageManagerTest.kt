package com.example.swiftgrab.data

import com.example.swiftgrab.data.cookies.CookieStorageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieStorageManagerTest {

    @Test
    fun `parseToNetscape correctly converts cookie string to Netscape 7-column format`() {
        val rawCookies = "auth_token=abc123token; ct0=xyz456csrf; guest_id=v1%3A123"
        val netscape = CookieStorageManager.parseToNetscape(rawCookies)

        assertTrue(netscape.startsWith("# Netscape HTTP Cookie File"))
        assertTrue(netscape.contains(".x.com\tTRUE\t/\tTRUE\t2147483647\tauth_token\tabc123token"))
        assertTrue(netscape.contains(".twitter.com\tTRUE\t/\tTRUE\t2147483647\tauth_token\tabc123token"))
        assertTrue(netscape.contains(".x.com\tTRUE\t/\tTRUE\t2147483647\tct0\txyz456csrf"))
        assertTrue(netscape.contains(".twitter.com\tTRUE\t/\tTRUE\t2147483647\tct0\txyz456csrf"))
    }

    @Test
    fun `containsAuthTokens detects presence of auth_token or twid`() {
        assertTrue(CookieStorageManager.containsAuthTokens("guest_id=123; auth_token=secret; ct0=csrf"))
        assertTrue(CookieStorageManager.containsAuthTokens("twid=\"u=999\"; guest_id=123"))
        assertFalse(CookieStorageManager.containsAuthTokens("guest_id=123; personalization_id=456"))
        assertFalse(CookieStorageManager.containsAuthTokens(null))
        assertFalse(CookieStorageManager.containsAuthTokens(""))
    }
}
