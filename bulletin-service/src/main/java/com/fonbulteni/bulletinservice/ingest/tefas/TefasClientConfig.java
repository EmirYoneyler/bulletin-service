package com.fonbulteni.bulletinservice.ingest.tefas;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
public class TefasClientConfig {

    @Bean
    public TefasClient tefasClient(RestClient.Builder builder, TefasProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient restClient = builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        // Thread.sleep(Duration) is Java 19+; this project targets 17.
        TefasClient.Sleeper sleeper = d -> Thread.sleep(d.toMillis(), d.toNanosPart() % 1_000_000);
        return new TefasClient(restClient, properties, sleeper, System::nanoTime);
    }
}
