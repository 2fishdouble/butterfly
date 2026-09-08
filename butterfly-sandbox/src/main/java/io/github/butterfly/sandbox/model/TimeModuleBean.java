package io.github.butterfly.sandbox.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class TimeModuleBean {
    private LocalDateTime now = LocalDateTime.now();
    private LocalDate today = LocalDate.now();
    private LocalTime time = LocalTime.now();
}
