package pt.servimatch.modules.uploads;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

import java.net.URI;
import java.time.Duration;

/**
 * Contentor MinIO partilhado (padrão "singleton container", ver
 * {@code pt.servimatch.testsupport.SharedPostgis}) para os testes de
 * integração do módulo {@code uploads} — mesma imagem fixada em
 * {@code infra/docker-compose.yml} (ambiente local), para que "passa nos
 * testes" e "passa contra o MinIO local" sejam a mesma garantia.
 *
 * <p>Deliberadamente hermético (Testcontainers, não o {@code docker-compose}
 * do repositório): {@code mvn verify} tem de ser reproduzível em CI sem
 * depender de infraestrutura arrancada à mão.
 */
final class SharedMinio {

    static final String ACCESS_KEY = "servimatch-test";
    static final String SECRET_KEY = "servimatch-test-secret";
    static final String BUCKET = "servimatch-uploads-test";
    static final String REGION = "eu-west-1";

    private static final GenericContainer<?> CONTAINER = new GenericContainer<>(
            DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z"))
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).withStartupTimeout(Duration.ofSeconds(60)));

    private static volatile boolean bucketReady = false;

    private SharedMinio() {
    }

    static synchronized String endpoint() {
        ensureStarted();
        return "http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(9000);
    }

    private static void ensureStarted() {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        if (!bucketReady) {
            createBucketIfAbsent();
            bucketReady = true;
        }
    }

    private static void createBucketIfAbsent() {
        String endpoint = "http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(9000);
        try (S3Client client = S3Client.builder()
                .region(Region.of(REGION))
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            try {
                client.createBucket(b -> b.bucket(BUCKET));
            } catch (BucketAlreadyOwnedByYouException ignored) {
                // idempotente: outra JVM/teste já criou o bucket.
            }
        }
    }
}
