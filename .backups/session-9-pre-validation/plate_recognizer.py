import logging
import re
from collections import Counter
from typing import Dict, List, Optional, Tuple

import cv2
import numpy as np

from src.common.models import PlateResult, Track
from src.detection.plate_detector import PlateDetector

logger = logging.getLogger(__name__)


# Temporal OCR consensus: keep a rolling history per track
OCR_CONSENSUS_HISTORY = 5


class PlateRecognizer:
    def __init__(self, settings):
        self.reader = None
        self.ocr_confidence_threshold = settings.PLATE_DETECTION_CONFIDENCE
        self.plate_detector = PlateDetector(
            model_path=settings.PLATE_MODEL_PATH,
            conf_threshold=settings.PLATE_DETECTION_CONFIDENCE,
        )
        # Per-track OCR history for temporal consensus: {track_id: [(text, conf, ts), ...]}
        self._track_ocr_history: Dict[int, List[Tuple[str, float, float]]] = {}
        self._load_reader()

    def _load_reader(self):
        try:
            import easyocr
            self.reader = easyocr.Reader(['en'], gpu=False)
            logger.info("EasyOCR reader initialized")
        except Exception as e:
            logger.error(f"Failed to initialize EasyOCR: {e}")
            self.reader = None

    def recognize(self, frame: np.ndarray, tracks: List[Track]) -> List[PlateResult]:
        """Legacy batch method — processes all tracks every frame."""
        if self.reader is None or not tracks:
            return []

        results: List[PlateResult] = []
        for track in tracks:
            if track.lost or track.completed or not track.detections:
                continue
            det = track.detections[-1]
            plate = self._detect_plate_in_roi(frame, det.bbox, track.track_id)
            if plate is not None:
                results.append(plate)
        return results

    def recognize_single(self, frame: np.ndarray, track: Track) -> List[PlateResult]:
        """Per-track OCR with temporal consensus. Call per-track per-frame as scheduled."""
        if self.reader is None or track.lost or track.completed or not track.detections:
            return []

        det = track.detections[-1]
        plate = self._detect_plate_in_roi(frame, det.bbox, track.track_id)
        if plate is None:
            return []

        # Update temporal history for this track
        tid = track.track_id
        if tid not in self._track_ocr_history:
            self._track_ocr_history[tid] = []
        history = self._track_ocr_history[tid]
        history.append((plate.normalized_plate, plate.ocr_confidence, plate.quality_score or 0))
        if len(history) > OCR_CONSENSUS_HISTORY:
            history.pop(0)

        # Temporal consensus: choose the text that appears most frequently
        # among high-confidence results in the recent window
        if len(history) >= 2:
            texts = [h[0] for h in history if h[1] >= self.ocr_confidence_threshold]
            if texts:
                most_common = Counter(texts).most_common(1)[0]
                consensus_text = most_common[0]
                consensus_count = most_common[1]
                if consensus_count >= max(2, len(history) // 2):
                    best_match = max(
                        (h for h in history if h[0] == consensus_text),
                        key=lambda x: x[1],
                        default=None,
                    )
                    if best_match is not None:
                        normalized, country, country_conf = self._normalize_plate(consensus_text)
                        return [PlateResult(
                            plate_text=consensus_text,
                            normalized_plate=normalized if normalized != plate.normalized_plate else consensus_text,
                            country=country,
                            country_confidence=country_conf,
                            ocr_confidence=round(best_match[1], 4),
                            quality_score=round(best_match[2], 4),
                            track_id=tid,
                            bbox=plate.bbox,
                            alternatives=plate.alternatives,
                            detector_version=self.plate_detector.model_version,
                            ocr_model_version="1.0",
                            plate_detector_version=self.plate_detector.model_version,
                        )]

        return [plate]

    def _locate_plate_roi(self, frame: np.ndarray, vehicle_bbox: List[float]) -> Optional[np.ndarray]:
        """Use dedicated plate detector to locate plate within vehicle bbox.
        Falls back to bottom-50% crop if plate detector is unavailable."""
        plate_dets = self.plate_detector.detect(frame, roi_bbox=vehicle_bbox)
        if plate_dets:
            best = max(plate_dets, key=lambda p: p.confidence)
            h, w = frame.shape[:2]
            x1 = max(0, int(best.bbox[0]))
            y1 = max(0, int(best.bbox[1]))
            x2 = min(w, int(best.bbox[2]))
            y2 = min(h, int(best.bbox[3]))
            if x2 > x1 and y2 > y1:
                return frame[y1:y2, x1:x2], best.bbox
        return None, None

    def _detect_plate_in_roi(self, frame: np.ndarray, bbox: List[float],
                             track_id: int) -> Optional[PlateResult]:
        h, w = frame.shape[:2]
        x1 = max(0, int(bbox[0]))
        y1 = max(0, int(bbox[1]))
        x2 = min(w, int(bbox[2]))
        y2 = min(h, int(bbox[3]))

        if x2 <= x1 or y2 <= y1:
            return None

        # Try dedicated plate detector first
        plate_roi, plate_bbox = self._locate_plate_roi(frame, bbox)
        plate_bbox_offset = plate_bbox

        if plate_roi is None:
            # Fallback: crop bottom 50% of vehicle bbox
            plate_roi_y1 = y1 + int((y2 - y1) * 0.50)
            plate_roi = frame[plate_roi_y1:y2, x1:x2]
            plate_bbox_offset = [x1, plate_roi_y1, x2, y2]

        if plate_roi is None or plate_roi.size == 0:
            return None

        # Preprocess: resize if too small, convert to grayscale, enhance contrast
        orig_h_r, orig_w_r = plate_roi.shape[:2]
        if orig_h_r < 24 or orig_w_r < 48:
            scale = max(2.5, 48.0 / max(orig_w_r, 1), 24.0 / max(orig_h_r, 1))
            plate_roi = cv2.resize(plate_roi, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)

        gray = cv2.cvtColor(plate_roi, cv2.COLOR_RGB2GRAY)

        clahe = cv2.createCLAHE(clipLimit=1.5, tileGridSize=(8, 8))
        enhanced = clahe.apply(gray)

        if enhanced.shape[0] > 30 and enhanced.shape[1] > 60:
            blurred = cv2.GaussianBlur(enhanced, (0, 0), 1.0)
            enhanced = cv2.addWeighted(enhanced, 1.5, blurred, -0.5, 0)

        try:
            ocr_results = self.reader.readtext(enhanced, paragraph=False)
        except Exception as e:
            logger.warning(f"OCR error for track {track_id}: {e}")
            return None

        best_text = None
        best_confidence = 0.0
        best_bbox = None
        alternatives: List[str] = []

        for (corners, text, conf) in ocr_results:
            if conf < self.ocr_confidence_threshold:
                continue
            cleaned = re.sub(r'[^A-Za-z0-9]', '', text).upper()
            if not cleaned or len(cleaned) < 3 or len(cleaned) > 15:
                continue
            alternatives.append(cleaned)
            if conf > best_confidence:
                best_text = cleaned
                best_confidence = conf
                if corners:
                    best_bbox = [float(corners[0][0]) + plate_bbox_offset[0],
                                 float(corners[0][1]) + plate_bbox_offset[1],
                                 float(corners[2][0]) + plate_bbox_offset[0],
                                 float(corners[2][1]) + plate_bbox_offset[1]]

        if best_text is None:
            return None

        normalized, country, country_conf = self._normalize_plate(best_text)
        return PlateResult(
            plate_text=best_text,
            normalized_plate=normalized,
            country=country,
            country_confidence=country_conf,
            ocr_confidence=round(best_confidence, 4),
            quality_score=round(best_confidence, 4),
            track_id=track_id,
            bbox=best_bbox or plate_bbox_offset,
            alternatives=alternatives,
            detector_version=self.plate_detector.model_version,
            ocr_model_version="1.0",
            plate_detector_version=self.plate_detector.model_version,
        )

    def _normalize_plate(self, text: str) -> Tuple[str, str, float]:
        # Nepal format: XX XX XXXX (e.g., "BA 1 2345" or "BA 12 3456")
        nepal_patterns = [
            r'^[A-Z]{2}\d{1,2}\d{4}$',
            r'^[A-Z]{2}\d{2}\d{4}$',
        ]
        for pattern in nepal_patterns:
            if re.match(pattern, text):
                return text, "NEPAL", 0.9

        # India format: XX XX XXXX (e.g., "DL 01 AB 1234")
        india_pattern = r'^[A-Z]{2}\d{2}[A-Z]{2}\d{4}$'
        if re.match(india_pattern, text):
            return text, "INDIA", 0.85

        # Broader India format with single letter state code
        broad_india = r'^[A-Z]{1,2}\d{1,2}[A-Z]{1,2}\d{1,4}$'
        if re.match(broad_india, text):
            return text, "INDIA", 0.7

        return text, "UNKNOWN", 0.5
