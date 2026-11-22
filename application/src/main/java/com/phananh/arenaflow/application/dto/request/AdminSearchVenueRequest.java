package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.service.VenueStatusFilter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Bộ lọc danh sách địa điểm của Admin: xem mọi trạng thái, không lọc theo vị trí, sắp theo tên.
 *
 * @param q        từ khoá khớp một phần trong tên hoặc địa chỉ, không phân biệt hoa thường; được cắt khoảng trắng
 * @param status   trạng thái cần lọc; vắng → {@code ALL}
 * @param page     trang bắt đầu từ 1; vắng → 1
 * @param pageSize số dòng mỗi trang, tối đa 100; vắng → 20
 */
public record AdminSearchVenueRequest(
        @Size(max = 100) String q,
        VenueStatusFilter status,
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer pageSize) {

    /** Gán giá trị mặc định cho tham số vắng và cắt khoảng trắng của từ khoá. */
    public AdminSearchVenueRequest {
        q = q == null ? null : q.trim();
        status = status == null ? VenueStatusFilter.ALL : status;
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
    }
}
