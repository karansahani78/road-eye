import logging
import os
from contextlib import asynccontextmanager
from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from src.config import Settings
from src.common.logging import setup_logging
from src.stream.stream_manager import StreamManager

load_dotenv()
logger = logging.getLogger(__name__)
settings = Settings()
stream_manager: StreamManager = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global stream_manager
    setup_logging()
    logger.info("RoadEye AI Engine starting...")
    stream_manager = StreamManager(settings)
    await stream_manager.start()
    yield
    logger.info("RoadEye AI Engine shutting down...")
    await stream_manager.stop()


app = FastAPI(
    title="RoadEye AI Engine",
    version="0.1.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
async def health():
    return {
        "status": "healthy",
        "service": "roadeye-ai-engine",
        "version": "0.1.0",
        "active_streams": len(stream_manager.active_streams) if stream_manager else 0,
    }


@app.get("/api/v1/streams")
async def list_streams():
    if not stream_manager:
        return {"streams": []}
    return {
        "streams": [
            {
                "camera_id": sid,
                "status": info.get("status", "unknown"),
                "fps": info.get("fps", 0),
                "vehicles_detected": info.get("vehicles_detected", 0),
            }
            for sid, info in stream_manager.get_stream_info().items()
        ]
    }


@app.post("/api/v1/streams/start")
async def start_stream(camera_id: str, source_url: str, fps: int = 15):
    if not stream_manager:
        return {"error": "Stream manager not initialized"}
    await stream_manager.start_stream(camera_id, source_url, fps)
    return {"status": "started", "camera_id": camera_id}


@app.post("/api/v1/streams/stop")
async def stop_stream(camera_id: str):
    if not stream_manager:
        return {"error": "Stream manager not initialized"}
    await stream_manager.stop_stream(camera_id)
    return {"status": "stopped", "camera_id": camera_id}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("src.main:app", host="0.0.0.0", port=8000, reload=True)
