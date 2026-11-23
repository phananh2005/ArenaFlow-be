package com.phananh.arenaflow.application.validation;

import com.phananh.arenaflow.common.time.AppTime;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

/** Kiểm tra {@link MinAge}: ngày sinh đủ số tuổi tối thiểu tính tới hôm nay. */
public class MinAgeValidator implements ConstraintValidator<MinAge, LocalDate> {

    private int minAge;

    /** Đọc số tuổi tối thiểu từ annotation. */
    @Override
    public void initialize(MinAge annotation) {
        minAge = annotation.value();
    }

    /**
     * Hợp lệ khi {@code dateOfBirth} + {@code minAge} năm không sau {@link AppTime#today()} (đúng sinh nhật là đủ
     * tuổi); null coi là hợp lệ để {@code @NotNull} quyết định bắt buộc.
     */
    @Override
    public boolean isValid(LocalDate dateOfBirth, ConstraintValidatorContext context) {
        return dateOfBirth == null || !dateOfBirth.plusYears(minAge).isAfter(AppTime.today());
    }
}
