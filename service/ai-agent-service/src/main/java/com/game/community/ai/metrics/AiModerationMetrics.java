package com.game.community.ai.metrics;

import com.game.community.model.enums.aiagent.ModerationContentType;
import com.game.community.model.vo.aiagent.ModerationResultVO;

/** AI 审核和 Provider 调用指标接口。 */
public interface AiModerationMetrics {

    /** 记录一次完整审核请求和最终结果。 */
    void recordRequest(ModerationContentType type, ModerationResultVO result, long durationMs);

    /** 记录一次 Provider 网络调用及成功失败。 */
    void recordProviderCall(String provider, String model, boolean success, long durationMs);

    /** 记录一次人工审核降级。 */
    void recordFallback(ModerationContentType type);

    /** 单元测试使用的无指标实现。 */
    static AiModerationMetrics noOp() {
        return new AiModerationMetrics() {
            @Override
            public void recordRequest(ModerationContentType type, ModerationResultVO result, long durationMs) {
            }

            @Override
            public void recordProviderCall(String provider, String model, boolean success, long durationMs) {
            }

            @Override
            public void recordFallback(ModerationContentType type) {
            }
        };
    }
}
