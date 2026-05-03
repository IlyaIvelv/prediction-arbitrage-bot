package com.prearbbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;

@Configuration
public class RestConfig {

    @Value("${proxy.host:}")
    private String proxyHost;

    @Value("${proxy.port:0}")
    private int proxyPort;

    // Твой основной RestTemplate (без прокси)
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // Новый RestTemplate для Kalshi (через прокси)
    @Bean(name = "kalshiRestTemplate")
    public RestTemplate kalshiRestTemplate() {
        if (proxyHost != null && !proxyHost.isEmpty() && proxyPort > 0) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
            factory.setProxy(proxy);

            // Важно: таймауты, чтобы запросы не висели вечно
            factory.setConnectTimeout(5000);
            factory.setReadTimeout(10000);
            return new RestTemplate(factory);
        }
        // Если прокси не задан, возвращаем обычный
        return new RestTemplate();
    }
}
