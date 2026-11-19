package com.example.swiftgrab.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XLoginDialogTest {

    @Test
    fun `isAllowedXLoginUrl allows https for x com and twitter com domains and subdomains`() {
        assertTrue(isAllowedXLoginUrl("https://x.com/i/flow/login"))
        assertTrue(isAllowedXLoginUrl("https://www.x.com/i/flow/login"))
        assertTrue(isAllowedXLoginUrl("https://mobile.x.com/login"))
        assertTrue(isAllowedXLoginUrl("https://twitter.com/login"))
        assertTrue(isAllowedXLoginUrl("https://api.twitter.com/oauth/authenticate"))
    }

    @Test
    fun `isAllowedXLoginUrl allows https for oauth and bot-challenge providers`() {
        assertTrue(isAllowedXLoginUrl("https://accounts.google.com/o/oauth2/auth"))
        assertTrue(isAllowedXLoginUrl("https://sub.accounts.google.com/signin"))
        assertTrue(isAllowedXLoginUrl("https://appleid.apple.com/auth/authorize"))
        assertTrue(isAllowedXLoginUrl("https://challenges.cloudflare.com/turnstile/v0/api.js"))
    }

    @Test
    fun `isAllowedXLoginUrl rejects non-https schemes`() {
        assertFalse(isAllowedXLoginUrl("http://x.com/login"))
        assertFalse(isAllowedXLoginUrl("http://twitter.com/login"))
        assertFalse(isAllowedXLoginUrl("file:///data/data/com.example.swiftgrab/databases/app.db"))
        assertFalse(isAllowedXLoginUrl("content://media/external"))
        assertFalse(isAllowedXLoginUrl("javascript:alert(1)"))
    }

    @Test
    fun `isAllowedXLoginUrl rejects unauthorized domains and spoof attempts`() {
        assertFalse(isAllowedXLoginUrl("https://evil-x.com/login"))
        assertFalse(isAllowedXLoginUrl("https://x.com.attacker.com/login"))
        assertFalse(isAllowedXLoginUrl("https://fake-twitter.com/login"))
        assertFalse(isAllowedXLoginUrl("https://google.com/login"))
        assertFalse(isAllowedXLoginUrl("https://apple.com/login"))
        assertFalse(isAllowedXLoginUrl("https://cloudflare.com/login"))
        assertFalse(isAllowedXLoginUrl("https://evil.com"))
        assertFalse(isAllowedXLoginUrl("https://evil.com\\.x.com/login"))
        assertFalse(isAllowedXLoginUrl("https://x.com:pass@evil.com/login"))
        assertFalse(isAllowedXLoginUrl("https://x.com:pass@evil.com/a|b"))
        assertFalse(isAllowedXLoginUrl(""))
        assertFalse(isAllowedXLoginUrl(null))
    }
}
