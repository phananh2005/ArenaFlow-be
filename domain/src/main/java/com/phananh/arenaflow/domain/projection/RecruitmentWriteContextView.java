package com.phananh.arenaflow.domain.projection;

/**
 * Context ghi trên một tin (sửa số lượng, đóng tin, xem ứng viên): cùng cột {@link RecruitmentView} — response dựng từ
 * dòng này mà không đọc lại; {@code viewerIsCaptain} là cờ quyền, {@code teamStatus} cho guard đội.
 */
public interface RecruitmentWriteContextView extends RecruitmentView {
}
