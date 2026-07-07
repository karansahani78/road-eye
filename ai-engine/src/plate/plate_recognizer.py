import logging
import re
from typing import List, Optional, Tuple
import cv2
import numpy as np

from src.common.models import PlateResult, Track, Detection

logger = logging.getLogger(__name__)


class PlateRecognizer:
    def __init__(self):
        self.reader = None
        self._load_reader()

    def _load_reader(self):
        try:
            import easyocr
            self.reader = easyocr.Reader(['en'], gpu=False)
            logger.info("EasyOCR reader initialized")
        except Exception as e:
            logger.warning(f"EasyOCR not available: {e}")
            self.reader = None

    def recognize(self, frame: np.ndarray,
                  tracks: List[Track]) -> List[PlateResult]:
        if self.reader is None:
            return []

        results = []
        for track in tracks:
            if not track.detections:
                continue
            det = track.detections[-1]
            plate = self._detect_plate_in_roi(frame, det.bbox)
            if plate:
                results.append(plate)

        return results

    def _detect_plate_in_roi(self, frame: np.ndarray,
                              bbox: List[float]) -> Optional[PlateResult]:
        try:
            x1, y1, x2, y2 = [int(v) for v in bbox]
            x1, y1 = max(0, x1), max(0, y1)
            x2 = min(frame.shape[1], x2)
            y2 = min(frame.shape[0], y2)

            roi = frame[y1:y2, x1:x2]
            if roi.size == 0:
                return None

            h, w = roi.shape[:2]
            bottom_roi = roi[int(h*0.6):h, :]

            if bottom_roi.size == 0:
                return None

            results = self.reader.readtext(bottom_roi)

            if not results:
                return None

            best = max(results, key=lambda r: r[2])
            bbox_coords, text, confidence = best

            if confidence < 0.3:
                return None

            normalized, country, country_conf = self._normalize_plate(text)

            return PlateResult(
                plate_text=text,
                normalized_plate=normalized,
                country=country,
                country_confidence=country_conf,
                ocr_confidence=confidence,
                bbox=bbox_coords,
                quality_score=confidence,
            )
        except Exception as e:
            logger.error(f"Plate recognition error: {e}")
            return None

    def _normalize_plate(self, text: str) -> Tuple[str, str, float]:
        cleaned = re.sub(r'[^A-Za-z0-9]', '', text).upper()

        # Nepal plate format: XX X XXXX (e.g., BA 1 2345)
        nepal_pattern = r'^[A-Z]{2}\d{1,2}\d{4}$'
        # India plate format: XX-XX-XXXX (e.g., UP 14 AB 1234)
        india_pattern = r'^[A-Z]{2}\d{2}[A-Z]{2}\d{4}$'

        if re.match(nepal_pattern, cleaned):
            return cleaned, "NEPAL", 0.9
        elif re.match(india_pattern, cleaned):
            return cleaned, "INDIA", 0.85
        elif len(cleaned) >= 5 and any(c.isdigit() for c in cleaned):
            return cleaned, "UNKNOWN", 0.5
        else:
            return cleaned, "UNKNOWN", 0.3
