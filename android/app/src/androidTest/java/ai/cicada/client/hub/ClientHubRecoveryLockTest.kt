package ai.cicada.client.hub

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Regression tests for durable N4 safety locks using an isolated, synthetic session. */
@RunWith(AndroidJUnit4::class)
class ClientHubRecoveryLockTest {
    private data class Sandbox(
        val context: Context,
        val root: File,
        val stateFile: File,
        val wrappedDeviceKeyFile: File,
    )

    @Test
    fun uncertainPendingRpcSurvivesReconstructionAndBlocksRecoveryResetAndRepin() {
        val sandbox = sandbox()
        try {
            val controlPublic = ClientWireCrypto.Identity.generate().use { it.publicIdentity }
            val initial = ClientHubSession(sandbox.context)
            initial.createDeviceIdentity()
            val pendingPacket = "SYNTHETIC_OPAQUE_PENDING_PACKET_MUST_NOT_BE_REPLACED"
            writeState(sandbox.stateFile, pendingState(controlPublic.toJson(), pendingPacket))
            val initialStateBytes = sandbox.stateFile.readBytes()
            val wrappedKeyBytes = sandbox.wrappedDeviceKeyFile.readBytes()

            // A new ClientHubSession instance represents process/session reconstruction.
            val restored = ClientHubSession(sandbox.context)
            val status = restored.getStatus()
            assertEquals("pending-n4-synthetic-operation",
                status.get("pendingOperationId").asString)
            assertEquals(42L, status.get("nextSequence").asLong)
            assertTrue(status.get("recoveryBlocked").asBoolean)
            assertFalse(status.get("remoteEnabled").asBoolean)

            // The pin uses an unreachable loopback endpoint. The precise local guard error
            // proves recovery stopped before parsing keys, opening HTTP, or changing state.
            expectError("RECOVERY_PROTOCOL_REQUIRED") { restored.recoverPending() }
            assertUnchanged(sandbox.stateFile, initialStateBytes)

            expectError("PENDING_RECOVERY_REQUIRED") { restored.startNewDeviceEnrollment() }
            assertUnchanged(sandbox.stateFile, initialStateBytes)
            assertUnchanged(sandbox.wrappedDeviceKeyFile, wrappedKeyBytes)

            val reconstructedAgain = ClientHubSession(sandbox.context)
            val replacementControl = ClientWireCrypto.Identity.generate().use { it.publicIdentity }
            expectError("PENDING_RECOVERY_REQUIRED") {
                reconstructedAgain.pinHub(
                    "https://127.0.0.1:1",
                    "hub-n4-replacement-synthetic",
                    replacementControl.toJson(),
                )
            }
            val after = ClientHubSession(sandbox.context).getStatus()
            assertEquals("hub-n4-original-synthetic", after.get("hubId").asString)
            assertEquals("pending-n4-synthetic-operation",
                after.get("pendingOperationId").asString)
            assertEquals(42L, after.get("nextSequence").asLong)
            assertFalse(after.get("remoteEnabled").asBoolean)
            assertUnchanged(sandbox.stateFile, initialStateBytes)
            assertUnchanged(sandbox.wrappedDeviceKeyFile, wrappedKeyBytes)
        } finally {
            sandbox.root.deleteRecursively()
        }
    }

    @Test
    fun ambiguousEnrollmentAttemptSurvivesReconstructionAndBlocksRetryResetAndRepin() {
        val sandbox = sandbox()
        try {
            val controlPublic = ClientWireCrypto.Identity.generate().use { it.publicIdentity }
            val initial = ClientHubSession(sandbox.context)
            val deviceKeyId = initial.createDeviceIdentity()
                .getAsJsonObject("devicePublicIdentity").get("id").asString
            writeState(
                sandbox.stateFile,
                enrollmentAttemptState(controlPublic.toJson(), deviceKeyId),
            )
            val initialStateBytes = sandbox.stateFile.readBytes()
            val wrappedKeyBytes = sandbox.wrappedDeviceKeyFile.readBytes()

            val restored = ClientHubSession(sandbox.context)
            val status = restored.getStatus()
            assertTrue(status.get("enrollmentRecoveryRequired").asBoolean)
            assertEquals("owner-n4-synthetic", status.get("pendingEnrollmentOwnerId").asString)
            assertEquals("device-n4-synthetic", status.get("pendingEnrollmentDeviceId").asString)
            assertFalse(status.get("enrolled").asBoolean)
            assertFalse(status.get("remoteEnabled").asBoolean)

            expectError("ENROLLMENT_RECOVERY_REQUIRED") {
                restored.enroll("", "", "{}", "", "not-a-grant")
            }
            assertUnchanged(sandbox.stateFile, initialStateBytes)

            expectError("ENROLLMENT_RECOVERY_REQUIRED") {
                restored.startNewDeviceEnrollment()
            }
            assertUnchanged(sandbox.stateFile, initialStateBytes)
            assertUnchanged(sandbox.wrappedDeviceKeyFile, wrappedKeyBytes)

            val replacementControl = ClientWireCrypto.Identity.generate().use { it.publicIdentity }
            expectError("ENROLLMENT_RECOVERY_REQUIRED") {
                ClientHubSession(sandbox.context).pinHub(
                    "https://127.0.0.1:1",
                    "hub-n4-replacement-synthetic",
                    replacementControl.toJson(),
                )
            }
            val after = ClientHubSession(sandbox.context).getStatus()
            assertTrue(after.get("enrollmentRecoveryRequired").asBoolean)
            assertEquals("hub-n4-original-synthetic", after.get("hubId").asString)
            assertFalse(after.get("enrolled").asBoolean)
            assertUnchanged(sandbox.stateFile, initialStateBytes)
            assertUnchanged(sandbox.wrappedDeviceKeyFile, wrappedKeyBytes)
        } finally {
            sandbox.root.deleteRecursively()
        }
    }

    private fun sandbox(): Sandbox {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(target.cacheDir, "n4-recovery-lock-${UUID.randomUUID()}")
        check(root.mkdirs()) { "Could not create isolated N4 test state" }
        val context = IsolatedContext(target, root)
        val sessionDir = File(root, "client-hub").apply {
            check(mkdirs()) { "Could not create isolated ClientHubSession state" }
        }
        return Sandbox(
            context,
            root,
            File(sessionDir, "session-state.json"),
            File(sessionDir, "device-key.wrap.json"),
        )
    }

    private fun pendingState(controlPublicJson: String, packet: String) =
        baseState(controlPublicJson).apply {
            add("enrollment", JsonObject().apply {
                addProperty("ownerId", "owner-n4-synthetic")
                addProperty("deviceId", "device-n4-synthetic")
                addProperty("sessionEpoch", 7)
                addProperty("deviceKeyVersion", 2)
                addProperty("state", "ACTIVE")
            })
            addProperty("nextSequence", 42)
            addProperty("lastResponseSequence", 23)
            addProperty("role", "manager")
            addProperty("sessionCapabilitiesReady", true)
            addProperty("validatedContractRevision", CONTRACT_REVISION)
            addProperty("validatedCatalogSha256", CATALOG_SHA256)
            add("allowedOperations", JsonArray().apply {
                add("session.capabilities")
                add("status.snapshot")
            })
            addProperty("authFenced", false)
            addProperty("recoveryBlocked", true)
            addProperty("sessionError", "RPC_HTTP_409_UNCERTAIN")
            addProperty("previousDeviceMayRemainOnHub", false)
            add("pending", JsonObject().apply {
                addProperty("operation", "status.snapshot")
                addProperty("operationId", "pending-n4-synthetic-operation")
                addProperty("sequence", 41)
                addProperty("packetJson", packet)
            })
        }

    private fun enrollmentAttemptState(controlPublicJson: String, deviceKeyId: String) =
        baseState(controlPublicJson).apply {
            addProperty("nextSequence", 1)
            addProperty("lastResponseSequence", 0)
            addProperty("role", null as String?)
            addProperty("sessionCapabilitiesReady", false)
            addProperty("validatedContractRevision", null as String?)
            addProperty("validatedCatalogSha256", null as String?)
            add("allowedOperations", JsonArray())
            addProperty("authFenced", false)
            addProperty("recoveryBlocked", false)
            addProperty("sessionError", null as String?)
            addProperty("previousDeviceMayRemainOnHub", false)
            add("enrollmentAttempt", JsonObject().apply {
                addProperty("ownerId", "owner-n4-synthetic")
                addProperty("deviceId", "device-n4-synthetic")
                addProperty("deviceKeyId", deviceKeyId)
                addProperty("grantSha256", "a".repeat(64))
            })
        }

    private fun baseState(controlPublicJson: String) = JsonObject().apply {
        add("pin", JsonObject().apply {
            // A refused loopback port is isolated from any actual Hub process.
            addProperty("baseUrl", "https://127.0.0.1:1")
            addProperty("hubId", "hub-n4-original-synthetic")
            addProperty("controlPublicIdentityJson", controlPublicJson)
            addProperty("controlKeyVersion", 1)
        })
        addProperty("previousDeviceMayRemainOnHub", false)
    }

    private fun writeState(file: File, state: JsonObject) {
        file.writeText(state.toString(), StandardCharsets.UTF_8)
    }

    private fun assertUnchanged(file: File, expected: ByteArray) {
        assertTrue("Expected isolated test file ${file.name}", file.exists())
        assertArrayEquals("${file.name} must remain byte-for-byte unchanged", expected, file.readBytes())
    }

    private fun expectError(expected: String, action: () -> Unit) {
        try {
            action()
            throw AssertionError("Expected HubSessionException $expected")
        } catch (error: HubSessionException) {
            assertEquals(expected, error.errorCode)
        }
    }

    private class IsolatedContext(
        base: Context,
        private val isolatedNoBackupFilesDir: File,
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getNoBackupFilesDir(): File = isolatedNoBackupFilesDir
    }

    private companion object {
        const val CONTRACT_REVISION = "client-hub-v1.1"
        const val CATALOG_SHA256 = "f6f05783ddc00e51b92ebe050b5d8e6b9b185fae80d8fc6f04bc3143b6782374"
    }
}
