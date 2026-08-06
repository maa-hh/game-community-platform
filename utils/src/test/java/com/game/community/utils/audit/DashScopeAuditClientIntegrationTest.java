package com.game.community.utils.audit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.TestPropertySource;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
@SpringBootTest(classes = DashScopeAuditClientIntegrationTest.TestApp.class)
@TestPropertySource(properties = {
        "audit.mode=llm",
        "spring.ai.dashscope.api-key=${DASHSCOPE_API_KEY}",
        "spring.ai.dashscope.chat.options.model=qwen-plus",
        "audit.dashscope.text-model=qwen-plus",
        "audit.dashscope.image-model=qwen3-vl-plus",
        "audit.dashscope.fail-open-on-unavailable=false",
        "spring.main.web-application-type=none",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration,org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration,org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration,com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAgentAutoConfiguration"
})
class DashScopeAuditClientIntegrationTest {

    @Autowired
    private AuditClient auditClient;

    @Test
    void shouldAuditTextThroughDashScope() {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "DASHSCOPE_API_KEY 未设置，跳过联调测试");

        AuditResult result = auditClient.auditText("这是一段正常的游戏社区自我介绍，今天心情很好，准备分享一下攻略。");

        assertNotNull(result);
        assertNotNull(result.getScore());
        assertFalse("内容审核服务暂不可用".equals(result.getReason()), "文本审核未真正打通 DashScope");
        System.out.printf("TEXT_AUDIT score=%s pass=%s reason=%s durationMs=%s totalTokens=%s%n",
                result.getScore(), result.isPass(), result.getReason(), result.getDurationMs(), result.getTotalTokens());
    }

    @Test
    void shouldAuditImageThroughDashScope() throws Exception {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "DASHSCOPE_API_KEY 未设置，跳过联调测试");

        byte[] imageBytes = buildSampleImage();
        AuditResult result = auditClient.auditImage(imageBytes, "image/png");

        assertNotNull(result);
        assertNotNull(result.getScore());
        assertFalse("图片审核服务暂不可用".equals(result.getReason()), "图片审核未真正打通 DashScope");
        System.out.printf("IMAGE_AUDIT score=%s pass=%s reason=%s durationMs=%s totalTokens=%s%n",
                result.getScore(), result.isPass(), result.getReason(), result.getDurationMs(), result.getTotalTokens());
    }

    @Test
    void shouldAuditImageUrlThroughDashScope() {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "DASHSCOPE_API_KEY 未设置，跳过联调测试");

        AuditResult result = auditClient.auditImageUrl("https://raw.githubusercontent.com/github/explore/main/topics/java/java.png");

        assertNotNull(result);
        assertNotNull(result.getScore());
        assertFalse("图片审核服务暂不可用".equals(result.getReason()), "图片 URL 审核未真正打通 DashScope");
        System.out.printf("IMAGE_URL_AUDIT score=%s pass=%s reason=%s durationMs=%s totalTokens=%s%n",
                result.getScore(), result.isPass(), result.getReason(), result.getDurationMs(), result.getTotalTokens());
    }

    private byte[] buildSampleImage() throws Exception {
        BufferedImage image = new BufferedImage(320, 320, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(245, 247, 250));
            graphics.fillRect(0, 0, 320, 320);
            graphics.setColor(new Color(47, 111, 237));
            graphics.fillRoundRect(40, 40, 240, 240, 36, 36);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font("SansSerif", Font.BOLD, 36));
            graphics.drawString("GC", 118, 175);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = {DashScopeAuditClient.class})
    static class TestApp {
    }
}
