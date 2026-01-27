package io.github.stevekk11.examples

import io.github.stevekk11.api.SubstitutionClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Example usage and tests for SubstitutionClient.
 * 
 * These tests demonstrate how to use the SubstitutionClient class
 * and validate its behavior.
 */
class SubstitutionClientExample {

    /**
     * Test: Set endpoint URL with valid value.
     */
    @Test
    fun testSetEndpointUrl() {
        val client = SubstitutionClient()
        client.setEndpointUrl("https://api.example.com/jecnarozvrh/v1")
        // If no exception is thrown, the test passes
    }

    /**
     * Test: Set endpoint URL with invalid value should throw exception.
     */
    @Test
    fun testSetEndpointUrlInvalid() {
        val client = SubstitutionClient()
        assertFailsWith<IllegalArgumentException> {
            client.setEndpointUrl("https://api.example.com/invalid")
        }
    }

    /**
     * Test: Set class symbol with valid value.
     */
    @Test
    fun testSetClassSymbol() {
        val client = SubstitutionClient()
        client.setClassSymbol("C2b")
        // If no exception is thrown, the test passes
    }

    /**
     * Test: Set class symbol with valid value (shorter).
     */
    @Test
    fun testSetClassSymbolShort() {
        val client = SubstitutionClient()
        client.setClassSymbol("A4")
        // If no exception is thrown, the test passes
    }

    /**
     * Test: Set class symbol with invalid value (too short).
     */
    @Test
    fun testSetClassSymbolTooShort() {
        val client = SubstitutionClient()
        assertFailsWith<IllegalArgumentException> {
            client.setClassSymbol("A")
        }
    }

    /**
     * Test: Set class symbol with invalid value (blank).
     */
    @Test
    fun testSetClassSymbolBlank() {
        val client = SubstitutionClient()
        assertFailsWith<IllegalArgumentException> {
            client.setClassSymbol("")
        }
    }

    /**
     * Test: Create SubstitutionClient instance.
     */
    @Test
    fun testCreateClient() {
        val client = SubstitutionClient()
        assertNotNull(client)
    }

    /**
     * Test: Configure client with both URL and class symbol.
     */
    @Test
    fun testConfigureClient() {
        val client = SubstitutionClient()
        client.setEndpointUrl("https://api.example.com/jecnarozvrh/data")
        client.setClassSymbol("E2")
        // If no exception is thrown, the test passes
    }

    /**
     * Test: Verify that URL must contain 'jecnarozvrh'.
     */
    @Test
    fun testUrlMustContainJecnarozvrh() {
        // These should all work
        val client1 = SubstitutionClient()
        client1.setEndpointUrl("http://example.com/jecnarozvrh")
        
        val client2 = SubstitutionClient()
        client2.setEndpointUrl("https://jecnarozvrh.example.com")
        
        val client3 = SubstitutionClient()
        client3.setEndpointUrl("https://api.jecnarozvrh.cz/v1")
        
        // This should fail
        val client4 = SubstitutionClient()
        assertFailsWith<IllegalArgumentException> {
            client4.setEndpointUrl("https://example.com/api/v1")
        }
    }
}
