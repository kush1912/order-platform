package com.orderplatform.notification.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationProviderProperties.class)
public class NotificationClientConfiguration {

    @Bean
    @Qualifier("smsRestClient")
    RestClient smsRestClient(NotificationProviderProperties properties) {
        return restClient(properties.sms(), properties.httpClient());
    }

    @Bean
    @Qualifier("whatsappRestClient")
    RestClient whatsappRestClient(NotificationProviderProperties properties) {
        return restClient(properties.whatsapp(), properties.httpClient());
    }

    private RestClient restClient(
            NotificationProviderProperties.HttpProvider provider,
            NotificationProviderProperties.HttpClient clientProperties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(clientProperties.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(clientProperties.readTimeout());

        return RestClient.builder()
                .baseUrl(provider.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
