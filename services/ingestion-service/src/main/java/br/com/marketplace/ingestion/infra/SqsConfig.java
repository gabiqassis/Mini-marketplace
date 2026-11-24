package br.com.marketplace.ingestion.infra;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

/**
 * Cliente SQS oficial da AWS. Localmente aponta para o ElasticMQ (API compativel com SQS) via
 * endpoint-override; na AWS basta remover o endpoint e usar as credenciais do ambiente (IAM role).
 */
@Configuration
@ConditionalOnProperty(name = "marketplace.sqs.enabled", havingValue = "true", matchIfMissing = true)
class SqsConfig {

    @Bean(destroyMethod = "close")
    SqsClient sqsClient(
            @Value("${marketplace.aws.region:us-east-1}") String region,
            @Value("${marketplace.aws.endpoint:}") String endpoint,
            @Value("${marketplace.aws.access-key:local}") String accessKey,
            @Value("${marketplace.aws.secret-key:local}") String secretKey) {
        SqsClientBuilder builder = SqsClient.builder()
                .region(Region.of(region))
                .httpClient(UrlConnectionHttpClient.builder().build());
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        }
        return builder.build();
    }
}
