package com.county_cars.vroom.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    /**
     * Injected from application.properties.
     * Locally resolves to http://localhost:{SERVER_PORT}.
     * In production set env var SWAGGER_SERVER_URL=https://api.yourdomain.com
     */
    @Value("${app.swagger.server-url}")
    private String serverUrl;

    @Bean
    public OpenAPI openAPI() {
        Server server = new Server()
                .url(serverUrl)
                .description(serverUrl.startsWith("https") ? "UAT" : "Local");

        return new OpenAPI()
                .addServersItem(server)
                .info(new Info()
                        .title("VROOM API")
                        .description("Production-ready authentication & user profile module")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("County Cars")
                                .email("dev@county-cars.com"))
                        .license(new License().name("Private")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
