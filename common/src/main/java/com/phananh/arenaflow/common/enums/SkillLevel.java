package com.phananh.arenaflow.common.enums;

import java.util.List;

public final class SkillLevel {

    private static final List<String> LABELS = List.of(
            "Chill", "Nhiệt tình", "Máu lửa", "Vui chơi", "Rèn luyện",
            "Thi đấu", "Mới bắt đầu", "Đã có kinh nghiệm", "Lão làng");

    private SkillLevel() {
    }

    public static List<String> labels() {
        return LABELS;
    }
}
