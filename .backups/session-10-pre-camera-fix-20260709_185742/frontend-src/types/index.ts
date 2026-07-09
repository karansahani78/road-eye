export interface User {
  id: string;
  username: string;
  email: string;
  fullName: string;
  phone: string;
  role: Role;
  active: boolean;
  lastLoginAt: string;
  createdAt: string;
}

export type Role =
  | 'SUPER_ADMIN'
  | 'SYSTEM_ADMIN'
  | 'TRAFFIC_ADMIN'
  | 'TRAFFIC_OFFICER'
  | 'SURVEILLANCE_OPERATOR'
  | 'CAMERA_OPERATOR'
  | 'VIOLATION_REVIEWER'
  | 'TRAFFIC_ANALYST'
  | 'AUDITOR'
  | 'READ_ONLY_USER';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface Camera {
  id: string;
  cameraCode: string;
  name: string;
  description: string;
  sourceType: 'RTSP' | 'VIDEO_FILE' | 'CCTV' | 'IP_CAMERA';
  sourceUrl?: string;
  sourceUrlConfigured?: boolean;
  location: string;
  latitude: number;
  longitude: number;
  direction: string;
  processingFps: number;
  resolution: string;
  active: boolean;
  healthStatus: 'ONLINE' | 'OFFLINE' | 'DEGRADED' | 'UNKNOWN';
  lastHeartbeat: string;
  lastSuccessfulFrame: string;
  locationSource?: 'DEVICE_GEOLOCATION' | 'MAP_SELECTION' | 'ADDRESS_SEARCH' | 'MANUAL_COORDINATES' | 'CAMERA_METADATA' | 'IMPORT' | 'UNKNOWN';
  locationAccuracyMeters?: number;
  createdAt: string;
  updatedAt?: string;
}

export interface CameraCalibration {
  id: string;
  cameraId: string;
  calibrationVersion: string;
  calibrationData?: string;
  referencePoint1?: string;
  referencePoint2?: string;
  knownDistance: number;
  perspectiveTransform?: string;
  algorithmVersion: string;
  calibratedBy: string;
  calibratedAt: string;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface Violation {
  id: string;
  cameraId: string;
  cameraName: string;
  timestamp: string;
  violationType: ViolationType;
  confidence: number;
  status: ViolationStatus;
  vehicleCategory: string;
  plateText: string;
  normalizedPlate: string;
  plateCountry: string;
  speedKmh: number;
  speedLimit: number;
  trackId: number;
  evidenceId: string;
  createdAt: string;
  detectorVersion?: string;
}

export type ViolationType =
  | 'OVERSPEEDING'
  | 'RED_LIGHT_VIOLATION'
  | 'WRONG_WAY_DRIVING'
  | 'ILLEGAL_PARKING'
  | 'STOP_LINE_VIOLATION'
  | 'RESTRICTED_ZONE_ENTRY'
  | 'NO_HELMET'
  | 'TRIPLE_RIDING'
  | 'BLACKLISTED_VEHICLE'
  | 'STOLEN_VEHICLE';

export type ViolationStatus =
  | 'DETECTED'
  | 'EVIDENCE_GENERATED'
  | 'PENDING_REVIEW'
  | 'UNDER_REVIEW'
  | 'CONFIRMED'
  | 'REJECTED'
  | 'NEEDS_ADDITIONAL_REVIEW'
  | 'FORWARDED_TO_AUTHORITY'
  | 'CLOSED';

export interface Alert {
  id: string;
  type: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  source: string;
  cameraId: string;
  cameraName: string;
  message: string;
  acknowledged: boolean;
  acknowledgedBy: string;
  acknowledgedAt: string;
  resolved: boolean;
  resolvedAt: string;
  createdAt: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface DashboardStats {
  totalCameras: number;
  onlineCameras: number;
  offlineCameras: number;
  activeStreams: number;
  vehiclesDetected: number;
  trafficVolume: number;
  averageSpeed: number;
  activeViolations: number;
  pendingReviews: number;
  confirmedViolations: number;
  activeCriticalAlerts: number;
}

export interface ViolationEvidence {
  id: string;
  violationId: string;
  evidenceType: string;
  filePath: string;
  bucketName: string;
  objectKey: string;
  fileSize: number;
  contentType: string;
  metadata: string;
  createdAt: string;
}

export interface AuditLog {
  id: string;
  actor: string;
  action: string;
  targetType: string;
  targetId: string;
  metadata: string;
  createdAt: string;
}

export interface Road {
  id: string;
  name: string;
  code: string;
  roadType: string;
  province: string;
  district: string;
  length: number;
  speedLimit: number;
}

export interface CameraHealthEvent {
  id: string;
  cameraId: string;
  cameraName: string;
  status: string;
  message?: string;
  errorType?: string;
  processedFrames?: number;
  droppedFrames?: number;
  inferenceLatencyMs?: number;
  recordedAt: string;
  createdAt: string;
}

export interface Incident {
  id: string;
  cameraId: string;
  cameraName: string;
  incidentType: string;
  severity: string;
  status: string;
  latitude: number;
  longitude: number;
  description: string;
  detectedAt: string;
  occurredAt: string;
  resolvedAt: string;
  assignedTo: string;
  assignedAt: string;
  resolutionNotes: string;
  source: string;
  confidence: number;
  createdAt: string;
  updatedAt: string;
}

export interface StreamStatus {
  camera_id: string;
  stream: {
    status: 'RUNNING' | 'STOPPED' | 'ERROR';
    fps: number;
    vehicles_detected: number;
    frame_count: number;
    dropped_frames: number;
    frame_ready: boolean;
    started_at: string;
    error_message?: string;
  };
}
