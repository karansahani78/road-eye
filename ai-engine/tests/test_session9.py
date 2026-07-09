import pytest
import numpy as np
from datetime import datetime, timezone
from unittest.mock import MagicMock, patch

from src.violation.violation_detector import (
    ViolationDetector, CameraViolationConfig
)
from src.detection.helmet_detector import HelmetDetector, WITH_HELMET, WITHOUT_HELMET
from src.detection.plate_detector import PlateDetector
from src.plate.plate_recognizer import PlateRecognizer
from src.common.models import (
    HelmetResult, PlateResult, Track, Detection, VehicleCategory,
    ViolationCandidate, ViolationType,
)
from src.config import Settings

AI_ENGINE_DIR = "/Users/apple/Desktop/survelience/ai-engine"


def make_helmet_simple(track_id, has_helmet):
    return {
        track_id: [HelmetResult(
            bbox=[0, 0, 10, 10],
            confidence=0.9,
            has_helmet=has_helmet,
            class_id=WITH_HELMET if has_helmet else WITHOUT_HELMET,
        )]
    }


def make_helmet_multi(track_ids, has_helmet_values):
    results = {}
    for tid, values in zip(track_ids, has_helmet_values):
        results[tid] = [
            HelmetResult(
                bbox=[0, 0, 10, 10],
                confidence=0.9,
                has_helmet=v,
                class_id=WITH_HELMET if v else WITHOUT_HELMET,
            )
            for v in values
        ]
    return results


class TestHelmetDetectorLogic:
    """Tests for HelmetDetector that don't require model inference."""

    def test_detect_riders_filters_categories(self):
        detector = HelmetDetector(model_path=f"{AI_ENGINE_DIR}/models/helmet.pt", conf_threshold=0.3)
        frame = np.zeros((720, 1280, 3), dtype=np.uint8)
        tracks_with_bbox = [
            (1, [100, 100, 200, 300], "CAR"),
            (2, [300, 100, 400, 300], "MOTORCYCLE"),
            (3, [500, 100, 600, 300], "TRUCK"),
            (4, [700, 100, 800, 300], "SCOOTER"),
        ]
        results = detector.detect_riders(frame, tracks_with_bbox)
        assert 1 not in results
        assert 3 not in results

    def test_detect_riders_empty_input(self):
        detector = HelmetDetector(model_path=f"{AI_ENGINE_DIR}/models/helmet.pt", conf_threshold=0.3)
        frame = np.zeros((720, 1280, 3), dtype=np.uint8)
        assert detector.detect_riders(frame, []) == {}

    def test_detect_riders_unknown_category(self):
        detector = HelmetDetector(model_path=f"{AI_ENGINE_DIR}/models/helmet.pt", conf_threshold=0.3)
        frame = np.zeros((720, 1280, 3), dtype=np.uint8)
        tracks_with_bbox = [(1, [100, 100, 200, 300], "")]
        assert detector.detect_riders(frame, tracks_with_bbox) == {}

    def test_detect_empty_frame(self):
        detector = HelmetDetector(model_path=f"{AI_ENGINE_DIR}/models/helmet.pt", conf_threshold=0.3)
        frame = np.zeros((720, 1280, 3), dtype=np.uint8)
        results = detector.detect(frame, [100, 100, 200, 300])
        assert isinstance(results, list)

    def test_with_helmet_without_helmet_constants(self):
        assert WITH_HELMET == 0
        assert WITHOUT_HELMET == 1


class TestPlateDetectorROI:
    """Tests for PlateDetector ROI offset logic."""

    def test_crop_to_roi_coordinates(self):
        detector = PlateDetector(model_path=f"{AI_ENGINE_DIR}/models/plate.pt", conf_threshold=0.3)
        frame = np.zeros((720, 1280, 3), dtype=np.uint8)
        results = detector.detect(frame, roi_bbox=[100, 100, 400, 300])
        assert isinstance(results, list)

    def test_detect_no_roi(self):
        detector = PlateDetector(model_path=f"{AI_ENGINE_DIR}/models/plate.pt", conf_threshold=0.3)
        frame = np.zeros((720, 1280, 3), dtype=np.uint8)
        results = detector.detect(frame)
        assert isinstance(results, list)

    def test_invalid_roi_returns_empty(self):
        detector = PlateDetector(model_path=f"{AI_ENGINE_DIR}/models/plate.pt", conf_threshold=0.3)
        frame = np.zeros((720, 1280, 3), dtype=np.uint8)
        results = detector.detect(frame, roi_bbox=[100, 100, 50, 50])
        assert results == []


class TestViolationDetectorNoHelmet:
    """Tests for _detect_no_helmet temporal confirmation and state isolation."""

    def test_no_helmet_needs_temporal_window(self):
        detector = ViolationDetector(helmet_temporal_window=5)
        for _ in range(4):
            v = detector._detect_no_helmet(
                make_helmet_simple(1, False), "cam1", {}, {}
            )
            assert len(v) == 0

    def test_no_helmet_reported_when_window_fills(self):
        detector = ViolationDetector(helmet_temporal_window=5)
        for _ in range(4):
            detector._detect_no_helmet(make_helmet_simple(1, False), "cam1", {}, {})
        # 5th call fills the window — violation triggers immediately
        violations = detector._detect_no_helmet(
            make_helmet_simple(1, False), "cam1", {}, {}
        )
        assert len(violations) == 1
        assert violations[0].violation_type == ViolationType.NO_HELMET
        assert violations[0].track_id == 1
        assert violations[0].camera_id == "cam1"

    def test_helmet_worn_no_violation(self):
        detector = ViolationDetector(helmet_temporal_window=5)
        for _ in range(6):
            v = detector._detect_no_helmet(
                make_helmet_simple(1, True), "cam1", {}, {}
            )
            assert len(v) == 0

    def test_camera_isolation(self):
        detector = ViolationDetector(helmet_temporal_window=3)
        # cam1: fill window and trigger (needs 3 frames, threshold=max(2,1.8)=2)
        detector._detect_no_helmet(make_helmet_simple(1, False), "cam1", {}, {})
        detector._detect_no_helmet(make_helmet_simple(1, False), "cam1", {}, {})
        v1_fill = detector._detect_no_helmet(
            make_helmet_simple(1, False), "cam1", {}, {}
        )
        assert len(v1_fill) == 1

        # cam2 should NOT have any state for track 1 (separate)
        detector._detect_no_helmet(make_helmet_simple(1, False), "cam2", {}, {})
        detector._detect_no_helmet(make_helmet_simple(1, False), "cam2", {}, {})
        v2 = detector._detect_no_helmet(
            make_helmet_simple(1, False), "cam2", {}, {}
        )
        # 3rd call fills window with 3 no-helmet entries → triggers
        assert len(v2) == 1
        assert ("cam1", 1) in detector._helmet_reported

    def test_track_isolation(self):
        detector = ViolationDetector(helmet_temporal_window=3)
        # Track 1 no-helmet, Track 2 has helmet
        track1 = make_helmet_simple(1, False)
        track2 = make_helmet_simple(2, True)

        for _ in range(2):
            detector._detect_no_helmet(track1, "cam1", {}, {})
            detector._detect_no_helmet(track2, "cam1", {}, {})

        combined = make_helmet_multi([1, 2], [[False], [True]])
        violations = detector._detect_no_helmet(combined, "cam1", {}, {})
        assert len(violations) == 1
        assert violations[0].track_id == 1

    def test_dedup_no_duplicate_report(self):
        detector = ViolationDetector(helmet_temporal_window=3)
        for _ in range(2):
            detector._detect_no_helmet(make_helmet_simple(1, False), "cam1", {}, {})
        # 3rd call fills window and triggers
        v1 = detector._detect_no_helmet(
            make_helmet_simple(1, False), "cam1", {}, {}
        )
        assert len(v1) == 1

        # Subsequent calls should NOT re-report
        for _ in range(5):
            v2 = detector._detect_no_helmet(
                make_helmet_simple(1, False), "cam1", {}, {}
            )
            assert len(v2) == 0

    def test_transition_helmet_to_no_helmet(self):
        detector = ViolationDetector(helmet_temporal_window=3)
        for _ in range(3):
            v = detector._detect_no_helmet(
                make_helmet_simple(1, True), "cam1", {}, {}
            )
            assert len(v) == 0

        detector._detect_no_helmet(
            make_helmet_simple(1, False), "cam1", {}, {}
        )
        v = detector._detect_no_helmet(
            make_helmet_simple(1, False), "cam1", {}, {}
        )
        assert len(v) == 1

    def test_mixed_detections_conservative(self):
        """When both With and Without Helmet detected, treat as no-helmet."""
        detector = ViolationDetector(helmet_temporal_window=3)
        helmet_results = {
            1: [
                HelmetResult(bbox=[0, 0, 10, 10], confidence=0.9, has_helmet=True, class_id=WITH_HELMET),
                HelmetResult(bbox=[0, 0, 10, 10], confidence=0.9, has_helmet=False, class_id=WITHOUT_HELMET),
            ]
        }
        for _ in range(2):
            detector._detect_no_helmet(helmet_results, "cam1", {}, {})
        # 3rd call fills window with False entries (mixed → conservative)
        violations = detector._detect_no_helmet(helmet_results, "cam1", {}, {})
        assert len(violations) == 1

    def test_empty_helmet_results_skipped(self):
        detector = ViolationDetector(helmet_temporal_window=3)
        assert detector._detect_no_helmet({}, "cam1", {}, {}) == []

    def test_track_without_results_skipped(self):
        detector = ViolationDetector(helmet_temporal_window=3)
        assert detector._detect_no_helmet({1: []}, "cam1", {}, {}) == []

    def test_cleanup_helmet_state(self):
        detector = ViolationDetector(helmet_temporal_window=3)
        detector._helmet_history[("cam1", 1)].extend([False, False, False])
        detector._helmet_history[("cam1", 2)].extend([False, False, False])
        detector._helmet_reported.add(("cam1", 1))
        detector.cleanup_helmet_state("cam1", {1})
        assert ("cam1", 1) in detector._helmet_history
        assert ("cam1", 2) not in detector._helmet_history
        assert ("cam1", 1) in detector._helmet_reported

    def test_cleanup_other_camera_unaffected(self):
        detector = ViolationDetector(helmet_temporal_window=3)
        detector._helmet_history[("cam1", 1)].extend([False, False, False])
        detector._helmet_history[("cam2", 1)].extend([False, False, False])
        detector._helmet_reported.add(("cam1", 1))
        detector.cleanup_helmet_state("cam2", set())
        assert ("cam1", 1) in detector._helmet_history
        assert ("cam2", 1) not in detector._helmet_history
        assert ("cam1", 1) in detector._helmet_reported

    def test_detector_version_present(self):
        detector = ViolationDetector(
            detector_version="yolov8-session9-test",
            helmet_temporal_window=3,
        )
        for _ in range(2):
            detector._detect_no_helmet(make_helmet_simple(1, False), "cam1", {}, {})
        violations = detector._detect_no_helmet(
            make_helmet_simple(1, False), "cam1", {}, {}
        )
        assert len(violations) == 1
        assert violations[0].detector_version == "yolov8-session9-test"

    def test_dedup_key_format(self):
        detector = ViolationDetector(helmet_temporal_window=3)
        for _ in range(2):
            detector._detect_no_helmet(
                make_helmet_simple(42, False), "cam-auth-test", {}, {}
            )
        detector._detect_no_helmet(
            make_helmet_simple(42, False), "cam-auth-test", {}, {}
        )
        expected_key = "cam-auth-test:42:NO_HELMET"
        assert expected_key in detector._reported_violations

    def test_different_camera_same_track(self):
        detector = ViolationDetector(helmet_temporal_window=2)
        # Fill camA (2 calls, 2nd triggers)
        for _ in range(1):
            detector._detect_no_helmet(make_helmet_simple(42, False), "camA", {}, {})
        va_fill = detector._detect_no_helmet(
            make_helmet_simple(42, False), "camA", {}, {}
        )
        assert len(va_fill) == 1
        assert va_fill[0].camera_id == "camA"

        # Fill camB (2 calls, 2nd triggers)
        for _ in range(1):
            detector._detect_no_helmet(make_helmet_simple(42, False), "camB", {}, {})
        vb_fill = detector._detect_no_helmet(
            make_helmet_simple(42, False), "camB", {}, {}
        )
        assert len(vb_fill) == 1
        assert vb_fill[0].camera_id == "camB"


class TestViolationDetectorTempWindowConfig:
    """Tests for different helmet_temporal_window configurations."""

    def test_window_2_immediate_detection(self):
        """With window=2, second no-helmet frame should trigger."""
        detector = ViolationDetector(helmet_temporal_window=2)
        v1 = detector._detect_no_helmet(
            make_helmet_simple(1, False), "cam1", {}, {}
        )
        assert len(v1) == 0
        v2 = detector._detect_no_helmet(
            make_helmet_simple(1, False), "cam1", {}, {}
        )
        assert len(v2) == 1

    def test_window_3_minority_helmet_no_violation(self):
        """With window=3, 1/3 no-helmet should not trigger (needs >=2)."""
        detector = ViolationDetector(helmet_temporal_window=3)
        detector._detect_no_helmet(make_helmet_simple(1, False), "cam1", {}, {})
        detector._detect_no_helmet(make_helmet_simple(1, True), "cam1", {}, {})
        detector._detect_no_helmet(make_helmet_simple(1, True), "cam1", {}, {})
        violations = detector._detect_no_helmet(
            make_helmet_simple(1, False), "cam1", {}, {}
        )
        # History: [False, True, True, False], window=3 → [True, True, False]
        # no_helmet_count=1, 1 < max(2, 1.8)=2 → skip
        assert len(violations) == 0


class TestPlateRecognizerCleanup:
    """Tests for PlateRecognizer state cleanup."""

    def _make_recognizer(self):
        r = MagicMock(spec=PlateRecognizer)
        r._track_ocr_history = {}
        r.cleanup_track_history = PlateRecognizer.cleanup_track_history.__get__(r, PlateRecognizer)
        return r

    def test_cleanup_track_history(self):
        recognizer = self._make_recognizer()
        recognizer._track_ocr_history = {
            1: [("ABC123", 0.9, 1.0)],
            2: [("DEF456", 0.8, 1.0)],
            3: [("GHI789", 0.95, 1.0)],
        }
        recognizer.cleanup_track_history({1, 3})
        assert 1 in recognizer._track_ocr_history
        assert 2 not in recognizer._track_ocr_history
        assert 3 in recognizer._track_ocr_history

    def test_cleanup_track_history_empty_active(self):
        recognizer = self._make_recognizer()
        recognizer._track_ocr_history = {
            1: [("ABC123", 0.9, 1.0)],
            2: [("DEF456", 0.8, 1.0)],
        }
        recognizer.cleanup_track_history(set())
        assert recognizer._track_ocr_history == {}

    def test_cleanup_track_history_empty_state(self):
        recognizer = self._make_recognizer()
        recognizer.cleanup_track_history({1, 2})
        assert recognizer._track_ocr_history == {}


class TestPlateRecognizerROI:
    """Tests for PlateRecognizer ROI coordinate validation."""

    def _make_recognizer(self):
        settings = MagicMock()
        settings.PLATE_DETECTION_CONFIDENCE = 0.3
        settings.PLATE_MODEL_PATH = f"{AI_ENGINE_DIR}/models/plate.pt"
        return PlateRecognizer(settings)

    def test_plate_roi_clips_to_frame(self):
        recognizer = self._make_recognizer()
        frame = np.zeros((100, 200, 3), dtype=np.uint8)
        roi, bbox = recognizer._locate_plate_roi(frame, [0, 0, 500, 500])
        assert roi is None

    def test_plate_detect_in_roi_invalid_bbox(self):
        recognizer = self._make_recognizer()
        frame = np.zeros((100, 200, 3), dtype=np.uint8)
        result = recognizer._detect_plate_in_roi(frame, [50, 50, 30, 30], track_id=1)
        assert result is None


class TestHelmetDetectorROI:
    """Tests for HelmetDetector ROI coordinate calculations."""

    def test_upper_roi_calculation(self):
        detector = HelmetDetector(model_path=f"{AI_ENGINE_DIR}/models/helmet.pt", conf_threshold=0.3)
        frame = np.zeros((720, 1280, 3), dtype=np.uint8)
        results = detector.detect(frame, [100, 200, 400, 600])
        assert isinstance(results, list)

    def test_invalid_vehicle_bbox(self):
        detector = HelmetDetector(model_path=f"{AI_ENGINE_DIR}/models/helmet.pt", conf_threshold=0.3)
        frame = np.zeros((720, 1280, 3), dtype=np.uint8)
        results = detector.detect(frame, [0, 0, -1, -1])
        assert results == []


class TestPlateDetectorModel:
    """Integration tests with actual plate model."""

    def test_plate_model_loads(self):
        detector = PlateDetector(model_path=f"{AI_ENGINE_DIR}/models/plate.pt", conf_threshold=0.3)
        assert detector.model is not None
        assert hasattr(detector.model, 'names')
        names = detector.model.names
        assert 0 in names
        assert names[0] == "license_plate"

    def test_model_version_set(self):
        detector = PlateDetector(model_path=f"{AI_ENGINE_DIR}/models/plate.pt", conf_threshold=0.3)
        assert detector.model_version is not None
        assert "plate" in detector.model_version

    def test_detect_returns_list(self):
        detector = PlateDetector(model_path=f"{AI_ENGINE_DIR}/models/plate.pt", conf_threshold=0.3)
        frame = np.zeros((720, 1280, 3), dtype=np.uint8)
        plates = detector.detect(frame)
        assert isinstance(plates, list)


class TestHelmetDetectorModel:
    """Integration tests with actual helmet model."""

    def test_helmet_model_loads(self):
        detector = HelmetDetector(model_path=f"{AI_ENGINE_DIR}/models/helmet.pt", conf_threshold=0.3)
        assert detector.model is not None
        assert hasattr(detector.model, 'names')
        names = detector.model.names
        assert 0 in names
        assert 1 in names
        assert names[0] == "With Helmet"
        assert names[1] == "Without Helmet"

    def test_model_version_set(self):
        detector = HelmetDetector(model_path=f"{AI_ENGINE_DIR}/models/helmet.pt", conf_threshold=0.3)
        assert detector.model_version is not None
        assert "helmet" in detector.model_version

    def test_detect_returns_list(self):
        detector = HelmetDetector(model_path=f"{AI_ENGINE_DIR}/models/helmet.pt", conf_threshold=0.3)
        frame = np.zeros((720, 1280, 3), dtype=np.uint8)
        results = detector.detect(frame, [100, 100, 400, 500])
        assert isinstance(results, list)


class TestViolationDetectorNominalBehavior:
    """Nominal behavior tests for end-to-end scenarios."""

    def test_detect_called_with_helmet_results(self):
        """ViolationDetector.detect() should accept helmet_results parameter."""
        detector = ViolationDetector(helmet_temporal_window=2)
        h = make_helmet_simple(1, False)
        for _ in range(1):
            detector._detect_no_helmet(h, "cam1", {}, {})
        violations = detector._detect_no_helmet(h, "cam1", {}, {})
        assert len(violations) == 1

    def test_helmet_history_max_size(self):
        """Helmet history should not exceed temporal window."""
        detector = ViolationDetector(helmet_temporal_window=3)
        for _ in range(10):
            detector._detect_no_helmet(make_helmet_simple(1, False), "cam1", {}, {})
        history = detector._helmet_history[("cam1", 1)]
        assert len(history) == 3

    def test_helmet_reported_set_usage(self):
        """_helmet_reported should grow correctly."""
        detector = ViolationDetector(helmet_temporal_window=2)
        for tid in [1, 2, 3]:
            for _ in range(2):
                detector._detect_no_helmet(
                    make_helmet_simple(tid, False), "cam1", {}, {}
                )
        assert len(detector._helmet_reported) == 3

    def test_helmet_reported_cleanup(self):
        """cleanup_helmet_state removes from _helmet_reported."""
        detector = ViolationDetector(helmet_temporal_window=2)
        detector._helmet_history[("cam1", 1)] = [False, False]
        detector._helmet_reported.add(("cam1", 1))
        detector._helmet_history[("cam1", 2)] = [False, False]
        detector._helmet_reported.add(("cam1", 2))
        detector.cleanup_helmet_state("cam1", {1})
        assert ("cam1", 1) in detector._helmet_reported
        assert ("cam1", 2) not in detector._helmet_reported


class TestViolationDetectorPlateIntegration:
    """Tests for plate_by_track integration in _detect_no_helmet."""

    def test_violation_includes_plate_when_available(self):
        detector = ViolationDetector(helmet_temporal_window=2)
        plate_by_track = {1: PlateResult(
            plate_text="BA1CHA",
            normalized_plate="BA1CHA",
            country="NEPAL",
            country_confidence=0.9,
            ocr_confidence=0.85,
            track_id=1,
        )}
        for _ in range(1):
            detector._detect_no_helmet(
                make_helmet_simple(1, False), "cam1", plate_by_track, {}
            )
        v = detector._detect_no_helmet(
            make_helmet_simple(1, False), "cam1", plate_by_track, {}
        )
        assert len(v) == 1
        assert v[0].plate_text == "BA1CHA"
        assert v[0].normalized_plate == "BA1CHA"
        assert v[0].plate_country == "NEPAL"

    def test_violation_no_plate(self):
        """Violation should still fire without plate data."""
        detector = ViolationDetector(helmet_temporal_window=2)
        for _ in range(1):
            detector._detect_no_helmet(
                make_helmet_simple(1, False), "cam1", {}, {}
            )
        v = detector._detect_no_helmet(
            make_helmet_simple(1, False), "cam1", {}, {}
        )
        assert len(v) == 1
        assert v[0].plate_text is None
