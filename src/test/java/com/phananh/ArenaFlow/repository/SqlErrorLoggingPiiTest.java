package com.phananh.ArenaFlow.repository;

import com.phananh.ArenaFlow.entity.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Đụng unique trên DB thật: giá trị dòng (số điện thoại = PII) không được xuất hiện trong log ở bất kỳ tầng nào. */
@ExtendWith(OutputCaptureExtension.class)
class SqlErrorLoggingPiiTest extends RepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void duplicatePhoneDoesNotLogPhoneNumber(CapturedOutput output) {
        // Tiền tố "00": số thật lưu dạng 09…/+84… nên không trùng dữ liệu DB dev; nanoTime → khác nhau mỗi lần chạy.
        String phone = "00" + String.format("%013d", Math.floorMod(System.nanoTime(), 10_000_000_000_000L));
        userRepository.saveAndFlush(userWithPhone(phone));

        // Hợp đồng GlobalExceptionHandler dựa vào: message của cause gốc giữ tên constraint, không còn Detail.
        assertThatThrownBy(() -> userRepository.saveAndFlush(userWithPhone(phone)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(((DataIntegrityViolationException) e).getMostSpecificCause().getMessage())
                        .contains("idx_user_phone_unique").doesNotContain("Detail"));

        // Log lỗi SQL vẫn có (tên constraint) nhưng không có giá trị dòng.
        assertThat(output.getAll()).contains("idx_user_phone_unique").doesNotContain(phone);
    }

    private static User userWithPhone(String phone) {
        User user = new User();
        user.setDisplayName("Pii Probe");
        user.setPhoneNumber(phone);
        return user;
    }
}
