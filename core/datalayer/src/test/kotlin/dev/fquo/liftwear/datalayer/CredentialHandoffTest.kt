package dev.fquo.liftwear.datalayer

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The key crosses the Data Layer in plaintext, and DataItems persist and replicate until
 * something deletes them. Deleting on ack is therefore a security property, not a tidiness
 * one, and it gets tests rather than a comment.
 */
class CredentialHandoffTest {

    private class FakeTransport : CredentialTransport {
        val puts = mutableListOf<CredentialPayload>()
        val acks = mutableListOf<String>()
        var deleteCount = 0
        var itemsPresent = 0

        override suspend fun put(payload: CredentialPayload) {
            puts += payload
            itemsPresent = 1
        }

        override suspend fun deleteCredentials(): Int {
            deleteCount++
            val removed = itemsPresent
            itemsPresent = 0
            return removed
        }

        override suspend fun sendAck(nodeId: String) {
            acks += nodeId
        }
    }

    private fun payload(key: String = "lftsk_test_key", issuedAt: Long = 1_000L) =
        CredentialPayload(key, issuedAt)

    // --- phone side ---

    @Test
    fun `offer publishes the key with the time it was issued`() = runTest {
        val transport = FakeTransport()
        CredentialHandoff(transport, now = { 4_242L }).offer("  lftsk_abc  ")
        assertEquals(1, transport.puts.size)
        // Trimmed: a pasted key routinely carries whitespace.
        assertEquals("lftsk_abc", transport.puts[0].apiKey)
        assertEquals(4_242L, transport.puts[0].issuedAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `offering a blank key is refused`() = runTest {
        CredentialHandoff(FakeTransport()).offer("   ")
    }

    @Test
    fun `ack deletes the credential item`() = runTest {
        val transport = FakeTransport()
        val handoff = CredentialHandoff(transport)
        handoff.offer("lftsk_abc")
        assertEquals(1, transport.itemsPresent)

        assertTrue(handoff.onAck(DataLayerContract.PATH_CREDENTIALS_ACK))
        assertEquals(0, transport.itemsPresent)
    }

    @Test
    fun `a message on another path never deletes anything`() = runTest {
        val transport = FakeTransport()
        val handoff = CredentialHandoff(transport)
        handoff.offer("lftsk_abc")

        assertFalse(handoff.onAck("/liftwear/something-else"))
        assertEquals(0, transport.deleteCount)
        assertEquals(1, transport.itemsPresent)
    }

    @Test
    fun `withdraw removes an unacknowledged credential`() = runTest {
        val transport = FakeTransport()
        val handoff = CredentialHandoff(transport)
        handoff.offer("lftsk_abc")
        assertEquals(1, handoff.withdraw())
        assertEquals(0, transport.itemsPresent)
    }

    // --- watch side ---

    private fun incoming(
        path: String = DataLayerContract.PATH_CREDENTIALS,
        node: String = "node-phone",
        payload: CredentialPayload? = payload(),
    ) = IncomingCredential(path, node, payload)

    @Test
    fun `a valid credential is stored and acknowledged`() = runTest {
        val transport = FakeTransport()
        var stored: String? = null
        val accepted = CredentialIntake(transport, now = { 1_000L })
            .accept(listOf(incoming())) { stored = it }

        assertTrue(accepted)
        assertEquals("lftsk_test_key", stored)
        assertEquals(listOf("node-phone"), transport.acks)
    }

    @Test
    fun `the key is stored before the ack is sent`() = runTest {
        // If the ack went first and the store then failed, the phone would delete the only
        // copy and the watch would be left unpaired with no way back.
        val transport = FakeTransport()
        val order = mutableListOf<String>()
        val failing = object : CredentialTransport by transport {
            override suspend fun sendAck(nodeId: String) {
                order += "ack"
                transport.sendAck(nodeId)
            }
        }
        CredentialIntake(failing, now = { 1_000L }).accept(listOf(incoming())) { order += "store" }
        assertEquals(listOf("store", "ack"), order)
    }

    @Test
    fun `a store failure prevents the ack`() = runTest {
        val transport = FakeTransport()
        val intake = CredentialIntake(transport, now = { 1_000L })
        runCatching {
            intake.accept(listOf(incoming())) { error("disk full") }
        }
        assertTrue("must not ack a key it failed to save", transport.acks.isEmpty())
    }

    @Test
    fun `a deleted item is the phone's own cleanup and is ignored`() = runTest {
        val transport = FakeTransport()
        var stored: String? = null
        val accepted = CredentialIntake(transport).accept(listOf(incoming(payload = null))) { stored = it }

        assertFalse(accepted)
        assertNull(stored)
        assertTrue(transport.acks.isEmpty())
    }

    @Test
    fun `items on other paths are ignored`() = runTest {
        val transport = FakeTransport()
        var stored: String? = null
        CredentialIntake(transport).accept(listOf(incoming(path = "/liftwear/other"))) { stored = it }
        assertNull(stored)
        assertTrue(transport.acks.isEmpty())
    }

    @Test
    fun `a blank key is not stored`() = runTest {
        val transport = FakeTransport()
        var stored: String? = null
        CredentialIntake(transport).accept(listOf(incoming(payload = payload(key = "")))) { stored = it }
        assertNull(stored)
    }

    @Test
    fun `a stale credential is refused but still acknowledged so the phone clears it`() = runTest {
        val transport = FakeTransport()
        var stored: String? = null
        val ttl = DataLayerContract.CREDENTIAL_TTL_MILLIS
        val accepted = CredentialIntake(transport, now = { ttl + 2_000L })
            .accept(listOf(incoming(payload = payload(issuedAt = 1_000L)))) { stored = it }

        assertFalse(accepted)
        assertNull("a token this old must not pair a watch", stored)
        assertEquals(
            "leaving it undeleted would strand a bearer token in the Data Layer",
            listOf("node-phone"),
            transport.acks,
        )
    }

    @Test
    fun `a credential issued far in the future is refused too`() = runTest {
        val transport = FakeTransport()
        var stored: String? = null
        CredentialIntake(transport, now = { 1_000L })
            .accept(listOf(incoming(payload = payload(issuedAt = 1_000L + 2 * DataLayerContract.CREDENTIAL_TTL_MILLIS)))) {
                stored = it
            }
        assertNull(stored)
    }

    @Test
    fun `a redelivered credential is acknowledged again`() = runTest {
        // Otherwise the phone never learns it landed and the item lives on forever.
        val transport = FakeTransport()
        val intake = CredentialIntake(transport, now = { 1_000L })
        intake.accept(listOf(incoming())) {}
        intake.accept(listOf(incoming())) {}
        assertEquals(listOf("node-phone", "node-phone"), transport.acks)
    }
}
