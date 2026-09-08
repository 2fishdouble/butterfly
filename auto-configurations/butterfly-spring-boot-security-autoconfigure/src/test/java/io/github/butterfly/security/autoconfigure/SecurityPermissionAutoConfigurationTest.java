package io.github.butterfly.security.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPermissionAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityPermissionAutoConfiguration.class))
            .withPropertyValues("butterfly.security.permission.scan-packages=" + SamplePermission.class.getPackageName());

    @Test
    void noDataSourceFallsBackToInMemoryAndCollectsEnums() {
        this.contextRunner.run(context -> {
            PermissionStorage storage = context.getBean(PermissionStorage.class);
            assertThat(storage).isInstanceOf(InMemoryPermissionStorage.class);

            InMemoryPermissionStorage memory = (InMemoryPermissionStorage) storage;
            assertThat(memory.getGroup(1L)).isEqualTo(SamplePermissionGroup.SYSTEM);
            assertThat(memory.getGroup(2L)).isEqualTo(SamplePermissionGroup.SALE);
            assertThat(memory.getPermission(100L)).isEqualTo(SamplePermission.SYSTEM_PAGE);
            assertThat(memory.getPermission(101L)).isEqualTo(SamplePermission.SYSTEM_USER_ADD);
            assertThat(memory.getPermission(201L)).isEqualTo(SamplePermission.SALE_EXPORT);
            assertThat(memory.permissions()).hasSize(SamplePermission.values().length);
            assertThat(memory.groups()).hasSize(SamplePermissionGroup.values().length);
        });
    }

    @Test
    void disabledSkipsStorageAndCollector() {
        this.contextRunner.withPropertyValues("butterfly.security.permission.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(PermissionStorage.class);
                    assertThat(context).doesNotHaveBean(PermissionCollector.class);
                });
    }
}
