package io.github.butterfly.mybatis.plus.autoconfigure;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaseEntity {


    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "creator_id", fill = FieldFill.INSERT)
    private Long creatorId;

    @TableField(value = "edit_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime editTime;

    @TableField(value = "editor_id", fill = FieldFill.INSERT_UPDATE)
    private Long editorId;

    @TableField(value = "is_deleted")
    @TableLogic
    private Boolean isDeleted;
}
