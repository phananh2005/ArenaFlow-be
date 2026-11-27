package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.entity.user.User;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.Repository;

import java.util.UUID;

/**
 * Chỉ dùng cho test: câu chậm 3 giây với cùng query hint timeout 2 giây như câu tìm người theo tên, để kiểm lệnh huỷ
 * câu của driver đi qua được kết nối DB thật (kể cả qua pooler).
 */
public interface QueryTimeoutProbeRepository extends Repository<User, UUID> {

    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "2000"))
    @Query(value = "SELECT CAST(pg_sleep(3) AS text)", nativeQuery = true)
    String sleepThreeSeconds();
}
