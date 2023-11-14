package com.wiblog.oss.service;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.internal.SkipMd5CheckStrategy;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.StringUtils;
import com.wiblog.oss.bean.OssProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;

import java.util.ArrayList;
import java.util.List;

/**
 * @author panwm
 * @since 2023/8/20 1:33
 */
public class OssTemplate {

    private final OssProperties ossProperties;

    private AmazonS3 amazonS3;

    private PutOperations putOperations;

    private QueryOperations queryOperations;

    private DeleteOperations deleteOperations;

    public OssTemplate(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
        initClient();
        initOps();
    }

    private void initClient() {
        if (StringUtils.isNullOrEmpty(ossProperties.getEndpoint())) {
            throw new IllegalArgumentException("illegal argument oss.endpoint");
        }
        if (StringUtils.isNullOrEmpty(ossProperties.getAccessKey())) {
            throw new IllegalArgumentException("illegal argument oss.access-key");
        }
        if (StringUtils.isNullOrEmpty(ossProperties.getSecretKey())) {
            throw new IllegalArgumentException("illegal argument oss.secret-key");
        }
        // fix: SdkClientException: Unable to verify integrity of data upload. Client calculated content hash
        System.setProperty(SkipMd5CheckStrategy.DISABLE_PUT_OBJECT_MD5_VALIDATION_PROPERTY, "true");
        System.setProperty(SkipMd5CheckStrategy.DISABLE_GET_OBJECT_MD5_VALIDATION_PROPERTY, "true");
        // fix: SdkClientException: Unable to reset stream after calculating AWS4 signature
        System.setProperty(SDKGlobalConfiguration.DEFAULT_S3_STREAM_BUFFER_SIZE, "1073741824");
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.withMaxConnections(ossProperties.getMaxConnections()).withConnectionTimeout(ossProperties.getConnectionTimeout())
                .withClientExecutionTimeout(60000).withRequestTimeout(60000);
        AwsClientBuilder.EndpointConfiguration endpointConfiguration = new AwsClientBuilder.EndpointConfiguration(
                ossProperties.getEndpoint(), null);
        AWSCredentials awsCredentials = new BasicAWSCredentials(ossProperties.getAccessKey(),
                ossProperties.getSecretKey());

        AWSCredentialsProvider awsCredentialsProvider = new AWSStaticCredentialsProvider(awsCredentials);
        this.amazonS3 = AmazonS3ClientBuilder.standard().withEndpointConfiguration(endpointConfiguration)
                .withClientConfiguration(clientConfiguration).withCredentials(awsCredentialsProvider)
                .disableChunkedEncoding().withPathStyleAccessEnabled(true).build();

        if (!amazonS3.doesBucketExistV2(ossProperties.getBucketName())) {
            amazonS3.createBucket((ossProperties.getBucketName()));
        }

        if (ossProperties.isCross()) {
            cross();
        }
    }

    /**
     * 跨域设置
     */
    private void cross() {

        // 创建CORS规则列表
        List<CORSRule> corsRules = new ArrayList<>();
        // 添加允许跨域访问的规则
        CORSRule corsRule = new CORSRule();
        corsRule.setAllowedOrigins("*");
        corsRule.setAllowedMethods(CORSRule.AllowedMethods.GET);
        corsRule.setAllowedHeaders("*");
        corsRules.add(corsRule);

        // 创建BucketCrossOriginConfiguration对象
        BucketCrossOriginConfiguration configuration = new BucketCrossOriginConfiguration(corsRules);
        amazonS3.setBucketCrossOriginConfiguration(ossProperties.getBucketName(), configuration);

    }

    private void initOps() {
        this.putOperations = new PutOperations(this.amazonS3, ossProperties);
        this.queryOperations = new QueryOperations(this.amazonS3, ossProperties);
        this.deleteOperations = new DeleteOperations(this.amazonS3, ossProperties);
    }

    public PutOperations put() {
        return this.putOperations;
    }

    public QueryOperations query() {
        return this.queryOperations;
    }

    public DeleteOperations delete() {
        return this.deleteOperations;
    }

}
