package com.murphypotato.simmctoolset.internal.simes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FermentationCountdownTest {
    @Test
    void parsesCompoundChineseAndMillisecondDurations() {
        assertEquals(93_784_750L,
                FermentationCountdown.parseMillis("1天 2小时 3分钟 4.5秒 250毫秒"));
        assertEquals(1_250L, FermentationCountdown.parseMillis("1秒 250ms"));
    }

    @Test
    void rejectsUnqualifiedTextAndRecognizesServerCompletion() {
        assertEquals(-1L, FermentationCountdown.parseMillis("服务器暂时没有数据"));
        assertTrue(FermentationCountdown.isServerComplete("腌制已完成。"));
        assertTrue(FermentationCountdown.isServerComplete("剩余时间：完成"));
        assertFalse(FermentationCountdown.isServerComplete("剩余时间：3秒"));
    }

    @Test
    void projectsRemainingTimeFromMonotonicNanos() {
        assertEquals(1_500L,
                FermentationCountdown.remainingMillis(5_000L, 1_000_000_000L, 4_500_000_000L));
        assertEquals(0L,
                FermentationCountdown.remainingMillis(5_000L, 1_000_000_000L, 8_000_000_000L));
    }
}
