package com.game.community.content.config;

import feign.Retryer;
import feign.Request;
import feign.RetryableException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Feign 的统一超时由 application.yml 管理，重试只允许少量瞬时失败重试。 */
@Configuration
public class FeignResilienceConfig {

    @Bean
    public Retryer feignRetryer() {
        return new SafeRetryer(200, 1000, 2);
    }

    /** 只重试幂等读请求，避免 POST/PUT 因网络超时被重复提交。 */
    private static final class SafeRetryer implements Retryer {

        private final long period;
        private final long maxPeriod;
        private final int maxAttempts;
        private final Retryer.Default delegate;

        private SafeRetryer(long period, long maxPeriod, int maxAttempts) {
            this.period = period;
            this.maxPeriod = maxPeriod;
            this.maxAttempts = maxAttempts;
            this.delegate = new Retryer.Default(period, maxPeriod, maxAttempts);
        }

        @Override
        public void continueOrPropagate(RetryableException exception) {
            Request.HttpMethod method = exception.method();
            if (method != Request.HttpMethod.GET && method != Request.HttpMethod.HEAD) {
                throw exception;
            }
            delegate.continueOrPropagate(exception);
        }

        @Override
        public Retryer clone() {
            return new SafeRetryer(period, maxPeriod, maxAttempts);
        }
    }
}
