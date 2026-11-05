package com.fonbulteni.bulletinservice.ingest.evds;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
public class EvdsClientConfig {

    private static final Logger log = LoggerFactory.getLogger(EvdsClientConfig.class);

    @Bean
    public EvdsClient evdsClient(RestClient.Builder builder, EvdsProperties properties, ObjectMapper objectMapper) {
        if (!properties.enabled()) {
            log.warn("EVDS_API_KEY is not set: EVDS client is disabled. Macro data will not be fetched and "
                    + "benchmark / real-return sections of the bulletin will be skipped.");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
                .build();
        return new EvdsClient(restClient, properties, objectMapper);
    }
}
