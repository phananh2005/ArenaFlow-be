package com.phananh.arenaflow.application.support;

/**
 * Hộp giới hạn toạ độ quanh một điểm để lọc bằng index trước khi tính khoảng cách Haversine (≈ 111,32 km mỗi độ vĩ).
 */
public record GeoBoundingBox(double minLat, double maxLat, double minLng, double maxLng) {

    private static final double KM_PER_DEGREE = 111.32d;

    public static GeoBoundingBox around(double lat, double lng, double radiusKm) {
        double latitudeDelta = radiusKm / KM_PER_DEGREE;
        double longitudeDelta = radiusKm / (KM_PER_DEGREE * Math.cos(Math.toRadians(lat)));
        return new GeoBoundingBox(lat - latitudeDelta, lat + latitudeDelta, lng - longitudeDelta, lng + longitudeDelta);
    }
}
