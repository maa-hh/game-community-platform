package com.game.community.shop.sentinel;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 商城服务本地兜底限流规则。生产环境可由 Sentinel Dashboard 下发覆盖。
 */
@Configuration
public class ShopSentinelRulesConfig {

    @PostConstruct
    public void loadRules() {
        FlowRule pageRule = rule("shop.item.page", 30);
        FlowRule createRule = rule("shop.order.create", 20);
        FlowRule exchangeRule = rule("shop.exchange", 10);
        FlowRule payRule = rule("shop.order.pay", 20);
        FlowRule currencyRule = rule("shop.currency.read", 30);
        FlowRuleManager.loadRules(List.of(pageRule, createRule, exchangeRule, payRule, currencyRule));
    }

    private FlowRule rule(String resource, double count) {
        FlowRule rule = new FlowRule();
        rule.setResource(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(count);
        return rule;
    }
}
