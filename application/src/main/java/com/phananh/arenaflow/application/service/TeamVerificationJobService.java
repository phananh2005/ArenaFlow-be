package com.phananh.arenaflow.application.service;

public interface TeamVerificationJobService {

    /**
     * Đối soát huy hiệu xác minh mọi đội theo lô id tăng dần, mỗi lô một transaction (2 truy vấn); trả số đội đổi
     * trạng thái huy hiệu. Bắt các thay đổi không refresh ngay (huỷ kèo, đổi đội trưởng, số điện thoại…).
     */
    int reconcileAll();
}
