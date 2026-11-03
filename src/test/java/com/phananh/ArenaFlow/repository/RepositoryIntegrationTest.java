package com.phananh.ArenaFlow.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test repository trên DB dev (.env.dev): mỗi test chạy trong transaction và rollback.
 * Đếm số câu SQL thật bằng Hibernate Statistics (NFS §3).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
abstract class RepositoryIntegrationTest {

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    /** Flush + clear để dữ liệu fixture xuống DB và truy vấn sau không đọc từ persistence context. */
    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    protected Statistics resetStatistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }
}
