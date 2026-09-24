import {NativeModules, Platform} from 'react-native';

export type ClientRpcOperation =
  | 'session.capabilities'
  | 'status.snapshot'
  | 'status.changes'
  | 'goal.lifecycle'
  | 'topology.snapshot'
  | 'topology.apply'
  | 'devices.list'
  | 'devices.revoke'
  | 'nodes.preview'
  | 'nodes.confirm'
  | 'nodes.list'
  | 'nodes.revoke'
  | 'approvals.list'
  | 'approvals.decide'
  | 'intent.get'
  | 'intent.status'
  | 'intent.list'
  | 'intent.submit'
  | 'link.list'
  | 'link.invite_create'
  | 'link.invite_preview'
  | 'link.invite_accept';

// Advertised by v1.1, but intentionally absent from the callable Client RPC
// union until canonical manifests and Endpoint attestations can be verified.
export type ClientHubAdvertisedOperation = ClientRpcOperation |
  'link.key_manifest' | 'link.key_grants' | 'link.key_grant' |
  'group.key_manifest' | 'group.key_grant' | 'group.key_status';

export interface ClientHubCapabilities {
  contract: string;
  contract_revision: 'client-hub-v1.1';
  catalog_sha256: string;
  status: 'partial' | 'not_ready' | string;
  planned_platform: string;
  legacy_management_api_available: boolean;
  client_control_pq_e2ee: boolean;
  authenticated_client_session: boolean;
  status_snapshot: boolean;
  topology_management: boolean;
  status_events: boolean;
  status_changes_partial: boolean;
  control_intents: boolean;
  device_binding: boolean;
  external_thread_links: boolean;
  external_link_invites: boolean;
  external_client_sessions: boolean;
  link_key_consent: boolean;
  group_endpoint_key_grants: boolean;
  client_device_enrollment: boolean;
  client_device_management: boolean;
  approval_read_and_decide: boolean;
  queued_goal_lifecycle: boolean;
  available_rpc_operations: ClientHubAdvertisedOperation[];
  external_rpc_operations: ClientHubAdvertisedOperation[];
}

export interface ClientHubPublicIdentity {
  id: string;
  kem_public: string;
  signing_public: string;
}

export interface ClientHubIdentity {
  contract: 'android-hub-v1';
  hub_id: string;
  control_public_identity: ClientHubPublicIdentity;
  control_key_version: 1;
  suite: 'ML-KEM-768+ML-DSA-65+AES-256-GCM';
}

export interface ClientHubCandidate {
  trusted: false;
  capabilities: ClientHubCapabilities;
  identity: ClientHubIdentity;
}

export interface ClientHubDeviceIdentity {
  id: string;
  kem_public: string;
  signing_public: string;
}

export interface ClientHubEnrollment {
  ownerId: string;
  deviceId: string;
  sessionEpoch: number;
  deviceKeyVersion: number;
  state: 'ACTIVE' | string;
}

export interface ClientHubSessionCapabilities {
  owner_id: string;
  role: 'manager' | 'external' | string;
  contract_revision: 'client-hub-v1.1';
  catalog_sha256: string;
  available_rpc_operations: ClientHubAdvertisedOperation[];
}

export interface ClientHubStatus {
  nativeAvailable: boolean;
  pinned: boolean;
  enrolled: boolean;
  sessionCapabilitiesReady: boolean;
  remoteEnabled: boolean;
  authFenced: boolean;
  recoveryBlocked?: boolean;
  enrollmentRecoveryRequired?: boolean;
  pendingEnrollmentOwnerId?: string;
  pendingEnrollmentDeviceId?: string;
  sessionError?: string;
  baseUrl?: string;
  hubId?: string;
  ownerId?: string;
  deviceId?: string;
  sessionEpoch?: number;
  role?: 'manager' | 'external' | string;
  nextSequence?: number;
  pendingOperationId?: string;
  pendingOperation?: ClientRpcOperation;
  allowedOperations: ClientRpcOperation[];
  error?: string;
}

export interface NativeClientHubStatus {
  pinned: boolean;
  enrolled: boolean;
  sessionCapabilitiesReady: boolean;
  remoteEnabled: boolean;
  authFenced: boolean;
  recoveryBlocked?: boolean;
  enrollmentRecoveryRequired?: boolean;
  pendingEnrollmentOwnerId?: string;
  pendingEnrollmentDeviceId?: string;
  sessionError?: string;
  baseUrl?: string;
  hubId?: string;
  ownerId?: string;
  deviceId?: string;
  role?: string;
  nextSequence?: number;
  pendingOperationId?: string;
  pendingOperation?: string;
  allowedOperations: string[];
}

export interface ClientHubNativeBridge {
  getStatus(): Promise<NativeClientHubStatus>;
  fetchHubMetadata(input: {baseUrl: string}): Promise<ClientHubCandidate>;
  pinHub(input: {
    baseUrl: string;
    hubId: string;
    controlPublicIdentityJson: string;
  }): Promise<{pinned: true; hubId: string; baseUrl: string; controlKeyId: string}>;
  createDeviceIdentity(): Promise<{devicePublicIdentity: ClientHubDeviceIdentity}>;
  startNewDeviceEnrollment(): Promise<{
    devicePublicIdentity: ClientHubDeviceIdentity;
    previousDeviceMayRemainOnHub: boolean;
  }>;
  enroll(input: {
    ownerId: string;
    ownerKeyId: string;
    ownerPublicIdentityJson: string;
    deviceId: string;
    ownerDeviceGrantBase64: string;
  }): Promise<ClientHubEnrollment>;
  rpc(input: {
    operation: ClientRpcOperation;
    body: Record<string, unknown>;
    operationId?: string;
  }): Promise<unknown>;
  recoverPending(): Promise<unknown>;
}

export interface ClientHubApi extends Omit<ClientHubNativeBridge, 'getStatus'> {
  getStatus(): Promise<ClientHubStatus>;
}

const native = NativeModules.CicadaClientHub as ClientHubNativeBridge | undefined;

const unavailableStatus: ClientHubStatus = {
  nativeAvailable: false,
  pinned: false,
  enrolled: false,
  sessionCapabilitiesReady: false,
  remoteEnabled: false,
  authFenced: false,
  allowedOperations: [],
  error: '此 Android 构建尚未提供经验证的 Client-Control PQ 加密桥接。远程操作已关闭。',
};

const unavailable = async (): Promise<never> => {
  throw new Error(unavailableStatus.error);
};

const operationNames: ClientRpcOperation[] = [
  'session.capabilities', 'status.snapshot', 'status.changes', 'goal.lifecycle',
  'topology.snapshot', 'topology.apply', 'devices.list', 'devices.revoke',
  'nodes.preview', 'nodes.confirm', 'nodes.list', 'nodes.revoke', 'approvals.list',
  'approvals.decide', 'intent.get', 'intent.status', 'intent.list', 'intent.submit',
  'link.list',
  'link.invite_create', 'link.invite_preview', 'link.invite_accept',
];
const isRpcOperation = (value: string): value is ClientRpcOperation =>
  operationNames.includes(value as ClientRpcOperation);
const normalizeStatus = (value: NativeClientHubStatus): ClientHubStatus => ({
  ...value,
  nativeAvailable: true,
  allowedOperations: value.allowedOperations.filter(isRpcOperation),
  pendingOperation: value.pendingOperation && isRpcOperation(value.pendingOperation) ?
    value.pendingOperation : undefined,
});

/**
 * Narrow platform boundary for Client ↔ Hub. All sensitive RPC sealing,
 * sequence persistence, response verification and private-key handling stay
 * inside the Android bridge. This facade never exposes a /v1 bearer route.
 */
export const clientHub: ClientHubApi = Platform.OS === 'android' && native ? {
  getStatus: async () => normalizeStatus(await native.getStatus()),
  fetchHubMetadata: input => native.fetchHubMetadata(input),
  pinHub: input => native.pinHub(input),
  createDeviceIdentity: () => native.createDeviceIdentity(),
  startNewDeviceEnrollment: () => native.startNewDeviceEnrollment(),
  enroll: input => native.enroll(input),
  rpc: input => native.rpc(input),
  recoverPending: () => native.recoverPending(),
} : {
  getStatus: async () => unavailableStatus,
  fetchHubMetadata: () => unavailable(),
  pinHub: () => unavailable(),
  createDeviceIdentity: () => unavailable(),
  startNewDeviceEnrollment: () => unavailable(),
  enroll: () => unavailable(),
  rpc: () => unavailable(),
  recoverPending: () => unavailable(),
};

export function operationAllowed(
  status: ClientHubStatus,
  operation: ClientRpcOperation,
): boolean {
  return status.nativeAvailable && status.remoteEnabled &&
    status.sessionCapabilitiesReady && !status.pendingOperationId &&
    status.allowedOperations.includes(operation);
}
