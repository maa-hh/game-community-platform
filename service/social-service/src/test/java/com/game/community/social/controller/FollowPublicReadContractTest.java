package com.game.community.social.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.common.constant.gateway.GatewayConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FollowPublicReadContractTest {

    @Test
    void followCountByAccountIsPublicRead() throws Exception {
        assertThat(FollowController.class
                .getMethod("countByAccount", Long.class)
                .isAnnotationPresent(LoginCheck.class)).isFalse();
        assertThat(GatewayConstants.PUBLIC_READ_PATH_PREFIXES.stream()
                .anyMatch("/social/follow/count/by-account/10000"::startsWith)).isTrue();
    }
}
