package com.gabon.common.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import lombok.Data;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.mediaconvert.MediaConvertClient;
import software.amazon.awssdk.services.mediaconvert.model.DescribeEndpointsRequest;
import software.amazon.awssdk.services.mediaconvert.model.DescribeEndpointsResponse;

/**
 * AWS MediaConvert 配置类
 * AWS MediaConvert Configuration
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aws.mediaconvert")
public class MediaConvertConfig {

    private String roleArn;
    private String region;
    private String jobTemplateName;
    private String sourcePrefix;
    private String previewPrefix;

    @Lazy
    @Bean
    public MediaConvertClient mediaConvertClient(AwsS3Config awsS3Config) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                awsS3Config.getAccessKey(), awsS3Config.getSecretKey());

        // 先用默认endpoint获取账户专属endpoint
        MediaConvertClient tempClient = MediaConvertClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();

        DescribeEndpointsResponse endpointsResponse = tempClient.describeEndpoints(
                DescribeEndpointsRequest.builder().build());
        String endpointUrl = endpointsResponse.endpoints().get(0).url();
        tempClient.close();

        // 用专属endpoint创建正式client
        return MediaConvertClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .endpointOverride(URI.create(endpointUrl))
                .build();
    }
}
