package com.phananh.arenaflow.application.service;

public interface VenueImageCleanupService {

    /** Xoá ảnh địa điểm đã lưu mà không còn dòng nào tham chiếu; trả về số ảnh đã xoá trong lượt chạy. */
    int cleanupOrphanAssets();
}
