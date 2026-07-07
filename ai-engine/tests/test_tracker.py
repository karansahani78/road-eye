import pytest
import numpy as np
from src.tracking.tracker import VehicleTracker
from src.common.models import Detection


class TestVehicleTracker:
    def setup_method(self):
        self.tracker = VehicleTracker(max_lost=10, iou_threshold=0.3)

    def test_empty_detections(self):
        tracks = self.tracker.update([], np.zeros((100, 100, 3), dtype=np.uint8))
        assert len(tracks) == 0
