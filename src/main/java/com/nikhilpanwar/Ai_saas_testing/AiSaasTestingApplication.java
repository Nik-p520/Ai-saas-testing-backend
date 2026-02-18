package com.nikhilpanwar.Ai_saas_testing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class AiSaasTestingApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiSaasTestingApplication.class, args);
	}

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);   // 5 seconds
        factory.setReadTimeout(180000);    // 3 minutes (Fixes the stuck test)
        return new RestTemplate(factory);
    }
}
