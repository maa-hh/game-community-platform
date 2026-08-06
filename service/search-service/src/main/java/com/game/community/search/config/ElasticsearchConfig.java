package com.game.community.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.Arrays;

@Configuration
public class ElasticsearchConfig {

    @Value("${spring.elasticsearch.uris}")
    private String elasticsearchUri;

    @Value("${search.elasticsearch.connect-timeout-ms:1000}")
    private int connectTimeoutMs;

    @Value("${search.elasticsearch.socket-timeout-ms:3000}")
    private int socketTimeoutMs;

    @Value("${search.elasticsearch.connection-request-timeout-ms:1000}")
    private int connectionRequestTimeoutMs;

    @Value("${search.elasticsearch.max-conn-total:100}")
    private int maxConnTotal;

    @Value("${search.elasticsearch.max-conn-per-route:50}")
    private int maxConnPerRoute;

    @Value("${search.elasticsearch.username:}")
    private String username;

    @Value("${search.elasticsearch.password:}")
    private String password;

    @Bean
    public RestClient restClient() {
        HttpHost[] hosts = Arrays.stream(elasticsearchUri.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(this::toHttpHost)
                .toArray(HttpHost[]::new);
        if (hosts.length == 0) {
            throw new IllegalArgumentException("spring.elasticsearch.uris 不能为空");
        }
        return RestClient.builder(hosts)
                .setRequestConfigCallback(builder -> builder
                        .setConnectTimeout(connectTimeoutMs)
                        .setSocketTimeout(socketTimeoutMs)
                        .setConnectionRequestTimeout(connectionRequestTimeoutMs))
                .setHttpClientConfigCallback(builder -> {
                    builder.setMaxConnTotal(maxConnTotal).setMaxConnPerRoute(maxConnPerRoute);
                    if (!username.isBlank()) {
                        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                        credentialsProvider.setCredentials(AuthScope.ANY,
                                new UsernamePasswordCredentials(username, password));
                        builder.setDefaultCredentialsProvider(credentialsProvider);
                    }
                    return builder;
                })
                .build();
    }

    private HttpHost toHttpHost(String value) {
        URI uri = URI.create(value);
        int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 9200);
        return new HttpHost(uri.getHost(), port, uri.getScheme());
    }

    @Bean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        return new RestClientTransport(restClient, new JacksonJsonpMapper(mapper));
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
