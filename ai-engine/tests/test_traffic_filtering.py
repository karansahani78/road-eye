import pytest
import numpy as np
from src.detection.vehicle_detector import (
    VehicleDetector, ACCEPTED_COCO_CLASSES, COCO_TO_ROADEYE
)
from src.tracking.tracker import VehicleTracker, TRAFFIC_CATEGORIES
from src.common.models import Detection, VehicleCategory
from src.config import Settings


class TestTrafficFiltering:
    """Phase 1: Only traffic classes enter the pipeline. People/household objects rejected."""

    def test_only_traffic_classes_accepted(self):
        """Only COCO classes 1-5 (bicycle/motorcycle/car/bus/truck) should be accepted."""
        assert 1 in ACCEPTED_COCO_CLASSES  # bicycle
        assert 2 in ACCEPTED_COCO_CLASSES  # motorcycle
        assert 3 in ACCEPTED_COCO_CLASSES  # car
        assert 4 in ACCEPTED_COCO_CLASSES  # bus
        assert 5 in ACCEPTED_COCO_CLASSES  # truck
        assert 0 not in ACCEPTED_COCO_CLASSES  # person - MUST be rejected
        assert 6 not in ACCEPTED_COCO_CLASSES  # boat - not a traffic vehicle
        assert 7 not in ACCEPTED_COCO_CLASSES  # train - not a traffic vehicle
        assert 56 not in ACCEPTED_COCO_CLASSES  # chair - not a traffic vehicle

    def test_no_other_fallback(self):
        """Detection must NEVER map to VehicleCategory.OTHER via fallback."""
        assert VehicleCategory.OTHER not in COCO_TO_ROADEYE.values()
        for class_id in ACCEPTED_COCO_CLASSES:
            category = COCO_TO_ROADEYE[class_id]
            assert category in TRAFFIC_CATEGORIES

    def test_accepted_classes_all_traffic(self):
        """All accepted classes must be real traffic categories."""
        for cat in COCO_TO_ROADEYE.values():
            assert cat in TRAFFIC_CATEGORIES

    def test_tracker_rejects_non_traffic(self):
        """Tracker must reject detections with non-traffic categories."""
        tracker = VehicleTracker()
        non_traffic_det = Detection(
            bbox=[0, 0, 50, 100],
            confidence=0.9,
            class_id=0,
            class_name="person",
            mapped_category=VehicleCategory.OTHER,
        )
        assert not tracker._is_traffic_detection(non_traffic_det)

    def test_tracker_accepts_traffic(self):
        """Tracker must accept detections with traffic categories."""
        tracker = VehicleTracker()
        traffic_det = Detection(
            bbox=[0, 0, 100, 80],
            confidence=0.9,
            class_id=3,
            class_name="car",
            mapped_category=VehicleCategory.CAR,
        )
        assert tracker._is_traffic_detection(traffic_det)


class TestTrackLifecycle:
    """Phase 3-4: Track lifecycle: TENTATIVE -> CONFIRMED -> LOST -> REMOVED."""

    def make_detection(self, bbox, cat=VehicleCategory.CAR, conf=0.9):
        return Detection(
            bbox=bbox,
            confidence=conf,
            class_id=3,
            class_name="car",
            mapped_category=cat,
        )

    def test_new_track_starts_tentative(self):
        tracker = VehicleTracker(min_hits=3)
        tracker.update([self.make_detection([0, 0, 100, 80])])
        assert len(tracker.get_confirmed_tracks()) == 0

    def test_track_confirmed_after_hits(self):
        tracker = VehicleTracker(min_hits=3)
        det = self.make_detection([0, 0, 100, 80])
        for _ in range(3):
            tracker.update([det])
        assert len(tracker.get_confirmed_tracks()) >= 1

    def test_lost_track_removed_after_timeout(self):
        import time
        tracker = VehicleTracker(max_lost=0.1, min_hits=1)
        det = self.make_detection([0, 0, 100, 80])
        tracker.update([det])
        assert len(tracker.get_confirmed_tracks()) == 1
        time.sleep(0.15)
        tracker.update([])
        assert len(tracker.get_confirmed_tracks()) == 0

    def test_no_duplicate_tracks_same_detection(self):
        tracker = VehicleTracker(min_hits=1)
        det = self.make_detection([50, 50, 200, 180])
        tracker.update([det])
        tracker.update([det])
        assert len(tracker._states) == 1

    def test_duplicate_tracks_for_close_boxes(self):
        """Two overlapping detections in same frame should NOT create two tracks."""
        tracker = VehicleTracker(iou_threshold=0.3, min_hits=1)
        det1 = self.make_detection([50, 50, 200, 180])
        det2 = self.make_detection([60, 55, 210, 185])
        tracker.update([det1, det2])
        assert len(tracker._states) == 1

    def test_track_has_correct_category(self):
        tracker = VehicleTracker(min_hits=1)
        det = self.make_detection([0, 0, 100, 80], cat=VehicleCategory.CAR)
        tracker.update([det])
        tracks = tracker.get_confirmed_tracks()
        assert len(tracks) == 1
        assert tracks[0].category == VehicleCategory.CAR


class TestVehicleCounting:
    """Phase 5: Vehicle counting uses only confirmed tracks."""

    def test_empty_frame_zero_count(self):
        tracker = VehicleTracker()
        tracker.update([])
        assert len(tracker.get_confirmed_tracks()) == 0

    def test_unconfirmed_not_counted(self):
        tracker = VehicleTracker(min_hits=5)
        det = Detection(
            bbox=[0, 0, 100, 80],
            confidence=0.9,
            class_id=3,
            class_name="car",
            mapped_category=VehicleCategory.CAR,
        )
        tracker.update([det])
        assert len(tracker.get_confirmed_tracks()) == 0

    def test_multiple_tracks_counted(self):
        tracker = VehicleTracker(min_hits=1)
        det1 = Detection(bbox=[0, 0, 100, 80], confidence=0.9,
                         class_id=3, class_name="car", mapped_category=VehicleCategory.CAR)
        det2 = Detection(bbox=[300, 0, 400, 80], confidence=0.9,
                         class_id=5, class_name="truck", mapped_category=VehicleCategory.TRUCK)
        tracker.update([det1, det2])
        assert len(tracker.get_confirmed_tracks()) == 2

    def test_traffic_only_no_people_counted(self):
        tracker = VehicleTracker(min_hits=1)
        person_det = Detection(bbox=[0, 0, 50, 100], confidence=0.9,
                               class_id=0, class_name="person", mapped_category=VehicleCategory.OTHER)
        car_det = Detection(bbox=[100, 0, 200, 80], confidence=0.9,
                            class_id=3, class_name="car", mapped_category=VehicleCategory.CAR)
        tracker.update([person_det, car_det])
        assert len(tracker.get_confirmed_tracks()) == 1


class TestSpeedUncalibrated:
    """Phase 14: Speed must NOT produce results without calibration."""

    def test_no_calibration_returns_empty(self):
        from src.speed.speed_estimator import SpeedEstimator
        estimator = SpeedEstimator()
        assert not estimator.has_calibration()
        results = estimator.estimate([], 0.033)
        assert len(results) == 0

    def test_after_calibration_works(self):
        from src.speed.speed_estimator import SpeedEstimator
        from src.common.models import Track, Detection
        estimator = SpeedEstimator()
        estimator.calibrate(10.0, 500.0)
        assert estimator.has_calibration()
