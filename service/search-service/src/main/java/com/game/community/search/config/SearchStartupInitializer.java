package com.game.community.search.config;

import com.game.community.search.initIndex.InitElasticsearchIndex;
import com.game.community.search.service.SearchStartupSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.alibaba.cloud.nacos.registry.NacosAutoServiceRegistration;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchStartupInitializer {

    private final InitElasticsearchIndex initElasticsearchIndex;
    private final SearchStartupSyncService searchStartupSyncService;
    private final ObjectProvider<NacosAutoServiceRegistration> nacosAutoServiceRegistrationProvider;
    private final ObjectProvider<NacosDiscoveryProperties> nacosDiscoveryPropertiesProvider;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("应用就绪，开始初始化搜索索引");
        initElasticsearchIndex.initIndex();
        log.info("搜索索引初始化完成，触发启动数据同步");
        boolean ready = searchStartupSyncService.syncOnStartupIfEnabled();
        if (!ready) {
            throw new IllegalStateException("搜索索引初始化/重建失败，拒绝向注册中心发布未就绪实例");
        }
        registerAfterReady();
    }

    private void registerAfterReady() {
        NacosAutoServiceRegistration registration = nacosAutoServiceRegistrationProvider.getIfAvailable();
        if (registration == null) {
            log.info("未发现 Nacos 服务注册器，跳过延迟注册");
            return;
        }
        NacosDiscoveryProperties properties = nacosDiscoveryPropertiesProvider.getIfAvailable();
        if (properties != null) {
            properties.setRegisterEnabled(true);
        }
        registration.start();
        log.info("搜索服务索引已就绪，完成服务注册");
    }
}
