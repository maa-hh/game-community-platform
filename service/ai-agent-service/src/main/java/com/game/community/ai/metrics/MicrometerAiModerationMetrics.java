package com.game.community.ai.metrics;

import com.game.community.model.enums.aiagent.ModerationContentType;
import com.game.community.model.vo.aiagent.ModerationResultVO;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** 使用 Micrometer 输出审核、Provider 和降级指标。 */
@Component
@RequiredArgsConstructor
public class MicrometerAiModerationMetrics implements AiModerationMetrics {

    private final MeterRegistry meterRegistry;

    /** 记录审核总量、结果分布和端到端耗时。 */
    @Override
    public void recordRequest(ModerationContentType type, ModerationResultVO result, long durationMs) {
        Counter.builder("ai.moderation.requests")
                .tag("type", value(type))
                .tag("result", value(result == null ? null : result.getResult()))
                .tag("provider", value(result == null ? null : result.getProvider()))
                .register(meterRegistry)
                .increment();
        Timer.builder("ai.moderation.duration")
                .tag("type", value(type))
                .register(meterRegistry)
                .record(Math.max(0, durationMs), TimeUnit.MILLISECONDS);
    }

    /** 记录 Provider 网络调用成功率和耗时。 */
    @Override
    public void recordProviderCall(String provider, String model, boolean success, long durationMs) {
        Counter.builder("ai.provider.calls")
                .tag("provider", value(provider))
                .tag("model", value(model))
                .tag("status", success ? "success" : "failure")
                .register(meterRegistry)
                .increment();
        Timer.builder("ai.provider.duration")
                .tag("provider", value(provider))
                .tag("model", value(model))
                .register(meterRegistry)
                .record(Math.max(0, durationMs), TimeUnit.MILLISECONDS);
    }

    /** 记录转人工审核次数。 */
    @Override
    public void recordFallback(ModerationContentType type) {
        Counter.builder("ai.moderation.fallbacks")
                .tag("type", value(type))
                .register(meterRegistry)
                .increment();
    }

    /** 将空标签归一化，避免指标标签出现 null。 */
    private String value(Object value) {
        return value == null ? "unknown" : value.toString();
    }
}
