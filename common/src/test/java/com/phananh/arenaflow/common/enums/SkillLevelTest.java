package com.phananh.arenaflow.common.enums;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillLevelTest {

    @Test
    void exposesAllSupportedLabels() {
        assertThat(SkillLevel.labels()).containsExactly(
                "Chill", "Nhiệt tình", "Máu lửa", "Vui chơi", "Rèn luyện",
                "Thi đấu", "Mới bắt đầu", "Đã có kinh nghiệm", "Lão làng");
    }
}
