package br.com.marketplace.invoice.infra;

import br.com.marketplace.invoice.domain.InvoiceIssuer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.LambdaClientBuilder;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

/**
 * Chama a Lambda nfe-emitter pela API oficial (AWS SDK v2, Invoke).
 *
 * <p>Localmente o endpoint aponta para o container public.ecr.aws/lambda/java:21, que traz o
 * Runtime Interface Emulator da propria AWS: o caminho HTTP e o mesmo da API Lambda
 * (/2015-03-31/functions/function/invocations). Na AWS basta tirar o endpoint e usar o nome real.
 *
 * <p>Bulkhead: no maximo N invocacoes simultaneas; SEFAZ lenta nao segura todas as threads. Localmente
 * N=1, porque o emulador (RIE) atende uma invocacao por vez; na AWS, N acompanha a concorrencia
 * reservada da funcao (protege a SEFAZ de rajadas).
 */
@Component
@ConditionalOnProperty(name = "marketplace.invoice.issuer", havingValue = "lambda", matchIfMissing = true)
class LambdaInvoiceIssuer implements InvoiceIssuer, DisposableBean {

    private final LambdaClient lambda;
    private final ObjectMapper mapper;
    private final String functionName;
    private final Semaphore bulkhead;
    private final Duration timeout;

    LambdaInvoiceIssuer(
            ObjectMapper mapper,
            @Value("${marketplace.aws.region:us-east-1}") String region,
            @Value("${marketplace.invoice.lambda.endpoint:}") String endpoint,
            @Value("${marketplace.invoice.lambda.function:function}") String functionName,
            @Value("${marketplace.invoice.lambda.timeout:PT10S}") Duration timeout,
            @Value("${marketplace.invoice.lambda.max-concurrent:10}") int maxConcurrent) {
        LambdaClientBuilder builder = LambdaClient.builder()
                .region(Region.of(region))
                .httpClient(UrlConnectionHttpClient.builder().socketTimeout(timeout).connectionTimeout(Duration.ofSeconds(2)).build())
                .overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(timeout).build());
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        }
        this.lambda = builder.build();
        this.mapper = mapper;
        this.functionName = functionName;
        this.bulkhead = new Semaphore(maxConcurrent);
        this.timeout = timeout;
    }

    @Override
    public Result issue(Request r) {
        boolean acquired = false;
        try {
            acquired = bulkhead.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new TechnicalFailure("muitas emissoes simultaneas", null);
            }
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("orderId", r.orderId().toString());
            input.put("sellerId", r.sellerId());
            input.put("sellerCnpj", r.sellerCnpj());
            input.put("series", r.series());
            input.put("number", r.number());
            input.put("amount", r.amount().toPlainString());
            input.put("issuedAt", r.issuedAt().toString());

            SdkBytes payload = SdkBytes.fromByteArray(mapper.writeValueAsBytes(input));
            InvokeResponse response = lambda.invoke(b -> b.functionName(functionName).payload(payload));
            JsonNode out = mapper.readTree(response.payload().asByteArray());
            if (response.functionError() != null || out.has("errorMessage")) {
                throw new TechnicalFailure("Lambda falhou: " + out.path("errorMessage").asText(), null);
            }
            return new Result("AUTHORIZED".equals(out.path("status").asText()), text(out, "accessKey"), text(out, "protocol"),
                    text(out, "rejectionCode"), text(out, "rejectionReason"));
        } catch (TechnicalFailure e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TechnicalFailure("interrompido", e);
        } catch (Exception e) {
            throw new TechnicalFailure("Lambda indisponivel: " + e.getMessage(), e);
        } finally {
            if (acquired) {
                bulkhead.release();
            }
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    @Override
    public void destroy() {
        lambda.close();
    }
}
