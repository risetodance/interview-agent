package interview.guide.modules.admin.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 系统配置实体单元测试
 */
@DisplayName("系统配置实体测试")
class SystemConfigEntityTest {

    @Nested
    @DisplayName("实体字段验证测试")
    class FieldValidationTests {

        @Test
        @DisplayName("使用 Builder 创建实体时，ID 应该为 null")
        void testBuilder_ShouldReturnNullId() {
            SystemConfigEntity entity = SystemConfigEntity.builder()
                .configKey("site.name")
                .configValue("Interview Guide")
                .description("网站名称")
                .configType("STRING")
                .editable(true)
                .build();

            assertNull(entity.getId());
        }

        @Test
        @DisplayName("使用 Builder 创建实体时，所有字段应正确设置")
        void testBuilder_ShouldSetAllFields() {
            String configKey = "site.name";
            String configValue = "Interview Guide";
            String description = "网站名称";
            String configType = "STRING";
            Boolean editable = true;

            SystemConfigEntity entity = SystemConfigEntity.builder()
                .configKey(configKey)
                .configValue(configValue)
                .description(description)
                .configType(configType)
                .editable(editable)
                .build();

            assertEquals(configKey, entity.getConfigKey());
            assertEquals(configValue, entity.getConfigValue());
            assertEquals(description, entity.getDescription());
            assertEquals(configType, entity.getConfigType());
            assertEquals(editable, entity.getEditable());
        }

        @Test
        @DisplayName("默认 editable 应该是 true")
        void testDefaultEditable_ShouldBeTrue() {
            SystemConfigEntity entity = SystemConfigEntity.builder()
                .configKey("test.key")
                .configValue("test")
                .build();

            assertTrue(entity.getEditable());
        }

        @Test
        @DisplayName("支持不同配置类型的创建")
        void testBuilder_ShouldSupportDifferentConfigTypes() {
            // STRING 类型
            SystemConfigEntity stringConfig = SystemConfigEntity.builder()
                .configKey("site.name")
                .configValue("value")
                .configType("STRING")
                .build();
            assertEquals("STRING", stringConfig.getConfigType());

            // INTEGER 类型
            SystemConfigEntity intConfig = SystemConfigEntity.builder()
                .configKey("site.maxUploadSize")
                .configValue("10485760")
                .configType("INTEGER")
                .build();
            assertEquals("INTEGER", intConfig.getConfigType());

            // BOOLEAN 类型
            SystemConfigEntity boolConfig = SystemConfigEntity.builder()
                .configKey("site.maintenance")
                .configValue("false")
                .configType("BOOLEAN")
                .build();
            assertEquals("BOOLEAN", boolConfig.getConfigType());

            // JSON 类型
            SystemConfigEntity jsonConfig = SystemConfigEntity.builder()
                .configKey("site.features")
                .configValue("{\"enabled\": true}")
                .configType("JSON")
                .build();
            assertEquals("JSON", jsonConfig.getConfigType());
        }
    }

    @Nested
    @DisplayName("JPA 生命周期回调测试")
    class JpaLifecycleTests {

        @Test
        @DisplayName("保存前应自动设置 createdAt 和 updatedAt")
        void testPrePersist_ShouldSetTimestamps() {
            SystemConfigEntity entity = SystemConfigEntity.builder()
                .configKey("test.key")
                .configValue("test")
                .build();

            entity.onCreate();

            assertNotNull(entity.getCreatedAt());
            assertNotNull(entity.getUpdatedAt());
        }

        @Test
        @DisplayName("更新前应自动更新 updatedAt")
        void testPreUpdate_ShouldUpdateTimestamp() {
            SystemConfigEntity entity = SystemConfigEntity.builder()
                .configKey("test.key")
                .configValue("test")
                .build();

            LocalDateTime originalTime = LocalDateTime.now();
            entity.setCreatedAt(originalTime);
            entity.setUpdatedAt(originalTime);

            entity.setConfigValue("updated");
            entity.onUpdate();

            assertTrue(entity.getUpdatedAt().isAfter(originalTime) ||
                       entity.getUpdatedAt().isEqual(originalTime));
        }
    }
}
