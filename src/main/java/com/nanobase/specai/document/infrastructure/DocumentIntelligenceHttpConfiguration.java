package com.nanobase.specai.document.infrastructure;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration
public class DocumentIntelligenceHttpConfiguration {
    @Bean
    RestClientCustomizer documentIntelligenceTimeouts(
        @Value("${specai.document-intelligence.http.connect-timeout:PT3S}")
        Duration connectTimeout,
        @Value("${specai.document-intelligence.http.read-timeout:PT30S}")
        Duration readTimeout) {
        return builder -> {
            HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
            JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(readTimeout);
            builder.requestFactory(requestFactory);
        };
    }
}
