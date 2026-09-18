package com.game.community.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sentinel Gateway入口限流配置。
 */
@Configuration
public class SentinelGatewayConfig {

    @Value("${gateway.sentinel.user-service-qps:50}")
    private double userServiceQps;

    @Value("${gateway.sentinel.steam-service-qps:100}")
    private double steamServiceQps;

    @Value("${gateway.sentinel.shop-service-qps:200}")
    private double shopServiceQps;

    @Value("${gateway.sentinel.ai-agent-service-qps:20}")
    private double aiAgentServiceQps;

    @Value("${gateway.sentinel.user-service-degrade-exception-ratio:0.5}")
    private double userServiceDegradeExceptionRatio;

    @Value("${gateway.sentinel.user-service-degrade-slow-rt-ms:3000}")
    private double userServiceDegradeSlowRtMs;

    @Value("${gateway.sentinel.user-service-degrade-slow-ratio:0.6}")
    private double userServiceDegradeSlowRatio;

    @Value("${gateway.sentinel.user-service-degrade-min-request-amount:20}")
    private int userServiceDegradeMinRequestAmount;

    @Value("${gateway.sentinel.user-service-degrade-stat-interval-ms:10000}")
    private int userServiceDegradeStatIntervalMs;

    @Value("${gateway.sentinel.user-service-degrade-time-window-seconds:10}")
    private int userServiceDegradeTimeWindowSeconds;

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public WebExceptionHandler gatewaySentinelBlockResponseHandler() {
        return (exchange, throwable) -> {
            BlockException blockException = findBlockException(throwable);
            if (blockException == null || exchange.getResponse().isCommitted()) {
                return Mono.error(throwable);
            }
            return writeBlockResponse(exchange, blockException);
        };
    }

    @Bean
    @Order(-1)
    public GlobalFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    @PostConstruct
    public void initSentinelRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();
        rules.add(new GatewayFlowRule("user-service")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(userServiceQps)
                .setIntervalSec(1));
        rules.add(new GatewayFlowRule("steam-service")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(steamServiceQps)
                .setIntervalSec(1));
        rules.add(new GatewayFlowRule("shop-service")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(shopServiceQps)
                .setIntervalSec(1));
        rules.add(new GatewayFlowRule("ai-agent-service")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(aiAgentServiceQps)
                .setIntervalSec(1));
        GatewayRuleManager.loadRules(rules);
        DegradeRuleManager.loadRules(buildDegradeRules());

        BlockRequestHandler blockRequestHandler = (exchange, throwable) -> ServerResponse
                .status(resolveHttpStatus(throwable))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildBlockBody(findBlockException(throwable)));
        GatewayCallbackManager.setBlockHandler(blockRequestHandler);
    }

    private List<DegradeRule> buildDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();
        rules.add(new DegradeRule("user-service")
                .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
                .setCount(userServiceDegradeExceptionRatio)
                .setMinRequestAmount(userServiceDegradeMinRequestAmount)
                .setStatIntervalMs(userServiceDegradeStatIntervalMs)
                .setTimeWindow(userServiceDegradeTimeWindowSeconds));
        rules.add(new DegradeRule("steam-service")
                .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
                .setCount(userServiceDegradeExceptionRatio)
                .setMinRequestAmount(userServiceDegradeMinRequestAmount)
                .setStatIntervalMs(userServiceDegradeStatIntervalMs)
                .setTimeWindow(userServiceDegradeTimeWindowSeconds));
        rules.add(new DegradeRule("steam-service")
                .setGrade(RuleConstant.DEGRADE_GRADE_RT)
                .setCount(userServiceDegradeSlowRtMs)
                .setSlowRatioThreshold(userServiceDegradeSlowRatio)
                .setMinRequestAmount(userServiceDegradeMinRequestAmount)
                .setStatIntervalMs(userServiceDegradeStatIntervalMs)
                .setTimeWindow(userServiceDegradeTimeWindowSeconds));
        rules.add(new DegradeRule("user-service")
                .setGrade(RuleConstant.DEGRADE_GRADE_RT)
                .setCount(userServiceDegradeSlowRtMs)
                .setSlowRatioThreshold(userServiceDegradeSlowRatio)
                .setMinRequestAmount(userServiceDegradeMinRequestAmount)
                .setStatIntervalMs(userServiceDegradeStatIntervalMs)
                .setTimeWindow(userServiceDegradeTimeWindowSeconds));
        return rules;
    }

    private Mono<Void> writeBlockResponse(ServerWebExchange exchange, BlockException blockException) {
        var response = exchange.getResponse();
        response.setStatusCode(resolveHttpStatus(blockException));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = buildBlockBody(blockException).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private HttpStatus resolveHttpStatus(Throwable throwable) {
        return isDegrade(throwable) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.TOO_MANY_REQUESTS;
    }

    private String buildBlockBody(BlockException blockException) {
        if (isDegrade(blockException)) {
            return "{\"code\":503,\"message\":\"服务暂时不可用，已触发熔断保护\",\"data\":{\"blockType\":\"DEGRADE\"}}";
        }
        return "{\"code\":429,\"message\":\"请求过于频繁，已触发限流保护\",\"data\":{\"blockType\":\"FLOW\"}}";
    }

    private boolean isDegrade(Throwable throwable) {
        BlockException blockException = findBlockException(throwable);
        return blockException instanceof DegradeException;
    }

    private BlockException findBlockException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof BlockException blockException) {
                return blockException;
            }
            current = current.getCause();
        }
        return null;
    }
}
