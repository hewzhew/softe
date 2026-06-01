package com.bupt.charging.support;

import java.time.LocalDateTime;

public interface TimeProvider {
    LocalDateTime now();

    static TimeProvider system() {
        return LocalDateTime::now;
    }
}
