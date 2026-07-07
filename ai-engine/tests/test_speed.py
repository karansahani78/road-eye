import pytest
from src.speed.speed_estimator import SpeedEstimator
from src.common.models import Track, Detection


class TestSpeedEstimator:
    def setup_method(self):
        self.estimator = SpeedEstimator()

    def test_no_tracks(self):
        results = self.estimator.estimate([], 0.033)
        assert len(results) == 0
