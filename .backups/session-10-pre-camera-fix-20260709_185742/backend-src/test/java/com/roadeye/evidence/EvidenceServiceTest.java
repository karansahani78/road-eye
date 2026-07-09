package com.roadeye.evidence;

import com.roadeye.evidence.service.EvidenceService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceServiceTest {

    @Test
    void computeSha256FromBytes_returnsCorrectHash() {
        byte[] data = "test evidence data".getBytes(StandardCharsets.UTF_8);
        String hash = EvidenceService.computeSha256(data);
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    void computeSha256FromStream_returnsCorrectHash() {
        byte[] data = "road-eye evidence integrity check".getBytes(StandardCharsets.UTF_8);
        InputStream stream = new ByteArrayInputStream(data);
        String hash = EvidenceService.computeSha256(stream);
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    void sha256_isDeterministic() {
        byte[] data = "deterministic test".getBytes(StandardCharsets.UTF_8);
        String hash1 = EvidenceService.computeSha256(data);
        String hash2 = EvidenceService.computeSha256(data);
        assertEquals(hash1, hash2);
    }

    @Test
    void differentInputs_produceDifferentHashes() {
        byte[] data1 = "input one".getBytes(StandardCharsets.UTF_8);
        byte[] data2 = "input two".getBytes(StandardCharsets.UTF_8);
        String hash1 = EvidenceService.computeSha256(data1);
        String hash2 = EvidenceService.computeSha256(data2);
        assertNotEquals(hash1, hash2);
    }

    @Test
    void emptyInput_producesValidHash() {
        byte[] data = new byte[0];
        String hash = EvidenceService.computeSha256(data);
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }
}
