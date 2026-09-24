package ai.cicada.client.hub

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import com.cicadaclient.BuildConfig
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.concurrent.withLock
import kotlin.math.floor

/**
 * Android's local Client-to-Hub session. All durable state is kept under
 * noBackupFilesDir. Device private key bytes are separately wrapped by a
 * non-exportable Android Keystore AES-GCM key.
 *
 * Public methods are synchronous so instrumentation tests can exercise the
 * exact same code as the React Native bridge. Call them off the UI thread.
 */
class ClientHubSession(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.noBackupFilesDir, "client-hub").apply {
        if (!exists() && !mkdirs()) throw HubSessionException("STORAGE_UNAVAILABLE", "Cannot create private Hub state directory")
    }
    private val stateFile = AtomicFile(File(directory, "session-state.json"))
    private val privateKeyFile = AtomicFile(File(directory, "device-key.wrap.json"))
    private val gson = com.google.gson.Gson()

    companion object {
        private const val KEY_ALIAS = "ai.cicada.client.hub.device-wrap.v1"
        private val KEY_AAD = "cicada/android/client-device-key/wrap/v1\u0000".toByteArray(StandardCharsets.UTF_8)
        private const val CONTRACT_REVISION = "client-hub-v1.1"
        private const val CATALOG_SHA256 = "f6f05783ddc00e51b92ebe050b5d8e6b9b185fae80d8fc6f04bc3143b6782374"
        private const val MAX_BODY_BYTES = 64 * 1024
        private const val MAX_PACKET_BYTES = 256 * 1024
        private const val MAX_RESPONSE_BYTES = 256 * 1024
        private const val MAX_METADATA_BYTES = 256 * 1024
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private val PROCESS_LOCK = ReentrantLock()
        private val RPC_OPERATIONS = setOf(
            "session.capabilities", "status.snapshot", "status.changes", "goal.lifecycle",
            "topology.snapshot", "topology.apply", "devices.list", "devices.revoke",
            "nodes.preview", "nodes.confirm", "nodes.list", "nodes.revoke",
            "approvals.list", "approvals.decide", "intent.get", "intent.status",
            "intent.list", "intent.submit", "link.list", "link.invite_create", "link.invite_preview",
            "link.invite_accept",
        )
    }

    private data class Pin(
        val baseUrl: String,
        val hubId: String,
        val controlPublicIdentityJson: String,
        val controlKeyVersion: Long = 1,
    )

    private data class Enrollment(
        val ownerId: String,
        val deviceId: String,
        val sessionEpoch: Long,
        val deviceKeyVersion: Long,
        val state: String,
    )

    private data class Pending(
        val operation: String,
        val operationId: String,
        val sequence: Long,
        /** Exact serialized signed and encrypted packet; retries must send these same bytes. */
        val packetJson: String,
    )

    private data class EnrollmentAttempt(
        val ownerId: String,
        val deviceId: String,
        val deviceKeyId: String,
        val grantSha256: String,
    )

    private data class State(
        var pin: Pin? = null,
        var enrollment: Enrollment? = null,
        var nextSequence: Long = 1,
        var lastResponseSequence: Long = 0,
        var role: String? = null,
        var sessionCapabilitiesReady: Boolean = false,
        var validatedContractRevision: String? = null,
        var validatedCatalogSha256: String? = null,
        var allowedOperations: List<String> = emptyList(),
        var authFenced: Boolean = false,
        var recoveryBlocked: Boolean = false,
        var sessionError: String? = null,
        var previousDeviceMayRemainOnHub: Boolean = false,
        var retiredPending: Pending? = null,
        var pending: Pending? = null,
        var enrollmentAttempt: EnrollmentAttempt? = null,
    )

    /** Fetches public metadata only. This method never establishes or changes trust. */
    fun fetchHubMetadata(baseUrl: String): JsonObject = PROCESS_LOCK.withLock {
        val base = validateBaseUrl(baseUrl)
        val capabilities = httpJson(base, "/v2/client/capabilities", "GET", null, MAX_METADATA_BYTES)
        if (optionalString(capabilities, "contract_revision") != CONTRACT_REVISION ||
            optionalString(capabilities, "catalog_sha256") != CATALOG_SHA256) {
            throw HubSessionException("CONTRACT_MISMATCH", "Hub public contract revision or catalog digest differs from this Client build")
        }
        val identity = httpJson(base, "/v2/client/identity", "GET", null, MAX_METADATA_BYTES)
        // Reject malformed candidate key material, while keeping it explicitly untrusted.
        val parsedPin = ClientWireCrypto.HubPin.parse(identity.toString())
        if (parsedPin.hubId.isBlank()) throw HubSessionException("INVALID_HUB_IDENTITY", "Hub identity is incomplete")
        JsonObject().apply {
            addProperty("trusted", false)
            add("capabilities", capabilities)
            add("identity", identity)
        }
    }

    /**
     * Pins values that the user supplied through an independent trusted channel.
     * No network response is consulted or promoted to a pin here.
     */
    fun pinHub(baseUrl: String, hubId: String, controlPublicIdentityJson: String): JsonObject = PROCESS_LOCK.withLock {
        val base = validateBaseUrl(baseUrl)
        val normalizedHubId = hubId.trim()
        if (normalizedHubId.isEmpty() || normalizedHubId.length > 256) {
            throw HubSessionException("INVALID_HUB_ID", "Enter a valid Hub ID from the independent trust channel")
        }
        val controlJson = parseObject(controlPublicIdentityJson, "Invalid Control public identity")
        val control = try {
            ClientWireCrypto.PublicIdentity.parse(controlJson)
        } catch (_: Exception) {
            throw HubSessionException("INVALID_HUB_IDENTITY", "Control public identity is invalid")
        }
        // Constructing a HubPin enforces the currently supported key version.
        try {
            ClientWireCrypto.HubPin(normalizedHubId, control, 1L)
        } catch (_: Exception) {
            throw HubSessionException("INVALID_HUB_IDENTITY", "Hub ID or Control public identity is invalid")
        }
        val state = readState()
        val old = state.pin
        val canonicalIdentity = control.toJson()
        if (old != null && (old.baseUrl != base || old.hubId != normalizedHubId ||
                old.controlPublicIdentityJson != canonicalIdentity) &&
            (state.pending != null || state.enrollmentAttempt != null)) {
            if (state.enrollmentAttempt != null) {
                throw HubSessionException("ENROLLMENT_RECOVERY_REQUIRED", "Keep the original Hub address and trust pin while device registration is unresolved")
            }
            throw HubSessionException("PENDING_RECOVERY_REQUIRED", "Keep the original Hub address and trust pin while enrollment or an encrypted RPC is unresolved")
        }
        if (old != null && (old.hubId != normalizedHubId || old.controlPublicIdentityJson != canonicalIdentity)) {
            // A changed trust anchor invalidates the prior enrolled authority and replay state.
            state.previousDeviceMayRemainOnHub = state.enrollment != null || state.previousDeviceMayRemainOnHub
            if (state.pending != null) state.retiredPending = state.pending
            state.enrollment = null
            state.nextSequence = 1
            state.lastResponseSequence = 0
            state.role = null
            state.sessionCapabilitiesReady = false
            state.validatedContractRevision = null
            state.validatedCatalogSha256 = null
            state.allowedOperations = emptyList()
            state.authFenced = false
            state.recoveryBlocked = false
            state.sessionError = null
            state.pending = null
        }
        state.pin = Pin(base, normalizedHubId, canonicalIdentity)
        writeState(state)
        JsonObject().apply {
            addProperty("pinned", true)
            addProperty("hubId", normalizedHubId)
            addProperty("baseUrl", base)
            addProperty("controlKeyId", control.id)
        }
    }

    /** Generates a device key once, wrapping its private bytes before any disk write. */
    fun createDeviceIdentity(): JsonObject = PROCESS_LOCK.withLock {
        val alreadyExists = privateKeyFile.baseFile.exists()
        val identity = if (alreadyExists) {
            loadDeviceIdentity()
        } else {
            ClientWireCrypto.Identity.generate()
        }
        try {
            if (!alreadyExists) writeWrappedPrivateJson(identity.privateJson())
            JsonObject().apply {
                add("devicePublicIdentity", JsonParser.parseString(identity.publicIdentity.toJson()).asJsonObject)
            }
        } finally {
            identity.close()
        }
    }

    /**
     * Explicitly abandons the local enrollment and starts a new device identity.
     * This does not revoke the old Hub record; an owner-authorized device must
     * revoke that record separately when the old key may still be usable.
     */
    fun startNewDeviceEnrollment(): JsonObject = PROCESS_LOCK.withLock {
        val state = readState()
        if (state.pin == null) throw HubSessionException("HUB_NOT_PINNED", "Pin a Hub before creating an enrolled device identity")
        if (state.enrollmentAttempt != null) {
            throw HubSessionException("ENROLLMENT_RECOVERY_REQUIRED", "Enrollment outcome is unknown; do not replace its device identity before Hub reconciliation")
        }
        if (state.pending != null) {
            throw HubSessionException("PENDING_RECOVERY_REQUIRED", "Do not replace the device key while an encrypted request is unresolved")
        }
        state.previousDeviceMayRemainOnHub = state.enrollment != null || state.previousDeviceMayRemainOnHub
        if (state.pending != null) state.retiredPending = state.pending
        state.enrollment = null
        state.nextSequence = 1
        state.lastResponseSequence = 0
        state.role = null
        state.sessionCapabilitiesReady = false
        state.validatedContractRevision = null
        state.validatedCatalogSha256 = null
        state.allowedOperations = emptyList()
        state.authFenced = false
        state.recoveryBlocked = false
        state.sessionError = null
        state.pending = null
        writeState(state)
        deleteDeviceWrappingKey()
        val identity = ClientWireCrypto.Identity.generate()
        try {
            writeWrappedPrivateJson(identity.privateJson())
            JsonObject().apply {
                add("devicePublicIdentity", JsonParser.parseString(identity.publicIdentity.toJson()).asJsonObject)
                addProperty("previousDeviceMayRemainOnHub", state.previousDeviceMayRemainOnHub)
            }
        } finally {
            identity.close()
        }
    }

    /** Locally verifies the independently owner-signed grant before sending enrollment. */
    fun enroll(
        ownerId: String,
        ownerKeyId: String,
        ownerPublicIdentityJson: String,
        deviceId: String,
        ownerDeviceGrantBase64: String,
    ): JsonObject = PROCESS_LOCK.withLock {
        val state = readState()
        val pin = state.pin ?: throw HubSessionException("HUB_NOT_PINNED", "Pin the Hub through an independent trusted channel first")
        if (state.enrollment != null) throw HubSessionException("ALREADY_ENROLLED", "This local device already has an enrollment")
        if (state.enrollmentAttempt != null) {
            throw HubSessionException("ENROLLMENT_RECOVERY_REQUIRED", "Enrollment outcome is unknown; do not repeat the device registration")
        }
        val checkedOwner = ownerId.trim()
        val checkedKeyId = ownerKeyId.trim()
        val checkedDeviceId = deviceId.trim()
        if (checkedOwner.isEmpty() || checkedKeyId.isEmpty() || checkedDeviceId.isEmpty()) {
            throw HubSessionException("INVALID_ENROLLMENT", "Owner, owner key, and device IDs are required")
        }
        val deviceIdentity = loadDeviceIdentity()
        try {
            verifyLivePinnedIdentity(pin)
            val ownerPublicJson = parseObject(ownerPublicIdentityJson, "Invalid owner approval public identity")
            val ownerPublic = try {
                ClientWireCrypto.PublicIdentity.parse(ownerPublicJson)
            } catch (_: Exception) {
                throw HubSessionException("INVALID_OWNER_IDENTITY", "Owner approval public identity is invalid")
            }
            if (ownerPublic.id != checkedKeyId) {
                throw HubSessionException("OWNER_KEY_ID_MISMATCH", "Owner key ID does not match its public identity")
            }
            val grantBytes = decodeCanonicalBase64(ownerDeviceGrantBase64)
            val grantText = String(grantBytes, StandardCharsets.UTF_8)
            try {
                ClientWireCrypto.verifyOwnerGrant(
                    grantText,
                    ownerPublic,
                    checkedOwner,
                    checkedDeviceId,
                    deviceIdentity.publicIdentity,
                    pin.hubId,
                )
            } catch (_: Exception) {
                throw HubSessionException("INVALID_OWNER_GRANT", "Owner grant signature or device binding is invalid")
            }
            val input = JsonObject().apply {
                addProperty("owner_id", checkedOwner)
                addProperty("owner_key_id", checkedKeyId)
                addProperty("device_id", checkedDeviceId)
                add("device_public_identity", JsonParser.parseString(deviceIdentity.publicIdentity.toJson()).asJsonObject)
                addProperty("owner_device_grant", Base64.encodeToString(grantBytes, Base64.NO_WRAP))
            }
            val grantDigest = MessageDigest.getInstance("SHA-256").digest(grantBytes)
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            state.enrollmentAttempt = EnrollmentAttempt(checkedOwner, checkedDeviceId, deviceIdentity.publicIdentity.id, grantDigest)
            writeState(state) // An ambiguous POST must not be recreated with a new nonce or device key.
            val response = httpJson(pin.baseUrl, "/v2/client/devices/enroll", "POST", input.toString(), 48 * 1024, expectedStatus = 201)
            val returnedOwner = requiredString(response, "owner_id")
            val returnedDevice = requiredString(response, "device_id")
            val epoch = longValue(response, "session_epoch")
            val keyVersion = longValue(response, "device_key_version")
            val deviceState = requiredString(response, "state")
            if (returnedOwner != checkedOwner || returnedDevice != checkedDeviceId || epoch < 1 || keyVersion < 1 || deviceState != "ACTIVE") {
                throw HubSessionException("INVALID_ENROLLMENT_RESPONSE", "Hub returned an enrollment that does not match this request")
            }
            state.enrollment = Enrollment(returnedOwner, returnedDevice, epoch, keyVersion, deviceState)
            state.nextSequence = 1
            state.lastResponseSequence = 0
            state.role = null
            state.sessionCapabilitiesReady = false
            state.validatedContractRevision = null
            state.validatedCatalogSha256 = null
            state.allowedOperations = emptyList()
            state.authFenced = false
            state.recoveryBlocked = false
            state.sessionError = null
            state.pending = null
            state.enrollmentAttempt = null
            writeState(state)
            JsonObject().apply {
                addProperty("ownerId", returnedOwner)
                addProperty("deviceId", returnedDevice)
                addProperty("sessionEpoch", epoch)
                addProperty("deviceKeyVersion", keyVersion)
                addProperty("state", deviceState)
            }
        } finally {
            deviceIdentity.close()
        }
    }

    /** Starts one encrypted RPC after persisting sequence and exact request bytes. */
    fun rpc(operation: String, body: JsonObject, operationId: String? = null): JsonObject = PROCESS_LOCK.withLock {
        val state = readState()
        requireReadyForRpc(state)
        if (state.pending != null) {
            throw HubSessionException("PENDING_RECOVERY_REQUIRED", "Recover the exact pending encrypted request before starting another RPC")
        }
        validateOperation(state, operation)
        createAndSend(state, operation, body, operationId)
    }

    /** Re-sends the persisted packet byte-for-byte after a transport interruption. */
    fun recoverPending(): JsonObject = PROCESS_LOCK.withLock {
        val state = readState()
        requireReadyForRpc(state)
        val pending = state.pending ?: throw HubSessionException("NO_PENDING_REQUEST", "There is no encrypted request to recover")
        if (state.recoveryBlocked) {
            throw HubSessionException("RECOVERY_PROTOCOL_REQUIRED", "Hub returned HTTP 409; keep the original ciphertext and sequence until the Hub defines safe reconciliation")
        }
        val pin = state.pin ?: throw HubSessionException("HUB_NOT_PINNED", "Hub pin is missing")
        val enrollment = state.enrollment ?: throw HubSessionException("DEVICE_NOT_ENROLLED", "Device enrollment is missing")
        val device = loadDeviceIdentity()
        try {
            val control = loadControlIdentity(pin)
            val route = routeFromPacket(pending.packetJson)
            val packetResponse = sendRpcPacket(state, pin.baseUrl, pending.packetJson)
            finishResponse(state, device, control, route, packetResponse)
        } finally {
            device.close()
        }
    }

    fun getStatus(): JsonObject = PROCESS_LOCK.withLock {
        val state = readState()
        val pending = state.pending
        val result = JsonObject().apply {
            addProperty("pinned", state.pin != null)
            addProperty("enrolled", state.enrollment != null)
            addProperty("sessionCapabilitiesReady", state.sessionCapabilitiesReady)
            addProperty("remoteEnabled", state.pin != null && state.enrollment != null &&
                state.sessionCapabilitiesReady && state.allowedOperations.isNotEmpty() &&
                state.pending == null && !state.authFenced && !state.recoveryBlocked)
            addProperty("authFenced", state.authFenced)
            addProperty("recoveryBlocked", state.recoveryBlocked)
            addProperty("previousDeviceMayRemainOnHub", state.previousDeviceMayRemainOnHub)
            addProperty("hasRetiredPending", state.retiredPending != null)
            addProperty("enrollmentRecoveryRequired", state.enrollmentAttempt != null)
            state.enrollmentAttempt?.let { attempt ->
                addProperty("pendingEnrollmentOwnerId", attempt.ownerId)
                addProperty("pendingEnrollmentDeviceId", attempt.deviceId)
            }
            if (state.pin != null) {
                addProperty("hubId", state.pin!!.hubId)
                addProperty("baseUrl", state.pin!!.baseUrl)
            }
            if (state.enrollment != null) {
                addProperty("ownerId", state.enrollment!!.ownerId)
                addProperty("deviceId", state.enrollment!!.deviceId)
            }
            if (state.role != null) addProperty("role", state.role)
            if (state.sessionError != null) addProperty("sessionError", state.sessionError)
            add("allowedOperations", JsonArray().apply { state.allowedOperations.forEach(::add) })
            if (state.enrollment != null) addProperty("nextSequence", state.nextSequence)
            if (pending != null) {
                addProperty("pendingOperationId", pending.operationId)
                addProperty("pendingOperation", pending.operation)
            }
        }
        result
    }

    private fun createAndSend(state: State, operation: String, body: JsonObject, operationId: String?): JsonObject {
        val pin = state.pin ?: throw HubSessionException("HUB_NOT_PINNED", "Hub pin is missing")
        val enrollment = state.enrollment ?: throw HubSessionException("DEVICE_NOT_ENROLLED", "Device enrollment is missing")
        val device = loadDeviceIdentity()
        try {
            val control = loadControlIdentity(pin)
            val sequence = state.nextSequence
            if (sequence < 1 || sequence == Long.MAX_VALUE) throw HubSessionException("SEQUENCE_EXHAUSTED", "Encrypted request sequence is exhausted")
            val logicalId = operationId?.trim()?.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
            if (logicalId.length > 256) throw HubSessionException("INVALID_OPERATION_ID", "Operation ID is too long")
            val route = ClientWireCrypto.Route().apply {
                version = 1
                direction = "REQUEST"
                hubId = pin.hubId
                ownerId = enrollment.ownerId
                deviceId = enrollment.deviceId
                sessionEpoch = enrollment.sessionEpoch
                this.sequence = sequence
                this.operationId = logicalId
                this.operation = operation
                senderKeyId = device.publicIdentity.id
                senderKeyVersion = enrollment.deviceKeyVersion
                receiverKeyId = control.id
                receiverKeyVersion = pin.controlKeyVersion
            }
            val bodyText = body.toString()
            if (bodyText.toByteArray(StandardCharsets.UTF_8).size > MAX_BODY_BYTES) {
                throw HubSessionException("REQUEST_TOO_LARGE", "Encrypted RPC body exceeds the protocol limit")
            }
            val exactPacket = try {
                ClientWireCrypto.sealRequest(device, control, route, bodyText)
            } catch (_: Exception) {
                throw HubSessionException("ENCRYPTION_FAILED", "Could not seal the encrypted RPC request")
            }
            if (exactPacket.toByteArray(StandardCharsets.UTF_8).size > MAX_PACKET_BYTES) {
                throw HubSessionException("REQUEST_TOO_LARGE", "Encrypted RPC packet exceeds the protocol limit")
            }
            state.nextSequence = sequence + 1
            state.pending = Pending(operation, logicalId, sequence, exactPacket)
            writeState(state) // Sequence and original ciphertext reach durable storage before network I/O.
            val response = sendRpcPacket(state, pin.baseUrl, exactPacket)
            return finishResponse(state, device, control, route, response)
        } finally {
            device.close()
        }
    }

    private fun finishResponse(
        state: State,
        device: ClientWireCrypto.Identity,
        control: ClientWireCrypto.PublicIdentity,
        route: ClientWireCrypto.Route,
        packetResponse: String,
    ): JsonObject {
        val opened = try {
            ClientWireCrypto.openResponse(device, control, route, packetResponse)
        } catch (_: Exception) {
            // Keep the original packet for exact retry if response authentication fails.
            throw HubSessionException("INVALID_ENCRYPTED_RESPONSE", "Hub response failed post-quantum authentication or decryption")
        }
        if (opened.route.sequence != state.lastResponseSequence + 1) {
            throw HubSessionException("RESPONSE_SEQUENCE_CONFLICT", "Hub response sequence is not monotonic")
        }
        val body = opened.body
        val requestId = optionalString(body, "request_id") ?: ""
        val operationId = optionalString(body, "operation_id") ?: ""
        val ok = body.get("ok")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
            ?: throw HubSessionException("INVALID_ENCRYPTED_RESPONSE", "Decrypted Hub response is missing its result status")
        if (operationId != route.operationId) throw HubSessionException("INVALID_ENCRYPTED_RESPONSE", "Decrypted operation ID does not match the request")

        if (route.operation == "session.capabilities" && ok) {
            val result = body.get("result")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: throw HubSessionException("INVALID_SESSION_CAPABILITIES", "Encrypted session.capabilities response is malformed")
            val owner = optionalString(result, "owner_id")
            val role = optionalString(result, "role")
            val operations = result.get("available_rpc_operations")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: throw HubSessionException("INVALID_SESSION_CAPABILITIES", "Encrypted session.capabilities omitted the allowed operation list")
            if (owner != state.enrollment?.ownerId || (role != "manager" && role != "external")) {
                throw HubSessionException("INVALID_SESSION_CAPABILITIES", "Encrypted session capabilities do not match this enrolled owner")
            }
            val revision = optionalString(result, "contract_revision")
            val catalog = optionalString(result, "catalog_sha256")
            if (revision != CONTRACT_REVISION || catalog != CATALOG_SHA256) {
                // The response is authenticated and final. Consume its sequence, but
                // never authorize operations from an unknown contract or catalog.
                state.role = null
                state.sessionCapabilitiesReady = false
                state.validatedContractRevision = null
                state.validatedCatalogSha256 = null
                state.allowedOperations = emptyList()
                state.lastResponseSequence = opened.route.sequence
                state.pending = null
                state.sessionError = "CONTRACT_MISMATCH"
                writeState(state)
                throw HubSessionException("CONTRACT_MISMATCH", "Encrypted Hub contract revision or catalog digest differs from this Client build")
            }
            val allowed = operations.mapNotNull { item ->
                if (item.isJsonPrimitive && item.asJsonPrimitive.isString) item.asString else null
            }.distinct().filter { it in RPC_OPERATIONS }
            if ("session.capabilities" !in allowed) {
                throw HubSessionException("INVALID_SESSION_CAPABILITIES", "Hub did not authorize session.capabilities for this device")
            }
            state.role = role
            state.allowedOperations = allowed
            state.validatedContractRevision = revision
            state.validatedCatalogSha256 = catalog
            state.sessionCapabilitiesReady = true
        } else if (route.operation == "session.capabilities") {
            state.role = null
            state.allowedOperations = emptyList()
            state.validatedContractRevision = null
            state.validatedCatalogSha256 = null
            state.sessionCapabilitiesReady = false
        }
        state.lastResponseSequence = opened.route.sequence
        state.pending = null
        state.recoveryBlocked = false
        state.sessionError = null
        writeState(state)
        return JsonObject().apply {
            addProperty("requestId", requestId)
            addProperty("operationId", operationId)
            addProperty("ok", ok)
            if (ok && body.has("result")) add("result", body.get("result"))
            if (!ok && body.has("error")) add("error", body.get("error"))
        }
    }

    private fun requireReadyForRpc(state: State) {
        if (state.pin == null) throw HubSessionException("HUB_NOT_PINNED", "Pin the Hub through an independent trusted channel first")
        if (state.authFenced) throw HubSessionException("CLIENT_SESSION_AUTH_FENCED", "Hub rejected Client authentication; remote RPC is disabled")
        if (state.enrollment == null) throw HubSessionException("DEVICE_NOT_ENROLLED", "Enroll this device with an owner-signed grant first")
        if (!privateKeyFile.baseFile.exists()) throw HubSessionException("DEVICE_KEY_MISSING", "Device private key is unavailable")
    }

    private fun validateOperation(state: State, operation: String) {
        if (operation !in RPC_OPERATIONS) throw HubSessionException("UNSUPPORTED_RPC_OPERATION", "Operation is not in the Client RPC contract")
        if (!state.sessionCapabilitiesReady) {
            if (operation != "session.capabilities") {
                throw HubSessionException("SESSION_CAPABILITIES_REQUIRED", "First encrypted RPC must be session.capabilities")
            }
        } else if (operation !in state.allowedOperations) {
            throw HubSessionException("RPC_OPERATION_NOT_AUTHORIZED", "Encrypted session.capabilities did not authorize this operation")
        }
    }

    private fun routeFromPacket(packetJson: String): ClientWireCrypto.Route = try {
        val packet = JsonParser.parseString(packetJson).asJsonObject
        ClientWireCrypto.Route.parse(packet.getAsJsonObject("route"))
    } catch (_: Exception) {
        throw HubSessionException("PENDING_PACKET_INVALID", "Persisted encrypted request cannot be recovered")
    }

    private fun loadControlIdentity(pin: Pin): ClientWireCrypto.PublicIdentity = try {
        ClientWireCrypto.PublicIdentity.parse(JsonParser.parseString(pin.controlPublicIdentityJson).asJsonObject)
    } catch (_: Exception) {
        throw HubSessionException("HUB_PIN_INVALID", "Stored Hub trust pin is invalid")
    }

    /** Confirms the endpoint still presents the independently entered trust pin. */
    private fun verifyLivePinnedIdentity(pin: Pin) {
        val liveJson = httpJson(pin.baseUrl, "/v2/client/identity", "GET", null, MAX_METADATA_BYTES)
        val live = try {
            ClientWireCrypto.HubPin.parse(liveJson.toString())
        } catch (_: Exception) {
            throw HubSessionException("HUB_IDENTITY_MISMATCH", "Hub identity endpoint does not match the supported contract")
        }
        val pinned = try {
            ClientWireCrypto.HubPin(pin.hubId, loadControlIdentity(pin), pin.controlKeyVersion)
        } catch (_: Exception) {
            throw HubSessionException("HUB_PIN_INVALID", "Stored Hub trust pin is invalid")
        }
        if (!live.sameAs(pinned)) {
            throw HubSessionException("HUB_IDENTITY_MISMATCH", "Live Hub identity differs from the independently entered pin")
        }
    }

    private fun sendRpcPacket(state: State, baseUrl: String, packetJson: String): String {
        try {
            return httpJsonRaw(baseUrl, "/v2/client/rpc", packetJson, MAX_RESPONSE_BYTES)
        } catch (error: HubSessionException) {
            if (error.errorCode == "HTTP_403") {
                state.authFenced = true
                state.sessionCapabilitiesReady = false
                state.allowedOperations = emptyList()
                state.role = null
                state.sessionError = "RPC_HTTP_403"
                // Preserve the signed ciphertext for support/audit; do not keep retrying it.
                writeState(state)
            } else if (error.errorCode == "HTTP_409") {
                state.recoveryBlocked = true
                state.sessionError = "RPC_HTTP_409_UNCERTAIN"
                // A generic conflict does not identify whether the operation ran.
                // Do not retry or retire the original ciphertext without a Hub protocol.
                writeState(state)
            }
            throw error
        }
    }

    private fun loadDeviceIdentity(): ClientWireCrypto.Identity {
        if (!privateKeyFile.baseFile.exists()) throw HubSessionException("DEVICE_KEY_MISSING", "Generate a device identity before enrollment")
        val wrapped = try {
            privateKeyFile.openRead().use { input -> JsonParser.parseReader(java.io.InputStreamReader(input, StandardCharsets.UTF_8)).asJsonObject }
        } catch (_: Exception) {
            throw HubSessionException("DEVICE_KEY_STORAGE_INVALID", "Wrapped device key storage cannot be read")
        }
        val version = longValue(wrapped, "version")
        if (version != 1L) throw HubSessionException("DEVICE_KEY_STORAGE_INVALID", "Unsupported wrapped device key version")
        val nonce = decodeBase64(requiredString(wrapped, "nonce"))
        val ciphertext = decodeBase64(requiredString(wrapped, "ciphertext"))
        if (nonce.size != 12 || ciphertext.isEmpty()) throw HubSessionException("DEVICE_KEY_STORAGE_INVALID", "Wrapped device key has invalid dimensions")
        val plaintext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrappingKey(create = false), GCMParameterSpec(128, nonce))
            cipher.updateAAD(KEY_AAD)
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            throw HubSessionException("DEVICE_KEY_UNAVAILABLE", "Android Keystore could not unwrap the device identity")
        }
        return try {
            val serialized = String(plaintext, StandardCharsets.UTF_8)
            ClientWireCrypto.Identity.fromPrivateJson(serialized)
        } catch (_: Exception) {
            throw HubSessionException("DEVICE_KEY_STORAGE_INVALID", "Unwrapped device identity is invalid")
        } finally {
            plaintext.fill(0)
        }
    }

    private fun writeWrappedPrivateJson(privateJson: String) {
        val plaintext = privateJson.toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey(create = true))
        cipher.updateAAD(KEY_AAD)
        val ciphertext = try {
            cipher.doFinal(plaintext)
        } finally {
            plaintext.fill(0)
        }
        val wrapped = JsonObject().apply {
            addProperty("version", 1)
            addProperty("nonce", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            addProperty("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        }
        val output = privateKeyFile.startWrite()
        try {
            output.write(wrapped.toString().toByteArray(StandardCharsets.UTF_8))
            output.fd.sync()
            privateKeyFile.finishWrite(output)
        } catch (error: Exception) {
            privateKeyFile.failWrite(output)
            throw HubSessionException("DEVICE_KEY_STORAGE_FAILED", "Could not persist wrapped device identity")
        } finally {
            ciphertext.fill(0)
        }
    }

    private fun getOrCreateWrappingKey(create: Boolean): SecretKey {
        try {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing
            if (!create) throw HubSessionException("DEVICE_KEY_UNAVAILABLE", "Android Keystore wrapping key is missing")
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
            generator.init(spec)
            return generator.generateKey()
        } catch (error: HubSessionException) {
            throw error
        } catch (_: Exception) {
            throw HubSessionException("KEYSTORE_UNAVAILABLE", "Android Keystore is unavailable")
        }
    }

    private fun deleteDeviceWrappingKey() {
        try {
            privateKeyFile.delete()
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
            throw HubSessionException("DEVICE_KEY_RESET_FAILED", "Could not remove the prior local device identity")
        }
    }

    private fun readState(): State {
        if (!stateFile.baseFile.exists()) return State()
        val json = try {
            stateFile.openRead().use { input -> JsonParser.parseReader(java.io.InputStreamReader(input, StandardCharsets.UTF_8)).asJsonObject }
        } catch (_: Exception) {
            throw HubSessionException("SESSION_STORAGE_INVALID", "Saved Hub session state cannot be read")
        }
        val state = State()
        state.pin = json.getAsJsonObject("pin")?.let { value ->
            Pin(
                requiredString(value, "baseUrl"),
                requiredString(value, "hubId"),
                requiredString(value, "controlPublicIdentityJson"),
                optionalLong(value, "controlKeyVersion") ?: 1L,
            )
        }
        state.enrollment = json.getAsJsonObject("enrollment")?.let { value ->
            Enrollment(
                requiredString(value, "ownerId"),
                requiredString(value, "deviceId"),
                longValue(value, "sessionEpoch"),
                longValue(value, "deviceKeyVersion"),
                requiredString(value, "state"),
            )
        }
        state.nextSequence = optionalLong(json, "nextSequence") ?: 1L
        state.lastResponseSequence = optionalLong(json, "lastResponseSequence") ?: 0L
        state.role = optionalString(json, "role")
        state.sessionCapabilitiesReady = json.get("sessionCapabilitiesReady")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        state.validatedContractRevision = optionalString(json, "validatedContractRevision")
        state.validatedCatalogSha256 = optionalString(json, "validatedCatalogSha256")
        state.authFenced = json.get("authFenced")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        state.recoveryBlocked = json.get("recoveryBlocked")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        state.sessionError = optionalString(json, "sessionError")
        state.previousDeviceMayRemainOnHub = json.get("previousDeviceMayRemainOnHub")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        state.retiredPending = json.getAsJsonObject("retiredPending")?.let { value ->
            Pending(
                requiredString(value, "operation"),
                requiredString(value, "operationId"),
                longValue(value, "sequence"),
                requiredString(value, "packetJson"),
            )
        }
        state.allowedOperations = json.getAsJsonArray("allowedOperations")?.mapNotNull { element ->
            if (element.isJsonPrimitive && element.asJsonPrimitive.isString) element.asString else null
        } ?: emptyList()
        state.pending = json.getAsJsonObject("pending")?.let { value ->
            Pending(
                requiredString(value, "operation"),
                requiredString(value, "operationId"),
                longValue(value, "sequence"),
                requiredString(value, "packetJson"),
            )
        }
        state.enrollmentAttempt = json.getAsJsonObject("enrollmentAttempt")?.let { value ->
            EnrollmentAttempt(
                requiredString(value, "ownerId"),
                requiredString(value, "deviceId"),
                requiredString(value, "deviceKeyId"),
                requiredString(value, "grantSha256"),
            )
        }
        if (state.validatedContractRevision != CONTRACT_REVISION || state.validatedCatalogSha256 != CATALOG_SHA256) {
            // An app update must not reuse an authorization from an older catalog.
            state.sessionCapabilitiesReady = false
            state.role = null
            state.allowedOperations = emptyList()
        }
        if (state.nextSequence < 1 || state.lastResponseSequence < 0) {
            throw HubSessionException("SESSION_STORAGE_INVALID", "Saved encrypted RPC sequence is invalid")
        }
        return state
    }

    private fun writeState(state: State) {
        val json = JsonObject()
        state.pin?.let { pin ->
            json.add("pin", JsonObject().apply {
                addProperty("baseUrl", pin.baseUrl)
                addProperty("hubId", pin.hubId)
                addProperty("controlPublicIdentityJson", pin.controlPublicIdentityJson)
                addProperty("controlKeyVersion", pin.controlKeyVersion)
            })
        }
        state.enrollment?.let { enrollment ->
            json.add("enrollment", JsonObject().apply {
                addProperty("ownerId", enrollment.ownerId)
                addProperty("deviceId", enrollment.deviceId)
                addProperty("sessionEpoch", enrollment.sessionEpoch)
                addProperty("deviceKeyVersion", enrollment.deviceKeyVersion)
                addProperty("state", enrollment.state)
            })
        }
        json.addProperty("nextSequence", state.nextSequence)
        json.addProperty("lastResponseSequence", state.lastResponseSequence)
        json.addProperty("role", state.role)
        json.addProperty("sessionCapabilitiesReady", state.sessionCapabilitiesReady)
        json.addProperty("validatedContractRevision", state.validatedContractRevision)
        json.addProperty("validatedCatalogSha256", state.validatedCatalogSha256)
        json.addProperty("authFenced", state.authFenced)
        json.addProperty("recoveryBlocked", state.recoveryBlocked)
        json.addProperty("sessionError", state.sessionError)
        json.addProperty("previousDeviceMayRemainOnHub", state.previousDeviceMayRemainOnHub)
        state.retiredPending?.let { pending ->
            json.add("retiredPending", JsonObject().apply {
                addProperty("operation", pending.operation)
                addProperty("operationId", pending.operationId)
                addProperty("sequence", pending.sequence)
                addProperty("packetJson", pending.packetJson)
            })
        }
        json.add("allowedOperations", JsonArray().apply { state.allowedOperations.forEach(::add) })
        state.pending?.let { pending ->
            json.add("pending", JsonObject().apply {
                addProperty("operation", pending.operation)
                addProperty("operationId", pending.operationId)
                addProperty("sequence", pending.sequence)
                addProperty("packetJson", pending.packetJson)
            })
        }
        state.enrollmentAttempt?.let { attempt ->
            json.add("enrollmentAttempt", JsonObject().apply {
                addProperty("ownerId", attempt.ownerId)
                addProperty("deviceId", attempt.deviceId)
                addProperty("deviceKeyId", attempt.deviceKeyId)
                addProperty("grantSha256", attempt.grantSha256)
            })
        }
        val output = stateFile.startWrite()
        try {
            output.write(json.toString().toByteArray(StandardCharsets.UTF_8))
            output.fd.sync()
            stateFile.finishWrite(output)
        } catch (_: Exception) {
            stateFile.failWrite(output)
            throw HubSessionException("SESSION_STORAGE_FAILED", "Could not persist encrypted RPC state")
        }
    }

    private fun validateBaseUrl(baseUrl: String): String {
        val uri = try { URI(baseUrl.trim()) } catch (_: Exception) {
            throw HubSessionException("INVALID_HUB_URL", "Hub URL is invalid")
        }
        val scheme = uri.scheme?.lowercase() ?: ""
        val host = uri.host?.lowercase() ?: ""
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
            (uri.rawPath.isNotEmpty() && uri.rawPath != "/") || host.isEmpty()) {
            throw HubSessionException("INVALID_HUB_URL", "Hub URL must contain only a scheme and host")
        }
        if (scheme == "https") {
            if (uri.port != -1 && uri.port !in 1..65535) throw HubSessionException("INVALID_HUB_URL", "Hub HTTPS port is invalid")
        } else if (scheme == "http") {
            if (!BuildConfig.DEBUG || host != "10.0.2.2" || uri.port !in setOf(8787, 8788)) {
                throw HubSessionException("INSECURE_HUB_URL", "HTTP is allowed only from a debug build to 10.0.2.2:8787 or :8788")
            }
        } else {
            throw HubSessionException("INSECURE_HUB_URL", "Hub URL must use HTTPS")
        }
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "$scheme://$host$port"
    }

    private fun httpJson(
        baseUrl: String,
        path: String,
        method: String,
        body: String?,
        maxBytes: Int,
        expectedStatus: Int = 200,
    ): JsonObject {
        val response = httpRequest(baseUrl, path, method, body, maxBytes, expectedStatus)
        return parseObject(response, "Hub returned malformed JSON")
    }

    private fun httpJsonRaw(baseUrl: String, path: String, packetJson: String, maxBytes: Int): String =
        httpRequest(baseUrl, path, "POST", packetJson, maxBytes, 200)

    private fun httpRequest(
        baseUrl: String,
        path: String,
        method: String,
        body: String?,
        maxBytes: Int,
        expectedStatus: Int,
    ): String {
        val normalizedBaseUrl = validateBaseUrl(baseUrl)
        val url = URL(normalizedBaseUrl + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = method
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Connection", "close")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(StandardCharsets.UTF_8))
                }
            }
            val status = connection.responseCode
            if (status != expectedStatus) {
                throw HubSessionException("HTTP_$status", "Hub request failed with HTTP $status")
            }
            val stream = connection.inputStream
            val bytes = stream.use { readBounded(it, maxBytes) }
            if (bytes.isEmpty()) throw HubSessionException("EMPTY_HUB_RESPONSE", "Hub returned an empty response")
            return String(bytes, StandardCharsets.UTF_8)
        } catch (error: HubSessionException) {
            throw error
        } catch (_: Exception) {
            throw HubSessionException("NETWORK_ERROR", "Hub connection failed; encrypted request state is preserved")
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw HubSessionException("HUB_RESPONSE_TOO_LARGE", "Hub response exceeds the protocol size limit")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun parseObject(text: String, message: String): JsonObject = try {
        val parsed = JsonParser.parseString(text)
        if (!parsed.isJsonObject) throw IllegalArgumentException("Expected object")
        parsed.asJsonObject
    } catch (_: Exception) {
        throw HubSessionException("INVALID_JSON", message)
    }

    private fun decodeCanonicalBase64(text: String): ByteArray {
        val decoded = decodeBase64(text)
        if (Base64.encodeToString(decoded, Base64.NO_WRAP) != text) {
            throw HubSessionException("INVALID_OWNER_GRANT", "Owner grant must use standard padded base64")
        }
        return decoded
    }

    private fun decodeBase64(text: String): ByteArray = try {
        Base64.decode(text, Base64.DEFAULT)
    } catch (_: Exception) {
        throw HubSessionException("INVALID_BASE64", "A public key or grant contains invalid base64")
    }

    private fun requiredString(value: JsonObject, key: String): String = optionalString(value, key)
        ?: throw HubSessionException("INVALID_JSON", "Hub session data is missing a required field")

    private fun optionalString(value: JsonObject, key: String): String? =
        value.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun longValue(value: JsonObject, key: String): Long = optionalLong(value, key)
        ?: throw HubSessionException("INVALID_JSON", "Hub session data is missing an integer field")

    private fun optionalLong(value: JsonObject, key: String): Long? = try {
        value.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
    } catch (_: Exception) { null }
}

class HubSessionException(val errorCode: String, message: String) : RuntimeException(message)
