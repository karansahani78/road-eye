import asyncio
import logging
import math
import time
from contextlib import asynccontextmanager
from typing import Dict, Optional

import cv2
from fastapi import FastAPI, HTTPException, Query, Request, Depends, Header
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from src.common.logging import setup_logging
from src.config import Settings
from src.stream.stream_manager import StreamManager, StreamStartResult
from src.violation.violation_detector import CameraViolationConfig

settings = Settings()
setup_logging(settings.LOG_LEVEL)
logger = logging.getLogger(__name__)
stream_manager: Optional[StreamManager] = None


class StreamStartRequest(BaseModel):
    camera_id: str
    source_url: str
    fps: int = 15
    speed_limit: Optional[float] = None
    allowed_direction: Optional[str] = None
    known_distance_m: Optional[float] = None
    reference_pixels: Optional[float] = None


class StreamStopRequest(BaseModel):
    camera_id: str


class CalibrateRequest(BaseModel):
    known_distance_m: float
    reference_pixels: Optional[float] = None
    reference_point1: Optional[str] = None
    reference_point2: Optional[str] = None
    calibration_data: Optional[str] = None


def _compute_pixel_coords(point_str: str) -> Optional[tuple]:
    if not point_str:
        return None
    try:
        parts = point_str.strip().split(",")
        if len(parts) >= 2:
            return (float(parts[0].strip()), float(parts[1].strip()))
    except (ValueError, TypeError):
        pass
    return None


def _compute_pixel_distance(point1: str, point2: str) -> Optional[float]:
    p1 = _compute_pixel_coords(point1)
    p2 = _compute_pixel_coords(point2)
    if p1 is None or p2 is None:
        return None
    dx = p2[0] - p1[0]
    dy = p2[1] - p1[1]
    return math.sqrt(dx * dx + dy * dy)


@asynccontextmanager
async def lifespan(app: FastAPI):
    global stream_manager
    stream_manager = StreamManager(settings)
    await stream_manager.start()
    logger.info("RoadEye AI Engine started")
    yield
    logger.info("Shutting down RoadEye AI Engine...")
    if stream_manager:
        await stream_manager.stop()
    logger.info("RoadEye AI Engine stopped")


app = FastAPI(title="RoadEye AI Engine", version="0.1.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


async def verify_api_key(x_api_key: str = Header("")):
    if settings.API_KEY and x_api_key != settings.API_KEY:
        raise HTTPException(status_code=401, detail="Invalid or missing API key")
    return x_api_key


@app.get("/health")
async def health():
    if stream_manager is None:
        return {"status": "starting"}
    streams = stream_manager.get_stream_info()
    active_count = sum(1 for s in streams.values() if s.get("status") == "running")
    return {
        "status": "ok",
        "active_streams": active_count,
        "streams": streams,
    }


@app.get("/api/v1/streams")
async def list_streams(api_key: str = Depends(verify_api_key)):
    if stream_manager is None:
        return {"streams": {}}
    return {"streams": stream_manager.get_stream_info()}


@app.post("/api/v1/streams/start")
async def start_stream(req: StreamStartRequest, api_key: str = Depends(verify_api_key)):
    if stream_manager is None:
        raise HTTPException(status_code=503, detail="Stream manager not ready")

    # Log safe diagnostic info — never log the password
    source_url_safe = _sanitize_url(req.source_url)
    logger.info(f"Starting stream: camera_id={req.camera_id} source_url={source_url_safe} fps={req.fps}")

    # Configure violation detection BEFORE starting stream to avoid race
    if req.speed_limit is not None or req.allowed_direction is not None:
        config = CameraViolationConfig(
            speed_limit_kmh=req.speed_limit or 60.0,
            allowed_direction=req.allowed_direction,
        )
        stream_manager.configure_violation_detection(req.camera_id, config)

    result = await stream_manager.start_stream(req.camera_id, req.source_url, req.fps)
    if result == StreamStartResult.STREAM_ALREADY_EXISTS:
        status = stream_manager.get_stream_status(req.camera_id)
        return {"status": "started", "camera_id": req.camera_id, "already_running": True, "stream": status}

    if result == StreamStartResult.CONNECTION_FAILED:
        logger.error(f"Stream connection failed for camera {req.camera_id}: source_url={source_url_safe}")
        raise HTTPException(status_code=502, detail="Unable to connect to camera stream")

    if result != StreamStartResult.SUCCESS:
        raise HTTPException(status_code=500, detail=f"Failed to start stream for camera {req.camera_id}")

    # Apply calibration if provided at stream start
    if req.known_distance_m is not None and req.reference_pixels is not None:
        processor = stream_manager.active_streams.get(req.camera_id)
        if processor:
            processor.set_calibration(req.known_distance_m, req.reference_pixels)
            logger.info(f"Calibration applied at stream start for {req.camera_id}")

    status = stream_manager.get_stream_status(req.camera_id)
    return {"status": "started", "camera_id": req.camera_id, "stream": status}


@app.post("/api/v1/streams/stop")
async def stop_stream(req: StreamStopRequest, api_key: str = Depends(verify_api_key)):
    if stream_manager is None:
        raise HTTPException(status_code=503, detail="Stream manager not ready")

    success = await stream_manager.stop_stream(req.camera_id)
    if not success:
        raise HTTPException(status_code=404, detail=f"No active stream for camera {req.camera_id}")

    return {"status": "stopped", "camera_id": req.camera_id}


@app.get("/api/v1/streams/{camera_id}/status")
async def stream_status(camera_id: str, api_key: str = Depends(verify_api_key)):
    if stream_manager is None:
        raise HTTPException(status_code=503, detail="Stream manager not ready")

    status = stream_manager.get_stream_status(camera_id)
    if status is None:
        raise HTTPException(status_code=404, detail=f"No stream for camera {camera_id}")

    return {"camera_id": camera_id, "stream": status}


@app.get("/api/v1/streams/{camera_id}/frame")
async def stream_frame(camera_id: str):
    """Get a single annotated frame as JPEG for live viewing."""
    if stream_manager is None:
        raise HTTPException(status_code=503, detail="Stream manager not ready")

    frame = await stream_manager.get_frame(camera_id)
    if frame is None:
        raise HTTPException(status_code=404, detail="No frame available")

    ret, jpeg = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 75])
    if not ret:
        raise HTTPException(status_code=500, detail="Failed to encode frame")

    return StreamingResponse(
        iter([jpeg.tobytes()]),
        media_type="image/jpeg",
        headers={
            "Cache-Control": "no-cache, no-store, must-revalidate",
            "Pragma": "no-cache",
            "Expires": "0",
        },
    )


@app.get("/api/v1/streams/{camera_id}/mjpeg")
async def stream_mjpeg(camera_id: str):
    """MJPEG live stream of annotated camera feed for browser viewing."""
    if stream_manager is None:
        raise HTTPException(status_code=503, detail="Stream manager not ready")

    async def generate_frames():
        while True:
            if stream_manager is None:
                break
            frame = await stream_manager.get_frame(camera_id)
            if frame is not None:
                ret, jpeg = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 65])
                if ret:
                    yield (
                        b'--frame\r\n'
                        b'Content-Type: image/jpeg\r\n'
                        b'Content-Length: ' + str(len(jpeg)).encode() + b'\r\n\r\n'
                        + jpeg.tobytes() + b'\r\n'
                    )
            await asyncio.sleep(0.05)  # ~20 FPS max

    return StreamingResponse(
        generate_frames(),
        media_type="multipart/x-mixed-replace; boundary=frame",
        headers={
            "Cache-Control": "no-cache, no-store, must-revalidate",
            "Pragma": "no-cache",
            "Expires": "0",
            "Access-Control-Allow-Origin": "*",
        },
    )


@app.get("/api/v1/streams/{camera_id}/configure")
async def configure_camera(
    camera_id: str,
    speed_limit: Optional[float] = Query(None),
    allowed_direction: Optional[str] = Query(None),
    api_key: str = Depends(verify_api_key),
):
    """Configure violation detection parameters for a camera."""
    if stream_manager is None:
        raise HTTPException(status_code=503, detail="Stream manager not ready")

    processor = stream_manager.active_streams.get(camera_id)
    if processor is None:
        raise HTTPException(status_code=404, detail=f"No active stream for camera {camera_id}")

    config = CameraViolationConfig(
        speed_limit_kmh=speed_limit or 60.0,
        allowed_direction=allowed_direction,
    )
    processor.violation_detector.configure_camera(camera_id, config)

    return {
        "camera_id": camera_id,
        "configured": True,
        "speed_limit": config.speed_limit_kmh,
        "allowed_direction": config.allowed_direction,
    }


@app.post("/api/v1/streams/{camera_id}/calibrate")
async def calibrate_stream(camera_id: str, req: CalibrateRequest, api_key: str = Depends(verify_api_key)):
    """Set or update camera calibration for speed estimation."""
    if stream_manager is None:
        raise HTTPException(status_code=503, detail="Stream manager not ready")

    if req.known_distance_m <= 0:
        raise HTTPException(status_code=400, detail="known_distance_m must be positive")

    # Determine reference_pixels: either direct or computed from reference points
    reference_pixels = req.reference_pixels
    if reference_pixels is None and req.reference_point1 and req.reference_point2:
        reference_pixels = _compute_pixel_distance(req.reference_point1, req.reference_point2)

    if reference_pixels is None or reference_pixels <= 0:
        raise HTTPException(
            status_code=400,
            detail="Provide either reference_pixels or both reference_point1 and reference_point2",
        )

    success = await stream_manager.calibrate_stream(
        camera_id, req.known_distance_m, reference_pixels, req.calibration_data
    )
    if not success:
        raise HTTPException(status_code=404, detail=f"No active stream for camera {camera_id}")

    return {
        "status": "calibrated",
        "camera_id": camera_id,
        "known_distance_m": req.known_distance_m,
        "reference_pixels": reference_pixels,
    }


def _sanitize_url(url: str) -> str:
    """Strip password from RTSP URL for safe logging."""
    if not url:
        return url
    try:
        # Handle rtsp://user:pass@host/path
        at_idx = url.find('@')
        if at_idx > 0:
            # Find the colon before the password
            scheme_end = url.find('://')
            if scheme_end >= 0:
                userinfo_end = scheme_end + 3
                colon_idx = url.find(':', userinfo_end)
                if 0 < colon_idx < at_idx:
                    safe = url[:colon_idx + 1] + '****' + url[at_idx:]
                    return safe
    except Exception:
        pass
    return url


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
