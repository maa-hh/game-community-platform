package com.game.community.ai.concurrency;

/** Provider 维度的模型并发许可接口。 */
public interface ModelConcurrencyLimiter {

    /** 尝试获取指定 Provider 的全局并发许可。 */
    boolean tryAcquire(String provider);

    /** 释放指定 Provider 的全局并发许可。 */
    void release(String provider);

    /** 创建测试和本地无共享存储场景使用的放行实现。 */
    static ModelConcurrencyLimiter allowAll() {
        return new ModelConcurrencyLimiter() {
            @Override
            public boolean tryAcquire(String provider) {
                return true;
            }

            @Override
            public void release(String provider) {
                // 无共享存储时无需释放。
            }
        };
    }
}
