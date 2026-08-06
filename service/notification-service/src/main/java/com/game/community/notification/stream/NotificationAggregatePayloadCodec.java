package com.game.community.notification.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.model.vo.notification.NotificationActorVO;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NotificationAggregatePayloadCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NotificationAggregatePayloadCodec() {
    }

    public static String encode(List<NotificationActorVO> actors, int total) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actors", actors == null ? List.of() : actors);
        payload.put("total", total);
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    public static NotificationAggregatePayload decode(String resultText) {
        if (!StringUtils.hasText(resultText) || !resultText.trim().startsWith("{")) {
            return null;
        }
        try {
            return MAPPER.readValue(resultText, new TypeReference<NotificationAggregatePayload>() {
            });
        } catch (Exception e) {
            return null;
        }
    }
}
