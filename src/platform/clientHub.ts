import {NativeModules, Platform} from 'react-native';
import type {
  MonitorBroadcastConfirmResult, MonitorBroadcastOperation, MonitorBroadcastPreview,
  MonitorBroadcastStatus,
} from './monitorBroadcast';
import {
  monitorConfirmResultFromNative, monitorPreviewResultFromNative,
  monitorStatusResultFromNative,
} from './monitorBroadcast';

export const CLIENT_HUB_V13_REVISION = 'client-hub-v1.3' as const;
export const CLIENT_HUB_V13_CATALOG_SHA256 =
  '808f9f635effc5fa845572b976c89696ea2bb86a6a9b6f326e49d1409b570377' as const;

export type ClientRpcOperation =
  | 'session.capabilities'
  | 'status.snapshot'
  | 'status.changes'
  | 'goal.lifecycle'
  | 'goal.result'
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
  | 'link.invite_accept'
  | 'group.key_manifest'
  | 'group.key_grant'
  | 'group.key_status'
  | 'link.key_manifest'
  | 'link.key_grants'
  | 'link.key_grant'
  | 'monitor.broadcast_prepare'
  | 'monitor.broadcast_confirm'
  | 'monitor.broadcast_recover'
  | 'monitor.broadcast_status';
export type MonitorBroadcastRpcOperation = Extract<ClientRpcOperation,
  'monitor.broadcast_prepare' | 'monitor.broadcast_confirm' |
  'monitor.broadcast_recover' | 'monitor.broadcast_status'>;
export type ClientHubGenericRpcOperation = Exclude<ClientRpcOperation, MonitorBroadcastRpcOperation>;

// Link key consent remains unavailable until its separate contract is verified.
export type ClientHubAdvertisedOperation = ClientRpcOperation;

export interface ClientHubCapabilities {
  contract: string;
  contract_revision: typeof CLIENT_HUB_V13_REVISION;
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

export interface ClientGoalResultRequest {
  intent_id: string;
}

export interface ClientGoalResultWorker {
  worker_id: string;
  status: string;
  attempt: number;
  summary?: string;
}

export interface ClientGoalArtifactReference {
  artifact_id: string;
  name: string;
  kind: string;
  digest?: string;
  status: string;
}

export interface ClientGoalResult {
  intent_id: string;
  intent_status: string;
  goal_id?: string;
  goal_status?: string;
  outcome?: string;
  summary?: string;
  workers: ClientGoalResultWorker[];
  artifacts: ClientGoalArtifactReference[];
  workers_truncated: boolean;
  artifacts_truncated: boolean;
}

export interface ClientHubSessionCapabilities {
  owner_id: string;
  role: 'manager' | 'external' | string;
  contract_revision: typeof CLIENT_HUB_V13_REVISION;
  catalog_sha256: string;
  rpc_recovery?: boolean;
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
  outcomeUncertainOperationId?: string;
  uncertainNeedsReconciliation?: boolean;
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
  outcomeUncertainOperationId?: string;
  uncertainNeedsReconciliation?: boolean;
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
  recoverEnrollment(): Promise<ClientHubEnrollment>;
  rpc(input: {
    operation: ClientRpcOperation;
    body: Record<string, unknown>;
    operationId?: string;
  }): Promise<unknown>;
  recoverPending(): Promise<unknown>;
  retryPendingExact(): Promise<unknown>;
  previewGroupKey(input: {groupId: string; endpointId: string; ownerKeyId: string}): Promise<{
    ok: boolean; verified: true; result: Record<string, unknown>;
  }>;
  grantGroupKey(input: {groupId: string; endpointId: string; ownerKeyId: string;
    expectedDigest: string; signedProofBase64: string}): Promise<unknown>;
  getGroupKeyStatus(input: {groupId: string; endpointId: string}): Promise<{
    ok: boolean; result?: Record<string, unknown>;
  }>;
  getMonitorBroadcastOperations?(): Promise<{operations: MonitorBroadcastOperation[]}>;
  monitorBroadcastPrepare?(input: {operationId?: string; groupId: string;
    monitorEndpointId: string; body: string}): Promise<unknown>;
  monitorBroadcastRecover?(input: {operationId: string}): Promise<unknown>;
  monitorBroadcastBodyMatches?(input: {previewId: string; body: string}):
    Promise<{matches: boolean; bodySha256: string}>;
  monitorBroadcastConfirm?(input: {previewId: string; body: string;
    consentDigest: string}): Promise<unknown>;
  monitorBroadcastStatus?(input: {previewId: string}): Promise<unknown>;
}

export interface ClientHubApi extends Omit<ClientHubNativeBridge,
  'getStatus' | 'getGroupKeyStatus' | 'rpc' | 'getMonitorBroadcastOperations' |
  'monitorBroadcastPrepare' | 'monitorBroadcastRecover' |
  'monitorBroadcastBodyMatches' |
  'monitorBroadcastConfirm' | 'monitorBroadcastStatus'> {
  getStatus(): Promise<ClientHubStatus>;
  rpc(input: {operation: ClientHubGenericRpcOperation; body: Record<string, unknown>;
    operationId?: string}): Promise<unknown>;
  goalResult(input: ClientGoalResultRequest): Promise<ClientGoalResult>;
  groupKeyStatus(input: {groupId: string; endpointId: string}): Promise<{
    ok: boolean; result?: Record<string, unknown>;
  }>;
  readonly monitorBroadcastReady: boolean;
  getMonitorBroadcastOperations(): Promise<{operations: MonitorBroadcastOperation[]}>;
  monitorBroadcastPrepare(input: {operationId?: string; groupId: string;
    monitorEndpointId: string; body: string}): Promise<MonitorBroadcastPreview>;
  monitorBroadcastRecover(input: {operationId: string}): Promise<MonitorBroadcastPreview>;
  monitorBroadcastBodyMatches(input: {previewId: string; body: string}):
    Promise<{matches: boolean; bodySha256: string}>;
  monitorBroadcastConfirm(input: {previewId: string; body: string;
    consentDigest: string}): Promise<MonitorBroadcastConfirmResult>;
  monitorBroadcastStatus(input: {previewId: string}): Promise<MonitorBroadcastStatus>;
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
  'session.capabilities', 'status.snapshot', 'status.changes', 'goal.lifecycle', 'goal.result',
  'topology.snapshot', 'topology.apply', 'devices.list', 'devices.revoke',
  'nodes.preview', 'nodes.confirm', 'nodes.list', 'nodes.revoke', 'approvals.list',
  'approvals.decide', 'intent.get', 'intent.status', 'intent.list', 'intent.submit',
  'link.list',
  'link.invite_create', 'link.invite_preview', 'link.invite_accept',
  'group.key_manifest', 'group.key_grant', 'group.key_status',
  'link.key_manifest', 'link.key_grants', 'link.key_grant',
  'monitor.broadcast_prepare', 'monitor.broadcast_confirm',
  'monitor.broadcast_recover', 'monitor.broadcast_status',
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
  monitorBroadcastReady: Boolean(native.getMonitorBroadcastOperations &&
    native.monitorBroadcastPrepare && native.monitorBroadcastRecover &&
    native.monitorBroadcastBodyMatches && native.monitorBroadcastConfirm &&
    native.monitorBroadcastStatus),
  getStatus: async () => normalizeStatus(await native.getStatus()),
  fetchHubMetadata: input => native.fetchHubMetadata(input),
  pinHub: input => native.pinHub(input),
  createDeviceIdentity: () => native.createDeviceIdentity(),
  startNewDeviceEnrollment: () => native.startNewDeviceEnrollment(),
  enroll: input => native.enroll(input),
  recoverEnrollment: () => native.recoverEnrollment(),
  goalResult: async input => {
    const status = normalizeStatus(await native.getStatus());
    if (!operationAllowed(status, 'goal.result')) {
      throw new Error('当前加密 session.capabilities 未授权 goal.result');
    }
    return native.rpc({operation: 'goal.result', body: {intent_id: input.intent_id}}) as Promise<ClientGoalResult>;
  },
  rpc: input => native.rpc(input),
  recoverPending: () => native.recoverPending(),
  retryPendingExact: () => native.retryPendingExact(),
  previewGroupKey: input => native.previewGroupKey(input),
  grantGroupKey: input => native.grantGroupKey(input),
  groupKeyStatus: input => native.getGroupKeyStatus(input),
  getMonitorBroadcastOperations: async () => {
    if (!native.getMonitorBroadcastOperations) return unavailable();
    return native.getMonitorBroadcastOperations();
  },
  monitorBroadcastPrepare: async input => {
    if (!native.monitorBroadcastPrepare) return unavailable();
    return monitorPreviewResultFromNative(await native.monitorBroadcastPrepare(input), {
      operationId: input.operationId,
      groupId: input.groupId,
      monitorEndpointId: input.monitorEndpointId,
    });
  },
  monitorBroadcastRecover: async input => {
    if (!native.monitorBroadcastRecover) return unavailable();
    return monitorPreviewResultFromNative(await native.monitorBroadcastRecover(input), {
      operationId: input.operationId,
    });
  },
  monitorBroadcastBodyMatches: async input => {
    if (!native.monitorBroadcastBodyMatches) return unavailable();
    return native.monitorBroadcastBodyMatches(input);
  },
  monitorBroadcastConfirm: async input => {
    if (!native.monitorBroadcastConfirm) return unavailable();
    return monitorConfirmResultFromNative(
      await native.monitorBroadcastConfirm(input), input.previewId,
    );
  },
  monitorBroadcastStatus: async input => {
    if (!native.monitorBroadcastStatus) return unavailable();
    return monitorStatusResultFromNative(
      await native.monitorBroadcastStatus(input), input.previewId,
    );
  },
} : {
  monitorBroadcastReady: false,
  getStatus: async () => unavailableStatus,
  fetchHubMetadata: () => unavailable(),
  pinHub: () => unavailable(),
  createDeviceIdentity: () => unavailable(),
  startNewDeviceEnrollment: () => unavailable(),
  enroll: () => unavailable(),
  recoverEnrollment: () => unavailable(),
  goalResult: () => unavailable(),
  rpc: () => unavailable(),
  recoverPending: () => unavailable(),
  retryPendingExact: () => unavailable(),
  previewGroupKey: () => unavailable(),
  grantGroupKey: () => unavailable(),
  groupKeyStatus: () => unavailable(),
  getMonitorBroadcastOperations: () => unavailable(),
  monitorBroadcastPrepare: () => unavailable(),
  monitorBroadcastRecover: () => unavailable(),
  monitorBroadcastBodyMatches: () => unavailable(),
  monitorBroadcastConfirm: () => unavailable(),
  monitorBroadcastStatus: () => unavailable(),
};

export function operationAllowed(
  status: ClientHubStatus,
  operation: ClientRpcOperation,
): boolean {
  if (status.uncertainNeedsReconciliation && [
    'goal.lifecycle', 'topology.apply', 'devices.revoke', 'nodes.confirm',
    'nodes.revoke', 'approvals.decide', 'intent.submit', 'link.invite_create',
    'link.invite_accept', 'group.key_grant', 'monitor.broadcast_prepare',
    'monitor.broadcast_confirm',
  ].includes(operation)) return false;
  return status.nativeAvailable && status.remoteEnabled &&
    status.sessionCapabilitiesReady && !status.pendingOperationId &&
    status.allowedOperations.includes(operation);
}
