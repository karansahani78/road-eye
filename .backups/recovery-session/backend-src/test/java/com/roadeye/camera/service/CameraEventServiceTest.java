package com.roadeye.camera.service;

import com.roadeye.camera.entity.Camera;
import com.roadeye.camera.entity.CameraCalibration;
import com.roadeye.camera.repository.CameraCalibrationRepository;
import com.roadeye.camera.repository.CameraHealthEventRepository;
import com.roadeye.camera.repository.CameraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CameraEventServiceTest {

    @Mock
    private CameraRepository cameraRepository;
    @Mock
    private CameraCalibrationRepository cameraCalibrationRepository;
    @Mock
    private CameraHealthEventRepository healthEventRepository;
    @Mock
    private RestTemplate restTemplate;

    private CameraEventService cameraEventService;

    @BeforeEach
    void setUp() {
        cameraEventService = new CameraEventService(
                cameraRepository, cameraCalibrationRepository,
                healthEventRepository, restTemplate);
    }

    @Test
    void computeReferencePixels_bothPointsValid_returnsCorrectDistance() throws Exception {
        java.lang.reflect.Method method = CameraEventService.class.getDeclaredMethod(
                "computeReferencePixels", String.class, String.class);
        method.setAccessible(true);

        Object result = method.invoke(cameraEventService, "100,200", "300,500");

        assertNotNull(result);
        double distance = (Double) result;
        assertEquals(360.555, distance, 0.01);
    }

    @Test
    void computeReferencePixels_nullPoints_returnsNull() throws Exception {
        java.lang.reflect.Method method = CameraEventService.class.getDeclaredMethod(
                "computeReferencePixels", String.class, String.class);
        method.setAccessible(true);

        assertNull(method.invoke(cameraEventService, null, "100,200"));
        assertNull(method.invoke(cameraEventService, "100,200", null));
        assertNull(method.invoke(cameraEventService, null, null));
    }

    @Test
    void computeReferencePixels_invalidFormat_returnsNull() throws Exception {
        java.lang.reflect.Method method = CameraEventService.class.getDeclaredMethod(
                "computeReferencePixels", String.class, String.class);
        method.setAccessible(true);

        assertNull(method.invoke(cameraEventService, "abc", "100,200"));
        assertNull(method.invoke(cameraEventService, "100,200", "xyz"));
    }

    @Test
    void computeReferencePixels_samePoint_returnsZero() throws Exception {
        java.lang.reflect.Method method = CameraEventService.class.getDeclaredMethod(
                "computeReferencePixels", String.class, String.class);
        method.setAccessible(true);

        Object result = method.invoke(cameraEventService, "100,200", "100,200");

        assertNotNull(result);
        assertEquals(0.0, (Double) result, 0.001);
    }

    @Test
    void computeReferencePixels_negativeCoordinates_worksCorrectly() throws Exception {
        java.lang.reflect.Method method = CameraEventService.class.getDeclaredMethod(
                "computeReferencePixels", String.class, String.class);
        method.setAccessible(true);

        Object result = method.invoke(cameraEventService, "-50,-100", "50,100");

        assertNotNull(result);
        assertEquals(223.607, (Double) result, 0.01);
    }

    @Test
    void computeReferencePixels_trailingWhitespace_handlesCorrectly() throws Exception {
        java.lang.reflect.Method method = CameraEventService.class.getDeclaredMethod(
                "computeReferencePixels", String.class, String.class);
        method.setAccessible(true);

        Object result = method.invoke(cameraEventService, "  100  ,  200  ", "300,500");

        assertNotNull(result);
        double distance = (Double) result;
        assertEquals(360.555, distance, 0.01);
    }
}
