package com.roadeye.camera.service;

import com.roadeye.camera.dto.CameraRequest;
import com.roadeye.camera.dto.CameraResponse;
import com.roadeye.camera.entity.Camera;
import com.roadeye.camera.entity.Camera.SourceType;
import com.roadeye.camera.repository.CameraCalibrationRepository;
import com.roadeye.camera.repository.CameraHealthEventRepository;
import com.roadeye.camera.repository.CameraRepository;
import com.roadeye.shared.BadRequestException;
import com.roadeye.shared.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CameraServiceTest {

    @Mock
    private CameraRepository cameraRepository;
    @Mock
    private CameraCalibrationRepository cameraCalibrationRepository;
    @Mock
    private CameraHealthEventRepository cameraHealthEventRepository;
    @Mock
    private EncryptionService encryptionService;

    private CameraService cameraService;

    private static final String ENC_PREFIX = "ENC:";

    @BeforeEach
    void setUp() {
        lenient().when(encryptionService.encrypt(any())).thenAnswer(inv -> ENC_PREFIX + inv.getArgument(0));
        lenient().when(encryptionService.decrypt(any())).thenAnswer(inv -> {
            String val = inv.getArgument(0);
            return val != null && val.startsWith(ENC_PREFIX) ? val.substring(ENC_PREFIX.length()) : val;
        });
        lenient().when(encryptionService.isEncrypted(any())).thenAnswer(inv -> {
            String val = inv.getArgument(0);
            return val != null && val.startsWith(ENC_PREFIX);
        });
        cameraService = new CameraService(
                cameraRepository, cameraCalibrationRepository, cameraHealthEventRepository, encryptionService);
    }

    @Test
    void createCamera_trimmedCode_storesTrimmed() {
        CameraRequest request = CameraRequest.builder()
                .cameraCode("  CAM-002  ")
                .name("Test")
                .sourceType(SourceType.RTSP)
                .active(true)
                .build();

        when(cameraRepository.findByCameraCode("CAM-002")).thenReturn(Optional.empty());
        when(cameraRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CameraResponse response = cameraService.createCamera(request);

        assertNotNull(response);
        assertEquals("CAM-002", response.getCameraCode());
        verify(cameraRepository).findByCameraCode("CAM-002");
    }

    @Test
    void createCamera_duplicateActiveCode_throwsBadRequest() {
        CameraRequest request = CameraRequest.builder()
                .cameraCode("CAM-001")
                .name("Test")
                .sourceType(SourceType.RTSP)
                .active(true)
                .build();

        Camera existing = new Camera();
        existing.setCameraCode("CAM-001");
        existing.setActive(true);

        when(cameraRepository.findByCameraCode("CAM-001")).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class, () -> cameraService.createCamera(request));
    }

    @Test
    void createCamera_duplicateDeactivatedCode_reactivatesWithSameUUID() {
        UUID originalId = UUID.randomUUID();
        String originalCode = "CAM-001";
        CameraRequest request = CameraRequest.builder()
                .cameraCode(originalCode)
                .name("Reactivated Camera")
                .sourceType(SourceType.RTSP)
                .active(true)
                .build();

        Camera deactivated = new Camera();
        deactivated.setId(originalId);
        deactivated.setCameraCode(originalCode);
        deactivated.setName("Old Name");
        deactivated.setActive(false);
        deactivated.setHealthStatus(Camera.HealthStatus.OFFLINE);

        when(cameraRepository.findByCameraCode(originalCode)).thenReturn(Optional.of(deactivated));
        when(cameraRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CameraResponse response = cameraService.createCamera(request);

        assertNotNull(response);
        assertEquals(originalId, response.getId(), "UUID must be preserved on reactivation");
        assertEquals("Reactivated Camera", response.getName());
        assertTrue(response.isActive());
        assertEquals(Camera.HealthStatus.UNKNOWN, deactivated.getHealthStatus(), "Health must reset to UNKNOWN");
    }

    @Test
    void createCamera_normalCreation_assignsNewUUID() {
        CameraRequest request = CameraRequest.builder()
                .cameraCode("CAM-NEW")
                .name("New Camera")
                .sourceType(SourceType.RTSP)
                .active(true)
                .build();

        when(cameraRepository.findByCameraCode("CAM-NEW")).thenReturn(Optional.empty());
        when(cameraRepository.save(any())).thenAnswer(invocation -> {
            Camera saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        CameraResponse response = cameraService.createCamera(request);

        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals("CAM-NEW", response.getCameraCode());
        assertTrue(response.isActive());
    }

    @Test
    void updateCamera_trimsCode() {
        UUID id = UUID.randomUUID();
        Camera existing = new Camera();
        existing.setId(id);
        existing.setCameraCode("CAM-OLD");
        existing.setActive(true);

        CameraRequest request = CameraRequest.builder()
                .cameraCode("  CAM-NEW  ")
                .name("Updated")
                .sourceType(SourceType.RTSP)
                .active(true)
                .build();

        when(cameraRepository.findById(id)).thenReturn(Optional.of(existing));
        when(cameraRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CameraResponse response = cameraService.updateCamera(id, request);

        assertEquals("CAM-NEW", response.getCameraCode());
    }

    @Test
    void updateCamera_sourceUrlNull_preservesExisting() {
        UUID id = UUID.randomUUID();
        Camera existing = new Camera();
        existing.setId(id);
        existing.setCameraCode("CAM-001");
        existing.setSourceUrlEncrypted("ENC:rtsp://old-url");
        existing.setActive(true);

        CameraRequest request = CameraRequest.builder()
                .cameraCode("CAM-001")
                .name("Updated")
                .sourceType(SourceType.RTSP)
                .active(true)
                .build();

        when(cameraRepository.findById(id)).thenReturn(Optional.of(existing));
        when(cameraRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CameraResponse response = cameraService.updateCamera(id, request);

        assertEquals("ENC:rtsp://old-url", existing.getSourceUrlEncrypted());
    }

    @Test
    void updateCamera_sourceUrlBlank_preservesExisting() {
        UUID id = UUID.randomUUID();
        Camera existing = new Camera();
        existing.setId(id);
        existing.setCameraCode("CAM-001");
        existing.setSourceUrlEncrypted("ENC:rtsp://old-url");
        existing.setActive(true);

        CameraRequest request = CameraRequest.builder()
                .cameraCode("CAM-001")
                .name("Updated")
                .sourceType(SourceType.RTSP)
                .active(true)
                .sourceUrl("")
                .build();

        when(cameraRepository.findById(id)).thenReturn(Optional.of(existing));
        when(cameraRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        cameraService.updateCamera(id, request);

        assertEquals("ENC:rtsp://old-url", existing.getSourceUrlEncrypted());
    }

    @Test
    void updateCamera_sourceUrlProvided_encryptsCredential() {
        UUID id = UUID.randomUUID();
        Camera existing = new Camera();
        existing.setId(id);
        existing.setCameraCode("CAM-001");
        existing.setSourceUrlEncrypted("ENC:rtsp://old-url");
        existing.setActive(true);

        CameraRequest request = CameraRequest.builder()
                .cameraCode("CAM-001")
                .name("Updated")
                .sourceType(SourceType.RTSP)
                .active(true)
                .sourceUrl("rtsp://new-url")
                .build();

        when(cameraRepository.findById(id)).thenReturn(Optional.of(existing));
        when(cameraRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        cameraService.updateCamera(id, request);

        assertEquals("ENC:rtsp://new-url", existing.getSourceUrlEncrypted());
    }

    @Test
    void mapToCameraResponse_sourceUrlConfigured() {
        Camera camera = new Camera();
        camera.setId(UUID.randomUUID());
        camera.setCameraCode("CAM-001");
        camera.setName("Test");
        camera.setSourceType(SourceType.RTSP);
        camera.setActive(true);
        camera.setSourceUrlEncrypted("ENC:rtsp://something");

        try {
            java.lang.reflect.Method method = CameraService.class.getDeclaredMethod(
                    "mapToCameraResponse", Camera.class);
            method.setAccessible(true);
            CameraResponse response = (CameraResponse) method.invoke(cameraService, camera);
            assertTrue(response.isSourceUrlConfigured());

            camera.setSourceUrlEncrypted(null);
            CameraResponse response2 = (CameraResponse) method.invoke(cameraService, camera);
            assertFalse(response2.isSourceUrlConfigured());

            camera.setSourceUrlEncrypted("");
            CameraResponse response3 = (CameraResponse) method.invoke(cameraService, camera);
            assertFalse(response3.isSourceUrlConfigured());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void sanitizeSourceUrl_removesCredentials() throws Exception {
        java.lang.reflect.Method method = CameraService.class.getDeclaredMethod(
                "sanitizeSourceUrl", String.class);
        method.setAccessible(true);

        String result1 = (String) method.invoke(null, "rtsp://admin:secret@192.168.1.4:554/stream");
        assertEquals("rtsp://****:****@192.168.1.4:554/stream", result1);

        String result2 = (String) method.invoke(null, "rtsp://192.168.1.4/stream");
        assertEquals("rtsp://192.168.1.4/stream", result2);

        assertNull(method.invoke(null, (String) null));

        String result4 = (String) method.invoke(null, "invalid-url");
        assertEquals("invalid-url", result4);
    }
}
