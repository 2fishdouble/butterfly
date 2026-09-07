package io.github.butterfly.autoconfigure;


import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@AutoConfiguration
@ConditionalOnProperty(prefix = "butterfly", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ButterflyAutoConfiguration {

}
