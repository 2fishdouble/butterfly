package io.github.butterfly.autoconfigure;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.ArrayUtil;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;


@Component
public class SpelSup {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    private static final DefaultParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private final BeanResolver beanResolver;

    public SpelSup(BeanFactory beanFactory) {
        this.beanResolver = new BeanFactoryResolver(beanFactory);
    }

    public String parseSpel(Method method, String[] keys, Object[] args) {
        StringBuilder sbu = new StringBuilder();
        try {
            if (!ArrayUtil.isEmpty(keys)) {
                StandardEvaluationContext context = new MethodBasedEvaluationContext(null, method, args, NAME_DISCOVERER);
                context.setBeanResolver(beanResolver);
                for (int i = 0; i < keys.length; i++) {
                    String value = keys[i];
                    if (Validator.isNotEmpty(value)) {
                        String parseValue = PARSER.parseExpression(value).getValue(context, String.class);
                        sbu.append(parseValue);
                        if (i < keys.length - 1) {
                            sbu.append(".");
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Parse SpEL failed", e);
        }
        return sbu.toString();
    }
}

