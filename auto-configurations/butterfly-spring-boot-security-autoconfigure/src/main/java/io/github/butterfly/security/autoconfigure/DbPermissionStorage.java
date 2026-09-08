package io.github.butterfly.security.autoconfigure;

import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Collection;

/**
 * 数据库存储(存在 {@code DataSource} 时生效)。
 * <p>
 * 使用纯 JDBC 而非 MyBatis/spring-jdbc,让权限字典表的管理尽量不耦合使用方的 ORM。
 * 建表与 Upsert 语句为 MySQL 语法,若需要其他数据库请自行替换方言。
 */
public class DbPermissionStorage implements PermissionStorage {

    private static final String CREATE_AUTHORITY_TABLE = """
            CREATE TABLE IF NOT EXISTS `authority` (
              `id` bigint NOT NULL COMMENT '主键',
              `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '权限名称',
              `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权限说明',
              `ui_description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'ui说明',
              `authority_group_id` bigint DEFAULT NULL COMMENT '权限分组',
              `parent_id` bigint DEFAULT NULL COMMENT '父级id, 如果当前权限存在父级id，此权限被勾选时也会将父级权限勾选，类似于列表',
              PRIMARY KEY (`id`) USING BTREE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='权限信息表'""";

    private static final String CREATE_AUTHORITY_GROUP_TABLE = """
            CREATE TABLE IF NOT EXISTS `authority_group` (
              `id` bigint NOT NULL COMMENT '主键',
              `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分组名称',
              `order` int DEFAULT NULL,
              PRIMARY KEY (`id`) USING BTREE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='权限分组信息表'""";

    private static final String UPSERT_GROUP = """
            INSERT INTO `authority_group` (`id`, `name`, `order`) VALUES (?, ?, ?) AS `new`
            ON DUPLICATE KEY UPDATE `name` = `new`.`name`, `order` = `new`.`order`""";

    private static final String UPSERT_AUTHORITY = """
            INSERT INTO `authority` (`id`, `name`, `description`, `ui_description`, `authority_group_id`, `parent_id`)
            VALUES (?, ?, ?, ?, ?, ?) AS `new`
            ON DUPLICATE KEY UPDATE `name` = `new`.`name`, `description` = `new`.`description`,
            `ui_description` = `new`.`ui_description`, `authority_group_id` = `new`.`authority_group_id`,
            `parent_id` = `new`.`parent_id`""";

    private final DataSource dataSource;

    public DbPermissionStorage(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void store(Collection<? extends IPermissionGroup> groups, Collection<? extends IPermission> permissions) {
        ensureSchema();
        try (Connection connection = this.dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                upsertGroups(connection, groups);
                upsertPermissions(connection, permissions);
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("保存权限到数据库失败", ex);
        }
    }

    private void ensureSchema() {
        try (Connection connection = this.dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_AUTHORITY_GROUP_TABLE);
            statement.execute(CREATE_AUTHORITY_TABLE);
        } catch (SQLException ex) {
            throw new IllegalStateException("创建权限表失败(authority/authority_group)", ex);
        }
    }

    private void upsertGroups(Connection connection, Collection<? extends IPermissionGroup> groups) throws SQLException {
        if (groups.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_GROUP)) {
            for (IPermissionGroup group : groups) {
                statement.setLong(1, group.getId());
                statement.setString(2, group.getTitle());
                statement.setInt(3, group.getOrder());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void upsertPermissions(Connection connection, Collection<? extends IPermission> permissions) throws SQLException {
        if (permissions.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_AUTHORITY)) {
            for (IPermission permission : permissions) {
                statement.setLong(1, permission.getId());
                statement.setString(2, permission.getTitle());
                statement.setString(3, permission.getDescription());
                statement.setString(4, permission.getUiDescription());
                setNullableBigint(statement, 5, groupId(permission.getGroup()));
                setNullableBigint(statement, 6, parentId(permission.getParent()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Nullable
    private static Long groupId(@Nullable IPermissionGroup group) {
        return group == null ? null : group.getId();
    }

    @Nullable
    private static Long parentId(@Nullable IPermission parent) {
        return parent == null ? null : parent.getId();
    }

    private static void setNullableBigint(PreparedStatement statement, int index, @Nullable Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }
}
