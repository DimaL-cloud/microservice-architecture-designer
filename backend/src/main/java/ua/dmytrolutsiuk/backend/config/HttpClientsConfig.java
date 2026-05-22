package ua.dmytrolutsiuk.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;
import ua.dmytrolutsiuk.backend.client.mermaidvalidator.MermaidValidatorClient;

@Configuration
@ImportHttpServices(group = "mermaid-validator", types = MermaidValidatorClient.class)
public class HttpClientsConfig {
}
