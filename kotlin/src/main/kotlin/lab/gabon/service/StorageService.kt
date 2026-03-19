package lab.gabon.service

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.deleteObject
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.presigners.presignPutObject
import aws.smithy.kotlin.runtime.net.url.Url
import lab.gabon.config.S3Config
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

class StorageService(
    private val config: S3Config,
) {
    private val logger = LoggerFactory.getLogger(StorageService::class.java)

    private val s3Client: S3Client? =
        if (config.endpoint.isBlank()) {
            logger.info("S3 endpoint is blank — running in stub mode")
            null
        } else {
            S3Client {
                endpointUrl = Url.parse(config.endpoint)
                region = config.region
                forcePathStyle = true
                credentialsProvider =
                    StaticCredentialsProvider {
                        accessKeyId = config.accessKey
                        secretAccessKey = config.secretKey
                    }
            }
        }

    suspend fun presignUpload(
        bucket: String,
        key: String,
        contentType: String,
        expireMinutes: Int = 15,
    ): String {
        val client =
            s3Client
                ?: return "https://stub.local/$bucket/$key?upload=true"

        val request =
            PutObjectRequest {
                this.bucket = bucket
                this.key = key
                this.contentType = contentType
            }
        val presigned = client.presignPutObject(request, expireMinutes.minutes)
        return presigned.url.toString()
    }

    fun buildPublicUrl(
        bucket: String,
        key: String,
    ): String {
        if (s3Client == null) return "https://stub.local/$bucket/$key"
        return "${config.endpoint.trimEnd('/')}/$bucket/$key"
    }

    suspend fun delete(
        bucket: String,
        key: String,
    ) {
        val client = s3Client ?: return
        client.deleteObject {
            this.bucket = bucket
            this.key = key
        }
    }

    fun generateKey(
        prefix: String,
        customerId: Long,
        fileName: String,
    ): String {
        val ext = fileName.substringAfterLast('.', "bin")
        return "$prefix/$customerId/${UUID.randomUUID()}.$ext"
    }

    fun close() {
        s3Client?.close()
        logger.info("S3 client closed")
    }
}
