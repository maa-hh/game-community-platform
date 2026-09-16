package com.game.community.steam.client;

import com.game.community.common.constant.steam.SteamApiConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.steam.config.SteamProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteamOpenIdService {

    private final RestTemplate restTemplate;
    private final SteamProperties steamProperties;

    /** 根据一次性 state 组装 Steam OpenID 授权地址。 */
    public String buildAuthUrl(String state) {
        String returnTo = UriComponentsBuilder.fromHttpUrl(steamProperties.getOpenidReturnTo())
                .queryParam("state", state)
                .toUriString();
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("openid.ns", "http://specs.openid.net/auth/2.0");
        params.add("openid.mode", "checkid_setup");
        params.add("openid.return_to", returnTo);
        params.add("openid.realm", steamProperties.getOpenidRealm());
        params.add("openid.identity", "http://specs.openid.net/auth/2.0/identifier_select");
        params.add("openid.claimed_id", "http://specs.openid.net/auth/2.0/identifier_select");
        return UriComponentsBuilder.fromHttpUrl(SteamApiConstants.OPENID_ENDPOINT)
                .queryParams(params)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();
    }

    /** 将 Steam 回调参数提交回 OpenID 服务端，验证授权并提取 Steam ID。 */
    public String verifyCallback(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            throw new BusinessException("Steam 回调参数无效");
        }
        String mode = params.get("openid.mode");
        if (!"id_res".equals(mode)) {
            throw new BusinessException("Steam 授权未完成");
        }
        MultiValueMap<String, String> verifyParams = new LinkedMultiValueMap<>();
        params.forEach((key, value) -> {
            // state 等业务参数只用于社区侧关联用户，不属于 OpenID 验证报文。
            if (StringUtils.hasText(key) && key.startsWith("openid.") && value != null) {
                verifyParams.add(key, value);
            }
        });
        verifyParams.set("openid.mode", "check_authentication");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(verifyParams, headers);
        String response = restTemplate.postForObject(SteamApiConstants.OPENID_ENDPOINT, request, String.class);
        if (!StringUtils.hasText(response) || !response.contains("is_valid:true")) {
            log.warn("Steam OpenID 校验失败: {}", response);
            throw new BusinessException("Steam 授权校验失败");
        }
        String claimedId = params.get("openid.claimed_id");
        if (!StringUtils.hasText(claimedId)) {
            throw new BusinessException("Steam 授权信息缺失");
        }
        return extractSteamId(claimedId);
    }

    /** 从 Steam OpenID claimed_id 中提取数字 Steam ID。 */
    private String extractSteamId(String claimedId) {
        String prefix = SteamApiConstants.OPENID_CLAIMED_ID_PREFIX;
        if (!claimedId.startsWith(prefix)) {
            throw new BusinessException("Steam ID 格式无效");
        }
        String steamId = claimedId.substring(prefix.length());
        if (!StringUtils.hasText(steamId)) {
            throw new BusinessException("Steam ID 格式无效");
        }
        return steamId;
    }
}
