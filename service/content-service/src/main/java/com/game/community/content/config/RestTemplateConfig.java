package com.game.community.content.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpMethod;

/**
 * RestTemplate 统一网络保护：连接/读取超时、有限重试和简单熔断。
 * content-service 当前主要使用 Feign，但保留该 Bean 可避免新增 HTTP 调用绕过基础保护。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getInterceptors().add(new ResilienceInterceptor(2, 200, 5, 10_000));
        return restTemplate;
    }

    private static final class ResilienceInterceptor implements ClientHttpRequestInterceptor {

        private final int maxAttempts;
        private final long retryDelayMs;
        private final int failureThreshold;
        private final long openDurationMs;
        private final AtomicInteger failures = new AtomicInteger();
        private volatile long openUntil;

        private ResilienceInterceptor(int maxAttempts, long retryDelayMs,
                                      int failureThreshold, long openDurationMs) {
            this.maxAttempts = maxAttempts;
            this.retryDelayMs = retryDelayMs;
            this.failureThreshold = failureThreshold;
            this.openDurationMs = openDurationMs;
        }

        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) throws IOException {
            long now = System.currentTimeMillis();
            if (openUntil > now) {
                throw new ResourceAccessException("RestTemplate 熔断中");
            }
            boolean retryable = request.getMethod() == HttpMethod.GET
                    || request.getMethod() == HttpMethod.HEAD;
            int attempts = retryable ? maxAttempts : 1;
            IOException last = null;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    org.springframework.http.client.ClientHttpResponse response = execution.execute(request, body);
                    failures.set(0);
                    return response;
                } catch (IOException e) {
                    last = e;
                    if (attempt < attempts) {
                        try {
                            Thread.sleep(retryDelayMs);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IOException("RestTemplate 重试被中断", interrupted);
                        }
                    }
                }
            }
            if (failures.incrementAndGet() >= failureThreshold) {
                openUntil = System.currentTimeMillis() + openDurationMs;
                failures.set(0);
            }
            throw last == null ? new IOException("RestTemplate 调用失败") : last;
        }
    }
}
