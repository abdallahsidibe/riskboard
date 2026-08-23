package com.riskboard.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI riskBoardOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RiskBoard API")
                        .description("API de gestion des limites de risque des contreparties. " +
                                "Permet de consulter les limites, importer des données CSV et gérer les dérogations.")
                        .version("1.0.0"));
    }
}
