package io.github.butterfly.redis.autoconfigure;


import cn.hutool.core.lang.Validator;
import io.github.butterfly.autoconfigure.SpelSup;
import io.github.butterfly.core.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;


@Aspect
@Component
public class IdempotentAspect {


    private final RedissonClient redissonClient;
    private final SpelSup spelSup;


    public IdempotentAspect(
                            SpelSup spelSup,
                            RedissonClient redissonClient) {
        this.spelSup = spelSup;
        this.redissonClient = redissonClient;
    }


    @Pointcut(value = "@annotation(io.github.butterfly.redis.autoconfigure.Idempotent)")
    public void cut() {
    }

    @Around("cut()")
    public Object interceptor(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);

        if (idempotent == null) {
            return joinPoint.proceed();
        }

        String key = buildKey(idempotent, method, joinPoint.getArgs());

        RLock lock = redissonClient.getLock(key);

        boolean locked = false;
        try {
            locked = lock.tryLock(0, -1, TimeUnit.SECONDS);

            if (!locked) {
                throw new BusinessException("请稍后再试");
            }

            return joinPoint.proceed();

        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }


    private String buildKey(Idempotent rateLimit, Method method, Object[] args) {
        String value = method.getDeclaringClass().getName() + "." + method.getName();
        String spElValue = spelSup.parseSpel(method, rateLimit.lockValue(), args);
        if (Validator.isNotEmpty(spElValue)) {
            value = value + "#" + spElValue;
        }
        return value;
    }
}

