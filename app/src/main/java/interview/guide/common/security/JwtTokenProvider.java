package interview.guide.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT Token 工具类
 * 提供 Token 生成、解析、验证等功能
 */
@Component
public final class JwtTokenProvider {

    private static final String CLAIM_KEY_USER_ID = "userId";
    private static final String CLAIM_KEY_USERNAME = "username";
    private static final String CLAIM_KEY_ROLE = "role";

    /**
     * Token 有效期: 7 天
     */
    private static final long TOKEN_EXPIRATION = 7 * 24 * 60 * 60 * 1000L;

    /**
     * JWT 密钥 (生产环境应从配置读取)
     * 使用 HS512 算法，密钥长度至少 512 位
     */
    private static final String SECRET = "interview-guide-jwt-secret-key-must-be-at-least-512-bits-long-for-hs512";

    private final SecretKey secretKey;

    /**
     * 私有构造器，使用单例模式
     */
    private JwtTokenProvider() {
        this.secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 获取 JwtTokenProvider 实例 (懒加载)
     */
    public static JwtTokenProvider getInstance() {
        return JwtTokenProviderHolder.INSTANCE;
    }

    /**
     * 生成 JWT Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param role     用户角色
     * @return JWT Token 字符串
     */
    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + TOKEN_EXPIRATION);

        return Jwts.builder()
                .subject(username)
                .claims(Map.of(
                        CLAIM_KEY_USER_ID, userId,
                        CLAIM_KEY_USERNAME, username,
                        CLAIM_KEY_ROLE, role
                ))
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * 解析 Token 获取 Claims
     *
     * @param token JWT Token 字符串
     * @return Claims 对象
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证 Token 是否有效
     *
     * @param token JWT Token 字符串
     * @return true: 有效, false: 无效或已过期
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 从 Token 获取用户ID
     *
     * @param token JWT Token 字符串
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_KEY_USER_ID, Long.class);
    }

    /**
     * 从 Token 获取用户名
     *
     * @param token JWT Token 字符串
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_KEY_USERNAME, String.class);
    }

    /**
     * 从 Token 获取用户角色
     *
     * @param token JWT Token 字符串
     * @return 用户角色
     */
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_KEY_ROLE, String.class);
    }

    /**
     * 获取 Token 过期时间
     *
     * @param token JWT Token 字符串
     * @return 过期时间
     */
    public Date getExpirationFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getExpiration();
    }

    /**
     * 内部类实现延迟加载单例
     */
    private static class JwtTokenProviderHolder {
        private static final JwtTokenProvider INSTANCE = new JwtTokenProvider();
    }
}
