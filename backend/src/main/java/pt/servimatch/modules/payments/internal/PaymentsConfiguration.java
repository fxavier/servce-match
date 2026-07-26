package pt.servimatch.modules.payments.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import pt.servimatch.modules.payments.GatewayCode;
import pt.servimatch.modules.payments.internal.eupago.EupagoIfthenPayPaymentGateway;

@Configuration
public class PaymentsConfiguration {

    @Bean
    public EupagoIfthenPayPaymentGateway eupagoPaymentGateway(RestClient.Builder restClientBuilder,
                                                                PaymentsProperties properties,
                                                                ObjectMapper objectMapper) {
        RestClient restClient = restClientBuilder.baseUrl(properties.eupagoBaseUrl()).build();
        return new EupagoIfthenPayPaymentGateway(
                GatewayCode.EUPAGO, restClient, properties.eupagoApiKey(), properties.eupagoWebhookSecret(), objectMapper);
    }

    @Bean
    public EupagoIfthenPayPaymentGateway ifthenpayPaymentGateway(RestClient.Builder restClientBuilder,
                                                                   PaymentsProperties properties,
                                                                   ObjectMapper objectMapper) {
        RestClient restClient = restClientBuilder.baseUrl(properties.ifthenpayBaseUrl()).build();
        return new EupagoIfthenPayPaymentGateway(
                GatewayCode.IFTHENPAY, restClient, properties.ifthenpayApiKey(), properties.ifthenpayWebhookSecret(), objectMapper);
    }
}
