package com.nanobase.specai.document.infrastructure;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "specai.storage.create-buckets", havingValue = "true")
public class MinioBucketInitializer implements ApplicationRunner {
    public static final List<String> BUCKETS = List.of(
        "specai-original", "specai-processed", "specai-thumbnails",
        "specai-reports", "specai-temp");
    private final MinioClient minio;

    public MinioBucketInitializer(MinioClient minio) {
        this.minio = minio;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        for (String bucket : BUCKETS) {
            if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        }
    }
}
