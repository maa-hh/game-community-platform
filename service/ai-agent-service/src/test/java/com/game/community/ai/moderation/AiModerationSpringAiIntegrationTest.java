package com.game.community.ai.moderation;

import com.game.community.ai.agent.AgentModelRegistry;
import com.game.community.ai.config.AgentModelProperties;
import com.game.community.ai.config.ModerationProperties;
import com.game.community.ai.service.impl.AiModerationServiceImpl;
import com.game.community.model.dto.aiagent.ModerationRequest;
import com.game.community.model.dto.aiagent.ModerationImageRequest;
import com.game.community.model.enums.aiagent.ModerationContentType;
import com.game.community.model.enums.aiagent.ModerationDecision;
import com.game.community.model.vo.aiagent.ModerationResultVO;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用本地 OpenAI 兼容桩验证 Spring AI 完整调用与结构化输出。 */
class AiModerationSpringAiIntegrationTest {

    private HttpServer server;

    /** 关闭测试 HTTP 服务，避免占用随机端口。 */
    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** 验证真实 ChatClient 请求、结构化评分和 token 指标映射。 */
    @Test
    void callsOpenAiCompatibleModelThroughSpringAi() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(requestBody.contains("正常的游戏攻略分享"));
            byte[] response = ("""
                    {"id":"chatcmpl-test","object":"chat.completion","created":1,"model":"test-text",
                    "choices":[{"index":0,"message":{"role":"assistant","content":"{\\"score\\":8,\\"reason\\":\\"内容正常\\"}"},"finish_reason":"stop"}],
                    "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                    """).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        AgentModelProperties modelProperties = new AgentModelProperties();
        AgentModelProperties.Provider provider = new AgentModelProperties.Provider();
        provider.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        provider.setApiKey("test-key");
        provider.setChatModel("test-chat");
        provider.setTextModerationModel("test-text");
        provider.setImageModerationModel("test-image");
        modelProperties.setDefaultProvider("test");
        modelProperties.setProviders(Map.of("test", provider));
        AgentModelRegistry registry = new AgentModelRegistry(modelProperties);
        ModerationProperties moderationProperties = new ModerationProperties();
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        AiModerationServiceImpl service = new AiModerationServiceImpl(registry, moderationProperties,
                new AhoCorasickSensitiveWordMatcher(moderationProperties, resourceLoader), resourceLoader);
        ModerationRequest request = new ModerationRequest();
        request.setType(ModerationContentType.TEXT);
        request.setContent("正常的游戏攻略分享");

        ModerationResultVO result = service.moderate(request);

        assertEquals(ModerationDecision.PASS, result.getResult());
        assertEquals(8, result.getScore());
        assertEquals("内容正常", result.getReason());
        assertEquals("test", result.getProvider());
        assertEquals("test-text", result.getModel());
        assertEquals(15, result.getTotalTokens());
    }

    /** 验证图片字节通过 Spring AI 多模态消息发送并映射人工审核。 */
    @Test
    void callsVisionModelWithBase64Media() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(requestBody.contains("image_url"));
            assertTrue(requestBody.contains(Base64.getEncoder().encodeToString("image".getBytes(StandardCharsets.UTF_8))));
            byte[] response = ("""
                    {"id":"chatcmpl-image","object":"chat.completion","created":1,"model":"test-image",
                    "choices":[{"index":0,"message":{"role":"assistant","content":"{\\"score\\":5,\\"reason\\":\\"图片文字语境不明\\"}"},"finish_reason":"stop"}],
                    "usage":{"prompt_tokens":20,"completion_tokens":6,"total_tokens":26}}
                    """).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        AgentModelProperties modelProperties = modelProperties(server.getAddress().getPort());
        AgentModelRegistry registry = new AgentModelRegistry(modelProperties);
        ModerationProperties moderationProperties = new ModerationProperties();
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        AiModerationServiceImpl service = new AiModerationServiceImpl(registry, moderationProperties,
                new AhoCorasickSensitiveWordMatcher(moderationProperties, resourceLoader), resourceLoader);
        ModerationRequest request = new ModerationRequest();
        request.setType(ModerationContentType.IMAGE);
        request.setImageBase64(Base64.getEncoder().encodeToString("image".getBytes(StandardCharsets.UTF_8)));
        request.setMimeType("image/png");

        ModerationResultVO result = service.moderate(request);

        assertEquals(ModerationDecision.HUMAN_REVIEW, result.getResult());
        assertEquals(5, result.getScore());
        assertEquals("test-image", result.getModel());
        assertEquals(26, result.getTotalTokens());
    }

    /** 文章正文和多图通过一次多模态请求发送，不产生文字/图片拆分调用。 */
    @Test
    void auditsArticleTextAndAllImagesInOneCall() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(requestBody.contains("文章标题"));
            assertTrue(requestBody.indexOf("image_url") != requestBody.lastIndexOf("image_url"));
            byte[] response = ("""
                    {"id":"chatcmpl-article","object":"chat.completion","created":1,"model":"test-image",
                    "choices":[{"index":0,"message":{"role":"assistant","content":"{\\\"score\\\":8,\\\"reason\\\":\\\"文章和配图正常\\\"}"},"finish_reason":"stop"}],
                    "usage":{"prompt_tokens":30,"completion_tokens":6,"total_tokens":36}}
                    """).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        AgentModelProperties modelProperties = modelProperties(server.getAddress().getPort());
        AgentModelRegistry registry = new AgentModelRegistry(modelProperties);
        ModerationProperties moderationProperties = new ModerationProperties();
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        AiModerationServiceImpl service = new AiModerationServiceImpl(registry, moderationProperties,
                new AhoCorasickSensitiveWordMatcher(moderationProperties, resourceLoader), resourceLoader);
        ModerationRequest request = new ModerationRequest();
        request.setType(ModerationContentType.ARTICLE);
        request.setTitle("文章标题");
        request.setContent("正常的攻略正文");
        ModerationImageRequest first = new ModerationImageRequest();
        first.setImageBase64(Base64.getEncoder().encodeToString("one".getBytes(StandardCharsets.UTF_8)));
        first.setMimeType("image/png");
        ModerationImageRequest second = new ModerationImageRequest();
        second.setImageBase64(Base64.getEncoder().encodeToString("two".getBytes(StandardCharsets.UTF_8)));
        second.setMimeType("image/png");
        request.setImages(List.of(first, second));

        ModerationResultVO result = service.moderate(request);

        assertEquals(1, calls.get());
        assertEquals(ModerationDecision.PASS, result.getResult());
        assertEquals("test-image", result.getModel());
    }

    /** 创建文本与图片测试共用的模型配置。 */
    private AgentModelProperties modelProperties(int port) {
        AgentModelProperties properties = new AgentModelProperties();
        AgentModelProperties.Provider provider = new AgentModelProperties.Provider();
        provider.setBaseUrl("http://127.0.0.1:" + port);
        provider.setApiKey("test-key");
        provider.setChatModel("test-chat");
        provider.setTextModerationModel("test-text");
        provider.setImageModerationModel("test-image");
        properties.setDefaultProvider("test");
        properties.setProviders(Map.of("test", provider));
        return properties;
    }
}
