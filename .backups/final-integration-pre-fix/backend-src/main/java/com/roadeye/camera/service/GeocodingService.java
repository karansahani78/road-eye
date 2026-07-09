package com.roadeye.camera.service;

import com.roadeye.camera.dto.GeocodingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);

    private final RestTemplate restTemplate;
    private final String nominatimEndpoint;
    private final String userAgent;
    private final long rateLimitMs;
    private final ReentrantLock rateLimitLock = new ReentrantLock();
    private Instant lastRequestTime = Instant.EPOCH;

    public GeocodingService(
            @Value("${geocoding.nominatim.endpoint:https://nominatim.openstreetmap.org}") String nominatimEndpoint,
            @Value("${geocoding.nominatim.user-agent:RoadEye/1.0}") String userAgent,
            @Value("${geocoding.nominatim.rate-limit-ms:1100}") long rateLimitMs,
            RestTemplateBuilder builder) {
        this.nominatimEndpoint = nominatimEndpoint;
        this.userAgent = userAgent;
        this.rateLimitMs = rateLimitMs;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    public List<GeocodingResult> search(String query, int limit) {
        enforceRateLimit();

        String url = UriComponentsBuilder.fromHttpUrl(nominatimEndpoint + "/search")
                .queryParam("q", query)
                .queryParam("format", "jsonv2")
                .queryParam("addressdetails", 1)
                .queryParam("limit", Math.min(limit, 20))
                .queryParam("accept-language", "en")
                .build()
                .toUriString();

        try {
            HttpEntity<String> requestEntity = buildRequestEntity();
            var exchange = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );

            if (exchange.getStatusCode() != HttpStatus.OK || exchange.getBody() == null) {
                log.warn("Nominatim search returned status {} for query: {}", exchange.getStatusCode(), query);
                return List.of();
            }

            return parseResults(exchange.getBody());
        } catch (Exception e) {
            log.warn("Geocoding search failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    public Optional<GeocodingResult> reverse(double lat, double lon) {
        enforceRateLimit();

        String url = UriComponentsBuilder.fromHttpUrl(nominatimEndpoint + "/reverse")
                .queryParam("lat", lat)
                .queryParam("lon", lon)
                .queryParam("format", "jsonv2")
                .queryParam("addressdetails", 1)
                .queryParam("accept-language", "en")
                .build()
                .toUriString();

        try {
            HttpEntity<String> requestEntity = buildRequestEntity();
            var response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                return Optional.empty();
            }

            return parseSingleResult(response.getBody());
        } catch (Exception e) {
            log.warn("Reverse geocoding failed for ({}, {}): {}", lat, lon, e.getMessage());
            return Optional.empty();
        }
    }

    private HttpEntity<String> buildRequestEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, userAgent);
        return new HttpEntity<>(headers);
    }

    private void enforceRateLimit() {
        rateLimitLock.lock();
        try {
            long elapsed = Duration.between(lastRequestTime, Instant.now()).toMillis();
            if (elapsed < rateLimitMs) {
                try {
                    Thread.sleep(rateLimitMs - elapsed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestTime = Instant.now();
        } finally {
            rateLimitLock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private List<GeocodingResult> parseResults(List<Map<String, Object>> raw) {
        List<GeocodingResult> results = new ArrayList<>();
        for (Map<String, Object> item : raw) {
            parseSingleResult(item).ifPresent(results::add);
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private Optional<GeocodingResult> parseSingleResult(Map<String, Object> item) {
        try {
            Object latObj = item.get("lat");
            Object lonObj = item.get("lon");
            if (latObj == null || lonObj == null) return Optional.empty();

            double lat = Double.parseDouble(latObj.toString());
            double lon = Double.parseDouble(lonObj.toString());

            Map<String, Object> address = (Map<String, Object>) item.get("address");

            return Optional.of(GeocodingResult.builder()
                    .displayName((String) item.get("display_name"))
                    .latitude(lat)
                    .longitude(lon)
                    .placeId(item.get("place_id") != null ? item.get("place_id").toString() : null)
                    .category((String) item.get("category"))
                    .type((String) item.get("type"))
                    .country(address != null ? (String) address.get("country") : null)
                    .state(address != null ? (String) address.get("state") : null)
                    .district(address != null ? (String) address.get("district") : null)
                    .municipality(address != null ? (String) address.get("municipality") : null)
                    .road(address != null ? (String) address.get("road") : null)
                    .houseNumber(address != null ? (String) address.get("house_number") : null)
                    .postcode(address != null ? (String) address.get("postcode") : null)
                    .importance(item.get("importance") != null ? ((Number) item.get("importance")).doubleValue() : null)
                    .boundingBox(item.get("boundingbox") != null ? (List<String>) item.get("boundingbox") : null)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to parse geocoding result: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
