package io.github.butterfly.mybatis.plus.autoconfigure;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;


@AutoConfiguration
public class MybatisPlusAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor mpi = new MybatisPlusInterceptor();
        mpi.addInnerInterceptor(new PaginationInnerInterceptor());
        mpi.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return mpi;
    }
}
