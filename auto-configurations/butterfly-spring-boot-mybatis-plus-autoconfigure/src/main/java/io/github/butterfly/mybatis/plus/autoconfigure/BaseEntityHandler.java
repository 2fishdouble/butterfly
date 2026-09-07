package io.github.butterfly.mybatis.plus.autoconfigure;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;


@Component
public class BaseEntityHandler implements MetaObjectHandler {


    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();

        if (findField(metaObject, "create_time") && getFieldValByName("createTime", metaObject) == null) {
            this.setFieldValByName("createTime", now, metaObject);
        }

        if (findField(metaObject, "edit_time") && getFieldValByName("editTime", metaObject) == null) {
            this.setFieldValByName("editTime", now, metaObject);
        }

    }


    @Override
    public void updateFill(MetaObject metaObject) {
        this.setFieldValByName("editTime", LocalDateTime.now(), metaObject);
    }


    private boolean findField(MetaObject metaObject, String fieldName) {
        String property = metaObject.getObjectWrapper().findProperty(fieldName, true);
        return Objects.nonNull(property);
    }
}
