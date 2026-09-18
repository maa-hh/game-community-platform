package com.game.community.ai.resilience;

import com.game.community.ai.agent.AgentModelRegistry;

import java.util.function.Supplier;

/** Provider 级限速、并发隔离和熔断接口。 */
public interface ProviderResilienceGuard {

    /** 在 Provider 保护策略下执行一次模型调用。 */
    <T> T execute(AgentModelRegistry.ModelClient model, Supplier<T> action);

    /** 单元测试使用的直通实现。 */
    static ProviderResilienceGuard allowAll() {
        return new ProviderResilienceGuard() {
            @Override
            public <T> T execute(AgentModelRegistry.ModelClient model, Supplier<T> action) {
                return action.get();
            }
        };
    }
}
