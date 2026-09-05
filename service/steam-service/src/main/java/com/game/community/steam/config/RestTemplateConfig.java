package com.game.community.steam.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@Configuration
public class RestTemplateConfig {

    private final SteamProperties steamProperties;

    public RestTemplateConfig(SteamProperties steamProperties) {
        this.steamProperties = steamProperties;
    }

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getInterceptors().add(new SteamRateLimitInterceptor(
                steamProperties.getRequestIntervalMs(),
                steamProperties.getRateLimitMaxAttempts(),
                steamProperties.getRateLimitRetryDelayMs()));
        return restTemplate;
    }

    /**
     * Steam 服务所有外部请求共用的串行限流器。
     *
     * <p>Kafka 只负责搜索索引事件，不能约束 RestTemplate 的出站并发；
     * 因此在统一 HTTP 出口限流，确保榜单、详情、评价和价格请求共用一个节流点。</p>
     */
    private static final class SteamRateLimitInterceptor
            implements ClientHttpRequestInterceptor {

        private final long requestIntervalMs;
        private final int maxAttempts;
        private final long retryDelayMs;
        private long lastRequestAt;

        private SteamRateLimitInterceptor(
                long requestIntervalMs, int maxAttempts, long retryDelayMs) {
            this.requestIntervalMs = Math.max(0L, requestIntervalMs);
            this.maxAttempts = Math.max(1, maxAttempts);
            this.retryDelayMs = Math.max(0L, retryDelayMs);
        }

        @Override
        public synchronized ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution)
                throws IOException {
            boolean retryable = request.getMethod() == HttpMethod.GET
                    || request.getMethod() == HttpMethod.HEAD;
            int attempts = retryable ? maxAttempts : 1;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                awaitNextRequest();
                ClientHttpResponse response = execution.execute(request, body);
                if (response.getStatusCode().value() != 429 || attempt >= attempts) {
                    return response;
                }
                response.close();
                sleep(retryDelayMs * attempt);
            }
            throw new IOException("Steam 请求重试失败");
        }

        private void awaitNextRequest() throws IOException {
            long waitMs = requestIntervalMs - (System.currentTimeMillis() - lastRequestAt);
            if (waitMs > 0L) {
                sleep(waitMs);
            }
            lastRequestAt = System.currentTimeMillis();
        }

        private void sleep(long delayMs) throws IOException {
            if (delayMs <= 0L) {
                return;
            }
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Steam 请求限流等待被中断", e);
            }
        }
    }
}
