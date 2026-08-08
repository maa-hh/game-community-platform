package com.game.community.feign;

import com.game.community.common.constant.gateway.GatewayConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

/**
 * 为服务间 Feign 请求注入当前后端服务契约要求的内部调用凭证。
 *
 * <p>服务间调用绕过 Gateway，必须由调用方自行携带该请求头，才能通过被调用服务的内部请求过滤器。</p>
 */
public class GatewayInternalFeignInterceptor implements RequestInterceptor {

    @Value("${gateway.internal-secret:}")
    private String internalSecret;

    @Override
    public void apply(RequestTemplate template) {
        if (StringUtils.hasText(internalSecret)) {
            template.header(GatewayConstants.INTERNAL_SECRET_HEADER, internalSecret);
        }
    }
}
