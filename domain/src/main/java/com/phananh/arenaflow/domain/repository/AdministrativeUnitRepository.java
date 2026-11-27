package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.user.AdministrativeUnit;
import com.phananh.arenaflow.domain.projection.ProvinceView;
import com.phananh.arenaflow.domain.projection.ProvinceWardView;
import com.phananh.arenaflow.domain.projection.WardView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdministrativeUnitRepository extends JpaRepository<AdministrativeUnit, UUID> {

    /** Chỉ trả khi id là phường/xã (level = 'ward'); id tỉnh/không tồn tại → empty (USER_002). */
    @Query(value = """
            SELECT w.id AS wardId, w.name AS wardName, p.id AS provinceId, p.name AS provinceName
            FROM administrative_unit w
            JOIN administrative_unit p ON p.id = w.parent_id
            WHERE w.id = :wardId AND w.level = 'ward'
            """, nativeQuery = true)
    Optional<WardView> findWardWithProvince(@Param("wardId") UUID wardId);

    /** Mọi tỉnh/thành, sắp theo thứ tự chữ cái tiếng Việt (collation ICU; collation mặc định C xếp sai "Đ"), rồi id. */
    @Query(value = """
            SELECT id AS provinceId, name AS provinceName
            FROM administrative_unit
            WHERE level = 'province'
            ORDER BY name COLLATE "vi-VN-x-icu", id
            """, nativeQuery = true)
    List<ProvinceView> findProvinces();

    /**
     * Phường/xã của một tỉnh/thành trong một câu: 0 dòng = id không phải tỉnh/thành (không tồn tại hoặc là phường);
     * đúng 1 dòng {@code wardId} null = tỉnh/thành chưa có phường/xã. Sắp theo tên (collation ICU), rồi id.
     */
    @Query(value = """
            SELECT p.id AS provinceId, w.id AS wardId, w.name AS wardName
            FROM administrative_unit p
            LEFT JOIN administrative_unit w ON w.parent_id = p.id AND w.level = 'ward'
            WHERE p.id = :provinceId AND p.level = 'province'
            ORDER BY w.name COLLATE "vi-VN-x-icu", w.id
            """, nativeQuery = true)
    List<ProvinceWardView> findWardsOfProvince(@Param("provinceId") UUID provinceId);
}
