package com.game.community.steam.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.payload.steam.SteamOwnedGamesPayload;
import com.game.community.model.payload.steam.SteamPlayerSummaryPayload;
import com.game.community.steam.config.SteamProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SteamApiClientTest {

    @Mock
    private RestTemplate restTemplate;

    private SteamApiClient steamApiClient;

    @BeforeEach
    void setUp() {
        SteamProperties properties = new SteamProperties();
        properties.setWebApiKey("test-key");
        steamApiClient = new SteamApiClient(restTemplate, properties, new ObjectMapper());
    }

    /** Steam 请求超时时应提示稍后重试，不能误报为游戏库未公开。 */
    @Test
    void getOwnedGamesShouldNotTreatNetworkFailureAsPrivateLibrary() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> steamApiClient.getOwnedGames("76561198000000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Steam 游戏库请求失败，请稍后重试");
    }

    /** Steam 正常返回空响应时才判定为游戏库未公开。 */
    @Test
    void getOwnedGamesShouldRecognizePrivateLibraryResponse() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn("{\"response\":{}}");

        SteamOwnedGamesPayload result = steamApiClient.getOwnedGames("76561198000000000");

        assertThat(result.isLibraryPublic()).isFalse();
        assertThat(result.getGames()).isEmpty();
    }

    /** 英文名补充失败不应丢弃已经成功获取的中文游戏库。 */
    @Test
    void getOwnedGamesShouldKeepChineseLibraryWhenEnglishRequestFails() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn("{\"response\":{\"game_count\":1,\"games\":[{\"appid\":10,\"name\":\"反恐精英\"}]}}")
                .thenThrow(new ResourceAccessException("timeout"));

        SteamOwnedGamesPayload result = steamApiClient.getOwnedGames("76561198000000000");

        assertThat(result.isLibraryPublic()).isTrue();
        assertThat(result.getGameCount()).isEqualTo(1);
        assertThat(result.getGames()).singleElement()
                .satisfies(game -> {
                    assertThat(game.getName()).isEqualTo("反恐精英");
                    assertThat(game.getNameZh()).isEqualTo("反恐精英");
                });
    }

    /** Steam 历史 HTTP 头像必须升级为 HTTPS，避免移动端浏览器拦截。 */
    @Test
    void getPlayerSummaryShouldNormalizeAvatarToHttps() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn("{\"response\":{\"players\":[{\"steamid\":\"76561198000000000\","
                        + "\"avatarfull\":\"http://avatars.steamstatic.com/avatar.jpg\"}]}}");

        SteamPlayerSummaryPayload result = steamApiClient.getPlayerSummary("76561198000000000");

        assertThat(result.getAvatarUrl())
                .isEqualTo("https://avatars.steamstatic.com/avatar.jpg");
    }
}
