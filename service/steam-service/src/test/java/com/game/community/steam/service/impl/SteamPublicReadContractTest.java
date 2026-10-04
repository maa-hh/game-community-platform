package com.game.community.steam.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.annotation.LoginCheck;
import com.game.community.common.constant.gateway.GatewayConstants;
import com.game.community.feign.SocialFeignClient;
import com.game.community.feign.UserFeignClient;
import com.game.community.model.base.Result;
import com.game.community.model.entity.game.UserSteamBind;
import com.game.community.model.vo.game.SteamBindVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.steam.client.SteamApiClient;
import com.game.community.steam.client.SteamOpenIdService;
import com.game.community.steam.controller.SteamBindController;
import com.game.community.steam.mapper.UserSteamBindMapper;
import com.game.community.steam.mapper.UserSteamGameMapper;
import com.game.community.steam.service.GameCatalogService;
import com.game.community.utils.RedisUtils;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SteamPublicReadContractTest {

    @Mock
    private SteamOpenIdService steamOpenIdService;
    @Mock
    private SteamApiClient steamApiClient;
    @Mock
    private GameCatalogService gameCatalogService;
    @Mock
    private SocialFeignClient socialFeignClient;
    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private RedisUtils redisUtils;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private UserSteamBindMapper userSteamBindMapper;
    @Mock
    private UserSteamGameMapper userSteamGameMapper;
    @Mock
    private SteamAchievementRefreshService steamAchievementRefreshService;

    private SteamServiceImpl service;

    @BeforeEach
    void setUp() {
        UserThreadLocal.removeUser();
        service = new SteamServiceImpl(
                steamOpenIdService,
                steamApiClient,
                gameCatalogService,
                socialFeignClient,
                userFeignClient,
                redisUtils,
                new ObjectMapper(),
                transactionTemplate,
                userSteamBindMapper,
                userSteamGameMapper,
                steamAchievementRefreshService);
    }

    @AfterEach
    void tearDown() {
        UserThreadLocal.removeUser();
    }

    @Test
    void anonymousViewerCanReadPublicSteamProfile() {
        long accountId = 10000L;
        long userId = 7L;
        UserCardInternalVO user = new UserCardInternalVO();
        user.setUserId(userId);
        user.setAccountId(accountId);
        when(userFeignClient.getUserByAccountId(accountId)).thenReturn(Result.success(user));
        when(userFeignClient.getUsersByUserIds(List.of(userId)))
                .thenReturn(Result.success(List.of(user)));

        UserSteamBind bind = new UserSteamBind();
        bind.setUserId(userId);
        bind.setSteamId("76561198000000000");
        bind.setPersonaName("Public player");
        bind.setAvatarUrl("https://example.test/avatar.jpg");
        bind.setLibraryPublic(1);
        when(userSteamBindMapper.selectById(userId)).thenReturn(bind);

        SteamBindVO result = service.profileForViewerByAccount(accountId);

        assertThat(result.getAccountId()).isEqualTo(accountId);
        assertThat(result.getSteamId()).isEqualTo("76561198000000000");
        verifyNoInteractions(socialFeignClient);
    }

    @Test
    void publicSteamRoutesDoNotRequireLoginAtControllerOrGateway() throws Exception {
        assertThat(SteamBindController.class
                .getMethod("profileForUserByAccount", Long.class)
                .isAnnotationPresent(LoginCheck.class)).isFalse();
        assertThat(SteamBindController.class
                .getMethod("libraryForUserByAccount", Long.class)
                .isAnnotationPresent(LoginCheck.class)).isFalse();
        assertThat(GatewayConstants.PUBLIC_READ_PATH_PREFIXES.stream()
                .anyMatch("/steam/users/by-account/10000/profile"::startsWith)).isTrue();
        assertThat(GatewayConstants.PUBLIC_READ_PATH_PREFIXES.stream()
                .anyMatch("/steam/users/by-account/10000/library"::startsWith)).isTrue();
    }
}
