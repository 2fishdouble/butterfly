package io.github.butterfly.core.id;

/**
 * ID 生成器 SPI。
 *
 * <p>autoconfigure 会注册默认实现为 Bean，使用者可在自己代码中提供
 * 同类型 Bean 覆盖它（配合 {@code @ConditionalOnMissingBean}）。
 */
public interface IdGenerator {

    /** 生成下一个全局唯一 ID */
    String next();
}
