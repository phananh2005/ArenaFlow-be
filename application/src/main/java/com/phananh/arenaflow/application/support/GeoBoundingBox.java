package com.phananh.arenaflow.application.support;

/**
 * Hộp giới hạn toạ độ quanh một điểm cho bán kính (km) — lọc thô bằng index trước khi tính Haversine. Xấp xỉ 111,32 km
 * mỗi độ vĩ; độ kinh co theo cos(vĩ độ).
 */
public record GeoBoundingBox(double minLat, double maxLat, double minLng, double maxLng) {

    private static final double KM_PER_DEGREE = 111.32d;

    public static GeoBoundingBox of(double lat, double lng, double radiusKm) {
        double latitudeDelta = radiusKm / KM_PER_DEGREE;
        double longitudeDelta = radiusKm / (KM_PER_DEGREE * Math.cos(Math.toRadians(lat)));
        return new GeoBoundingBox(lat - latitudeDelta, lat + latitudeDelta, lng - longitudeDelta, lng + longitudeDelta);
    }
}
