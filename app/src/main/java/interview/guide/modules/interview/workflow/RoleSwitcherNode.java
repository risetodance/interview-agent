package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.model.InterviewerRoleEntity;
import interview.guide.modules.interview.repository.InterviewerRoleRepository;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
                state.updateState(Map.of(
                        "currentPerspectiveId", nextPerspectiveId,
                        "nextPerspectiveId", 0L  // 清除临时变量
                ));
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
                        session.getSelectedPerspectives(), new TypeReference<List<Long>>() {});
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
            Map<String, Object> updatedState = new HashMap<>();
            updatedState.put("currentPerspectiveId", newPerspectiveId);
            updatedState.put("nextPerspectiveId", 0L);  // 清除临时变量

            // 获取新视角信息
            Optional<InterviewerRoleEntity> roleOpt = interviewerRoleRepository.findById(newPerspectiveId);
            if (roleOpt.isPresent()) {
                InterviewerRoleEntity role = roleOpt.get();
                updatedState.put("currentPerspectiveName", role.getRoleName());
                log.info("Role switcher: switched to perspective {} ({})", role.getRoleName(), newPerspectiveId);
            }

            state.updateState(updatedState);

        } catch (Exception e) {
            log.error("Role switcher node error: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }

        return state;
    }
}
