package io.github.butterfly.core.id;

import java.util.UUID;

/**
 * 基于 UUID 的 {@link IdGenerator}：生成 32 位小写十六进制（去掉连字符）。
 */
public class UuidIdGenerator implements IdGenerator {

    @Override
    public String next() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
