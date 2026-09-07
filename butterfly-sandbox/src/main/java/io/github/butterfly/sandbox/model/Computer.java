package io.github.butterfly.sandbox.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class Computer {
    private Long id;
    private String name;
    private LocalDateTime createTime;
    private List<Product> products;

    @Data
    public static class Product {
        private Long id;
        private String name;
        private String description;
        private BigDecimal price;
        private Integer quantity;
        private BigDecimal totalAmount;
    }
}

