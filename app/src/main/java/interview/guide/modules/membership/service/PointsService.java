package interview.guide.modules.membership.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.membership.enums.PointsType;
import interview.guide.modules.membership.model.PointsRecordDTO;
import interview.guide.modules.membership.model.PointsRecordEntity;
import interview.guide.modules.membership.model.SignInStatusResponse;
import interview.guide.modules.membership.repository.PointsRecordRepository;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 积分服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsService {

    private final UserRepository userRepository;
    private final PointsRecordRepository pointsRecordRepository;

    /**
     * 签到积分配置（连续签到天数循环 1-7 天）
     */
    private static final int[] SIGN_IN_POINTS = {1, 2, 3, 4, 5, 6, 7};

    /**
     * 获取用户积分
     *
     * @param userId 用户ID
     * @return 积分余额
     */
    @Transactional(readOnly = true)
    public Integer getPoints(Long userId) {
        UserEntity user = getUserById(userId);
        return user.getPoints();
    }

    /**
     * 获取用户积分记录列表
     *
     * @param userId 用户ID
     * @return 积分记录列表（按时间倒序）
     */
    @Transactional(readOnly = true)
    public List<PointsRecordDTO> getPointsHistory(Long userId) {
        // 验证用户存在
        getUserById(userId);

        List<PointsRecordEntity> records = pointsRecordRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return records.stream()
                .map(this::toPointsRecordDTO)
                .toList();
    }

    /**
     * 添加积分并记录
     *
     * @param userId      用户ID
     * @param points      积分数量（正数）
     * @param type        积分类型
     * @param description 描述
     * @return 更新后的积分余额
     */
    @Transactional(rollbackFor = Exception.class)
    public Integer addPoints(Long userId, Integer points, PointsType type, String description) {
        if (points <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "积分数量必须大于0");
        }

        UserEntity user = getUserById(userId);

        // 更新用户积分（使用 immutable 模式，创建新对象）
        int newPoints = user.getPoints() + points;
        user.setPoints(newPoints);
        userRepository.save(user);

        // 记录积分变动
        PointsRecordEntity record = PointsRecordEntity.builder()
                .userId(userId)
                .points(points)
                .type(type)
                .description(description)
                .build();
        pointsRecordRepository.save(record);

        log.info("用户积分添加成功: userId={}, points={}, type={}, description={}, newPoints={}",
                userId, points, type, description, newPoints);

        return newPoints;
    }

    /**
     * 扣减积分
     *
     * @param userId      用户ID
     * @param points      积分数量（正数）
     * @param description 描述
     * @return 更新后的积分余额
     */
    @Transactional(rollbackFor = Exception.class)
    public Integer deductPoints(Long userId, Integer points, String description) {
        if (points <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "积分数量必须大于0");
        }

        UserEntity user = getUserById(userId);

        // 检查积分是否充足
        if (user.getPoints() < points) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINTS, "积分不足");
        }

        // 更新用户积分
        int newPoints = user.getPoints() - points;
        user.setPoints(newPoints);
        userRepository.save(user);

        // 记录积分变动（使用负数表示扣减）
        PointsRecordEntity record = PointsRecordEntity.builder()
                .userId(userId)
                .points(-points)
                .type(PointsType.EXCHANGE)
                .description(description)
                .build();
        pointsRecordRepository.save(record);

        log.info("用户积分扣减成功: userId={}, points={}, description={}, newPoints={}",
                userId, points, description, newPoints);

        return newPoints;
    }

    /**
     * 签到
     * 根据连续签到天数计算积分，循环 1-7 天
     *
     * @param userId 用户ID
     * @return 签到获得的积分
     */
    @Transactional(rollbackFor = Exception.class)
    public Integer signIn(Long userId) {
        UserEntity user = getUserById(userId);

        // 检查今天是否已签到
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        Optional<PointsRecordEntity> todaySignIn = pointsRecordRepository.findTodaySignIn(
                userId, PointsType.SIGN_IN, startOfDay, endOfDay);

        if (todaySignIn.isPresent()) {
            log.warn("用户今天已签到: userId={}", userId);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "今天已签到");
        }

        // 计算连续签到天数
        int consecutiveDays = getConsecutiveSignInDays(user);

        // 计算本次签到应得积分
        int pointsEarned = getSignInPoints(consecutiveDays);

        // 添加积分
        int newPoints = user.getPoints() + pointsEarned;
        user.setPoints(newPoints);
        userRepository.save(user);

        // 记录积分变动
        PointsRecordEntity record = PointsRecordEntity.builder()
                .userId(userId)
                .points(pointsEarned)
                .type(PointsType.SIGN_IN)
                .description("签到奖励（第" + (consecutiveDays + 1) + "天）")
                .build();
        pointsRecordRepository.save(record);

        log.info("用户签到成功: userId={}, days={}, pointsEarned={}, newPoints={}",
                userId, consecutiveDays + 1, pointsEarned, newPoints);

        return pointsEarned;
    }

    /**
     * 完成面试积分
     * 用户完成一次面试后调用
     *
     * @param userId       用户ID
     * @param interviewId  面试ID
     * @return 获得的积分
     */
    @Transactional(rollbackFor = Exception.class)
    public Integer completeInterview(Long userId, Long interviewId) {
        // 检查是否已经领取过该面试的积分
        if (pointsRecordRepository.existsByUserIdAndTypeAndBusinessId(
                userId, PointsType.COMPLETE_INTERVIEW, interviewId)) {
            log.warn("用户已领取过该面试积分: userId={}, interviewId={}", userId, interviewId);
            return 0;
        }

        // 固定奖励 10 积分
        int pointsEarned = 10;

        UserEntity user = getUserById(userId);

        // 更新用户积分
        int newPoints = user.getPoints() + pointsEarned;
        user.setPoints(newPoints);
        userRepository.save(user);

        // 记录积分变动
        PointsRecordEntity record = PointsRecordEntity.builder()
                .userId(userId)
                .points(pointsEarned)
                .type(PointsType.COMPLETE_INTERVIEW)
                .description("完成面试奖励")
                .businessId(interviewId)
                .build();
        pointsRecordRepository.save(record);

        log.info("用户完成面试获得积分: userId={}, interviewId={}, pointsEarned={}, newPoints={}",
                userId, interviewId, pointsEarned, newPoints);

        return pointsEarned;
    }

    /**
     * 知识库分享积分
     * 用户分享一个知识库后调用
     *
     * @param userId   用户ID
     * @param kbId     知识库ID
     * @return 获得的积分
     */
    @Transactional(rollbackFor = Exception.class)
    public Integer shareKnowledgeBase(Long userId, Long kbId) {
        // 检查是否已经领取过该知识库的分享积分
        if (pointsRecordRepository.existsByUserIdAndTypeAndBusinessId(
                userId, PointsType.SHARE_KB, kbId)) {
            log.warn("用户已领取过该知识库分享积分: userId={}, kbId={}", userId, kbId);
            return 0;
        }

        // 固定奖励 5 积分
        int pointsEarned = 5;

        UserEntity user = getUserById(userId);

        // 更新用户积分
        int newPoints = user.getPoints() + pointsEarned;
        user.setPoints(newPoints);
        userRepository.save(user);

        // 记录积分变动
        PointsRecordEntity record = PointsRecordEntity.builder()
                .userId(userId)
                .points(pointsEarned)
                .type(PointsType.SHARE_KB)
                .description("知识库分享奖励")
                .businessId(kbId)
                .build();
        pointsRecordRepository.save(record);

        log.info("用户知识库分享获得积分: userId={}, kbId={}, pointsEarned={}, newPoints={}",
                userId, kbId, pointsEarned, newPoints);

        return pointsEarned;
    }

    /**
     * 根据连续签到天数获取应得积分
     * 循环 1-7 天
     */
    private int getSignInPoints(int consecutiveDays) {
        // 取模运算，实现循环 1-7 天
        return SIGN_IN_POINTS[consecutiveDays % SIGN_IN_POINTS.length];
    }

    /**
     * 获取连续签到天数
     * TODO: 需要根据用户实体的 lastSignInDate 和 consecutiveDays 字段计算
     */
    private int getConsecutiveSignInDays(UserEntity user) {
        // TODO: 实现连续签到逻辑
        // 1. 检查 lastSignInDate 是否为昨天
        // 2. 如果是昨天，consecutiveDays + 1
        // 3. 如果是今天，返回 consecutiveDays（已经签到）
        // 4. 如果不是昨天也不是今天，重置为 0
        return 0;
    }

    /**
     * 根据用户ID获取用户实体
     */
    private UserEntity getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 将积分记录实体转换为DTO
     * 遵循 immutable 原则，返回新对象
     */
    private PointsRecordDTO toPointsRecordDTO(PointsRecordEntity entity) {
        return PointsRecordDTO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .points(entity.getPoints())
                .type(entity.getType())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * 获取签到状态
     *
     * @param userId 用户ID
     * @return 签到状态
     */
    public SignInStatusResponse getSignInStatus(Long userId) {
        // 检查今天是否已签到
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        Optional<PointsRecordEntity> todaySignIn = pointsRecordRepository.findTodaySignIn(
                userId, PointsType.SIGN_IN, startOfDay, endOfDay);

        boolean signedIn = todaySignIn.isPresent();
        int consecutiveDays = 0;

        if (!signedIn) {
            // 未签到时，计算连续签到天数
            UserEntity user = getUserById(userId);
            consecutiveDays = getConsecutiveSignInDays(user);
        }

        // 计算本次签到可获得的积分
        int pointsCanEarn = getSignInPoints(consecutiveDays);

        return SignInStatusResponse.builder()
                .signedIn(signedIn)
                .consecutiveDays(consecutiveDays)
                .pointsCanEarn(pointsCanEarn)
                .build();
    }
}
