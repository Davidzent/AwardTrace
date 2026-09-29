package com.zntsns.awardtrace.shared;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(S3Config.S3Properties.class)
public class S3Config {

    /**
     * @param bucket the raw bucket: source files under {@code raw/}, database dumps under {@code backups/}
     * @param endpoint set only to reach an S3 stand-in such as S3Mock; left unset, the client uses Amazon S3 with
     *     the default AWS credentials chain
     */
    @ConfigurationProperties("awardtrace.s3")
    public record S3Properties(String bucket, String region, URI endpoint) {
    }

    @Bean
    S3Client s3Client(S3Properties properties) {
        var builder = S3Client.builder().region(Region.of(properties.region()));
        if (properties.endpoint() != null) {
            // S3Mock serves path-style URLs only and accepts any credentials.
            builder.endpointOverride(properties.endpoint())
                    .forcePathStyle(true)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
        }
        return builder.build();
    }
}
