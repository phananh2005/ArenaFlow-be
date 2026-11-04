package com.phananh.ArenaFlow.repository;

import com.phananh.ArenaFlow.entity.user.AdministrativeUnit;
import com.phananh.ArenaFlow.repository.projection.WardView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
