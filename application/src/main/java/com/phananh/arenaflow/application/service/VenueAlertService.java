package com.phananh.arenaflow.application.service;

public interface VenueAlertService {

    /** Cảnh báo Admin một lần cho mỗi địa điểm ACTIVE lâu không được chọn; trả về số địa điểm đã cảnh báo. */
    int alertStaleVenues();
}
