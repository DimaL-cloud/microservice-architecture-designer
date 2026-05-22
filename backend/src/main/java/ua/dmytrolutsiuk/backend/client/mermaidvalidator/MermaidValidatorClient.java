package ua.dmytrolutsiuk.backend.client.mermaidvalidator;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

public interface MermaidValidatorClient {

    @PostExchange("/validate")
    MermaidValidationResponse validate(@RequestBody MermaidValidationRequest request);
}
