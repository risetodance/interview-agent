package interview.guide.common.init;

import interview.guide.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * root 管理员初始化器。
 * <p>应用启动时检查 users 表是否存在 username='root' 的用户；不存在则插入一个默认管理员账号，
 * 供首次部署后登录管理后台、指派 AI 主模型。
 * <ul>
 *   <li>固定 id=0（PostgreSQL 显式插入不消费序列，绕开 JPA IDENTITY 策略覆盖）。</li>
 *   <li>role=ADMIN（@Enumerated(STRING)），前端 AdminRouteGuard 认这个角色。</li>
 *   <li>密码 BCrypt('123456')，弱口令仅用于首登，登录后请尽快走改密码流程覆盖。</li>
 *   <li>已存在则跳过（保留运维改过的密码，不重复初始化）。</li>
 * </ul>
 *
 * <p><b>实现方式</b>：用 {@link JdbcTemplate} 执行原生 INSERT，而非 JPA save。
 * UserEntity 的 id 标注了 @GeneratedValue(IDENTITY)，JPA save 会忽略手动 set 的 id，
 * 导致 root 拿到自增 id 而非 0；原生 SQL 显式写入 id=0 可规避此陷阱。
 * 另外 @PrePersist 仅在 JPA save 时触发，原生 INSERT 不走实体生命周期回调，
 * 故 created_at / updated_at 必须在 SQL 里显式赋 NOW()。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RootUserInitializer implements CommandLineRunner {

    private static final String ROOT_USERNAME = "root";
    private static final String DEFAULT_PASSWORD = "123456";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername(ROOT_USERNAME).isPresent()) {
            log.debug("root 用户已存在，跳过初始化");
            return;
        }
        String encodedPassword = passwordEncoder.encode(DEFAULT_PASSWORD);
        // 显式指定 id=0 绕过 IDENTITY 策略；所有非空与业务字段一并写入，避免依赖 DB 列默认值
        String sql = "INSERT INTO users "
                + "(id, username, password, role, status, points, membership, "
                + "resume_quota_used, interview_quota_used, ai_call_quota_used, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
        int rows;
        try {
            rows = jdbcTemplate.update(sql,
                    0L, ROOT_USERNAME, encodedPassword,
                    "ADMIN", "ACTIVE", 0, "FREE",
                    0, 0, 0);
        } catch (Exception e) {
            // 插入失败必须明确抛出：root 缺失则无法登录后台指派主模型，应用不应继续对外服务
            log.error("root 用户初始化失败，后续无法登录管理后台", e);
            throw new IllegalStateException("root 用户初始化失败：" + e.getMessage(), e);
        }
        if (rows != 1) {
            throw new IllegalStateException("root 用户插入未生效，影响行数=" + rows);
        }
        log.info("已初始化 root 用户（id=0, role=ADMIN），默认密码 {}，请尽快修改", DEFAULT_PASSWORD);
    }
}
