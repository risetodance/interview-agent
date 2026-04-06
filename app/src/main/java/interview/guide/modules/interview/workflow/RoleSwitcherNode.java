package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.model.InterviewerRoleEntity;
import interview.guide.modules.interview.repository.InterviewerRoleRepository;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 角色切换节点 - 根据 DeciderNode 的决策切换视角
 * 实际的视角切换逻辑在此节点中实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleSwitcherNode {

    private final InterviewPersistenceService persistenceService;
    private final InterviewerRoleRepository interviewerRoleRepository;
    private final ObjectMapper objectMapper;

    public OverAllState execute(OverAllState state) {
        String sessionId = (String) state.value("sessionId").orElse(null);
        Long currentPerspectiveId = ((Number) state.value("currentPerspectiveId").orElse(0L)).longValue();
        Long nextPerspectiveId = ((Number) state.value("nextPerspectiveId").orElse(0L)).longValue();

        log.info("Role switcher node: sessionId={}, currentPerspectiveId={}, nextPerspectiveId={}",
                sessionId, currentPerspectiveId, nextPerspectiveId);

        if (sessionId == null) {
            log.error("Role switcher node: sessionId is null");
            return state;
        }

        try {
            // 如果已有明确的 nextPerspectiveId（由 DeciderNode 设置），直接使用
            if (nextPerspectiveId > 0) {
                log.info("Role switcher: using nextPerspectiveId from decider={}", nextPerspectiveId);
                Map<String, Object> switchState = getSwitchState(sessionId, nextPerspectiveId);
                state.updateState(switchState);
                return state;
            }

            // 否则根据规则选择下一个视角
            Optional<InterviewSessionEntity> sessionOpt = persistenceService.findBySessionId(sessionId);
            if (sessionOpt.isEmpty()) {
                log.error("Role switcher node: session not found, sessionId={}", sessionId);
                return state;
            }

            InterviewSessionEntity session = sessionOpt.get();
            if (session.getSelectedPerspectives() == null || session.getSelectedPerspectives().isBlank()) {
                log.warn("Role switcher node: no selected perspectives, sessionId={}", sessionId);
                return state;
            }

            // 解析已选择的视角列表
            List<Long> selectedPerspectives;
            try {
                selectedPerspectives = objectMapper.readValue(
                        session.getSelectedPerspectives(), new TypeReference<>() {
                        });
            } catch (Exception e) {
                log.warn("解析 selectedPerspectives 失败: {}", e.getMessage());
                return state;
            }

            if (selectedPerspectives == null || selectedPerspectives.isEmpty()) {
                return state;
            }

            // 找到当前视角的下一个视角（轮询）
            int currentIndex = selectedPerspectives.indexOf(currentPerspectiveId);
            int nextIndex = (currentIndex + 1) % selectedPerspectives.size();
            Long newPerspectiveId = selectedPerspectives.get(nextIndex);

            log.info("Role switcher: switching from perspective {} to {}, nextIndex={}",
                    currentPerspectiveId, newPerspectiveId, nextIndex);

            // 更新状态
            Map<String, Object> switchState = getSwitchState(sessionId, newPerspectiveId);
            state.updateState(switchState);

        } catch (Exception e) {
            log.error("Role switcher node error: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }

        return state;
    }

    @NotNull
    private Map<String, Object> getSwitchState(String sessionId, Long nextPerspectiveId) {
        // 获取该视角的最新答题数据
        Optional<InterviewAnswerEntity> perspectiveAnswers = persistenceService.findLastAnswerBySessionAndPerspective(sessionId, nextPerspectiveId);
        Map<String, Object> switchState = new HashMap<>();
        switchState.put("currentPerspectiveId", nextPerspectiveId);
        switchState.put("nextPerspectiveId", 0L);

        InterviewAnswerEntity latestAnswer = null;
        if (perspectiveAnswers.isPresent()) {
            latestAnswer = perspectiveAnswers.get();
        }

        switchState.put("currentQuestion", latestAnswer != null && latestAnswer.getQuestion() != null ? latestAnswer.getQuestion() : "");
        switchState.put("userAnswer", latestAnswer != null && latestAnswer.getUserAnswer() != null ? latestAnswer.getUserAnswer() : "");
        switchState.put("feedback", latestAnswer != null && latestAnswer.getFeedback() != null ? latestAnswer.getFeedback() : "");
        switchState.put("currentCategory", latestAnswer != null && latestAnswer.getCategory() != null ? latestAnswer.getCategory() : "");


        // 获取新视角信息
        Optional<InterviewerRoleEntity> roleOpt = interviewerRoleRepository.findById(nextPerspectiveId);
        if (roleOpt.isPresent()) {
            InterviewerRoleEntity role = roleOpt.get();
            switchState.put("currentPerspectiveName", role.getRoleName());
            log.info("Role switcher: switched to perspective {} ({})", role.getRoleName(), nextPerspectiveId);
        }
        return switchState;
    }
}
