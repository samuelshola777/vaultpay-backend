//package com.vaultpay.utils.appsecurity;
//
//import io.swagger.v3.oas.models.OpenAPI;
//import io.swagger.v3.oas.models.info.Info;
//import io.swagger.v3.oas.models.Components;
//import io.swagger.v3.oas.models.security.SecurityRequirement;
//import io.swagger.v3.oas.models.security.SecurityScheme;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class OpenApiConfig {
//
//    @Bean
//    OpenAPI vaultPayOpenApi() {
//        String schemeName = "bearerAuth";
//        return new OpenAPI()
//                .info(new Info()
//                        .title("VaultPay API")
//                        .description("Digital wallet and payment platform API")
//                        .version("v1"))
//                .addSecurityItem(new SecurityRequirement().addList(schemeName))
//                .components(new Components().addSecuritySchemes(schemeName,
//                        new SecurityScheme()
//                                .name(schemeName)
//                                .type(SecurityScheme.Type.HTTP)
//                                .scheme("bearer")
//                                .bearerFormat("JWT")));
//    }
//}
