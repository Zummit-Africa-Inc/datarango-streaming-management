package com.datarango.streaming.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.OffsetDateTime;

@Service
public class AzureBlobService {

    private static final Logger logger = LoggerFactory.getLogger(AzureBlobService.class);

    private final BlobServiceClient blobServiceClient;
    private final String containerName;

    public AzureBlobService(BlobServiceClient blobServiceClient,
                           @Value("${azure.storage.container-name}") String containerName) {
        this.blobServiceClient = blobServiceClient;
        this.containerName = containerName;
    }

    public BlobContainerClient getContainerClient() {
        return blobServiceClient.getBlobContainerClient(containerName);
    }

    public BlobClient getBlobClient(String blobName) {
        return getContainerClient().getBlobClient(blobName);
    }

    public boolean blobExists(String blobName) {
        try {
            return getBlobClient(blobName).exists();
        } catch (Exception e) {
            logger.error("Error checking blob existence: {}", blobName, e);
            return false;
        }
    }

    public BlobProperties getBlobProperties(String blobName) {
        return getBlobClient(blobName).getProperties();
    }

    public long getBlobSize(String blobName) {
        return getBlobProperties(blobName).getBlobSize();
    }

    public String getContentType(String blobName) {
        return getBlobProperties(blobName).getContentType();
    }

    public byte[] downloadRange(String blobName, long start, long end) {
        BlobClient blobClient = getBlobClient(blobName);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        long count = end - start + 1;
        BlobRange range = new BlobRange(start, count);

        blobClient.downloadStreamWithResponse(
                outputStream,
                range,
                null,
                null,
                false,
                null,
                null
        );

        return outputStream.toByteArray();
    }

    public void downloadToStream(String blobName, OutputStream outputStream) {
        BlobClient blobClient = getBlobClient(blobName);
        blobClient.downloadStream(outputStream);
    }

    public void downloadRangeToStream(String blobName, OutputStream outputStream, long start, long end) {
        BlobClient blobClient = getBlobClient(blobName);
        long count = end - start + 1;
        BlobRange range = new BlobRange(start, count);

        blobClient.downloadStreamWithResponse(
                outputStream,
                range,
                null,
                null,
                false,
                null,
                null
        );
    }

    public String generateSasUrl(String blobName, int expiryMinutes) {
        BlobClient blobClient = getBlobClient(blobName);

        BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);
        OffsetDateTime expiryTime = OffsetDateTime.now().plusMinutes(expiryMinutes);

        BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(expiryTime, permission);

        String sasToken = blobClient.generateSas(sasValues);
        return blobClient.getBlobUrl() + "?" + sasToken;
    }

    public String uploadBlob(String blobName, InputStream inputStream, long contentLength, String contentType) throws IOException {
        BlobClient blobClient = getBlobClient(blobName);

        blobClient.upload(inputStream, contentLength, true);

        if (contentType != null && !contentType.isEmpty()) {
            blobClient.setHttpHeaders(new com.azure.storage.blob.models.BlobHttpHeaders()
                    .setContentType(contentType));
        }

        logger.info("Uploaded blob: {}, size: {}, contentType: {}", blobName, contentLength, contentType);
        return blobClient.getBlobUrl();
    }
}
