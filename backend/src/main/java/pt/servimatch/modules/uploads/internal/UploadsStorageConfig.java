package pt.servimatch.modules.uploads.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Cliente e assinador S3, configurados a partir de {@link UploadsProperties}
 * — endpoint compatível S3: MinIO local (dev/teste) ou S3/R2 real
 * (produção), nunca escolhido por {@code if} espalhado no código.
 */
@Configuration
@EnableConfigurationProperties(UploadsProperties.class)
class UploadsStorageConfig {

    @Bean
    AwsCredentialsProvider uploadsCredentialsProvider(UploadsProperties properties) {
        if (properties.accessKey() != null && !properties.accessKey().isBlank()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        }
        // Produção: cadeia de credenciais por omissão (IAM/role), sem segredo no repositório.
        return DefaultCredentialsProvider.create();
    }

    @Bean
    S3Client uploadsS3Client(UploadsProperties properties, AwsCredentialsProvider credentialsProvider) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyleAccess())
                        .build());
        applyEndpointOverride(builder::endpointOverride, properties);
        return builder.build();
    }

    @Bean
    S3Presigner uploadsS3Presigner(UploadsProperties properties, AwsCredentialsProvider credentialsProvider) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyleAccess())
                        .build());
        applyEndpointOverride(builder::endpointOverride, properties);
        return builder.build();
    }

    private static void applyEndpointOverride(java.util.function.Consumer<URI> setter, UploadsProperties properties) {
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            setter.accept(URI.create(properties.endpoint()));
        }
    }
}
