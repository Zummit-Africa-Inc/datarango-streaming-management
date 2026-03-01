package com.datarango.streaming.service;

import com.datarango.streaming.dto.VideoStreamInfoDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

@Service
public class VideoStreamService {

    private static final Logger logger = LoggerFactory.getLogger(VideoStreamService.class);
    private static final long CHUNK_SIZE = 1024 * 1024; // 1MB chunks
    private static final int SAS_TOKEN_EXPIRY_MINUTES = 60;

    private final AzureBlobService azureBlobService;

    public VideoStreamService(AzureBlobService azureBlobService) {
        this.azureBlobService = azureBlobService;
    }

    public VideoStreamInfoDto getVideoInfo(String videoId) {
        String blobName = resolveBlobName(videoId);

        if (!azureBlobService.blobExists(blobName)) {
            throw new VideoNotFoundException("Video not found: " + videoId);
        }

        long fileSize = azureBlobService.getBlobSize(blobName);
        String contentType = azureBlobService.getContentType(blobName);

        return VideoStreamInfoDto.builder()
                .videoId(videoId)
                .fileName(blobName)
                .contentType(contentType != null ? contentType : "video/mp4")
                .fileSize(fileSize)
                .streamUrl("/api/videos/" + videoId + "/stream")
                .build();
    }

    public String getVideoSasUrl(String videoId) {
        String blobName = resolveBlobName(videoId);

        if (!azureBlobService.blobExists(blobName)) {
            throw new VideoNotFoundException("Video not found: " + videoId);
        }

        return azureBlobService.generateSasUrl(blobName, SAS_TOKEN_EXPIRY_MINUTES);
    }

    public ResponseEntity<Resource> streamVideo(String videoId, String rangeHeader) {
        String blobName = resolveBlobName(videoId);

        if (!azureBlobService.blobExists(blobName)) {
            throw new VideoNotFoundException("Video not found: " + videoId);
        }

        long fileSize = azureBlobService.getBlobSize(blobName);
        String contentType = azureBlobService.getContentType(blobName);

        if (contentType == null || contentType.isEmpty()) {
            contentType = "video/mp4";
        }

        // If no range header, return the full video (for small files or direct download)
        if (rangeHeader == null || rangeHeader.isEmpty()) {
            return streamFullVideo(blobName, fileSize, contentType);
        }

        // Parse range header: "bytes=start-end"
        return streamVideoRange(blobName, rangeHeader, fileSize, contentType);
    }

    private ResponseEntity<Resource> streamFullVideo(String blobName, long fileSize, String contentType) {
        logger.info("Streaming full video: {}, size: {}", blobName, fileSize);

        byte[] videoData = azureBlobService.downloadRange(blobName, 0, fileSize - 1);
        InputStreamResource resource = new InputStreamResource(new ByteArrayInputStream(videoData));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(fileSize);
        headers.add("Accept-Ranges", "bytes");

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    private ResponseEntity<Resource> streamVideoRange(String blobName, String rangeHeader, long fileSize, String contentType) {
        try {
            // Parse range: "bytes=start-" or "bytes=start-end"
            String range = rangeHeader.replace("bytes=", "");
            String[] ranges = range.split("-");

            long start = Long.parseLong(ranges[0]);
            long end;

            if (ranges.length > 1 && !ranges[1].isEmpty()) {
                end = Long.parseLong(ranges[1]);
            } else {
                // If no end specified, use chunk size or remaining file
                end = Math.min(start + CHUNK_SIZE - 1, fileSize - 1);
            }

            // Validate range
            if (start >= fileSize) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header("Content-Range", "bytes */" + fileSize)
                        .build();
            }

            // Ensure end doesn't exceed file size
            end = Math.min(end, fileSize - 1);
            long contentLength = end - start + 1;

            logger.info("Streaming video range: {} bytes={}-{}/{}", blobName, start, end, fileSize);

            byte[] videoData = azureBlobService.downloadRange(blobName, start, end);
            InputStreamResource resource = new InputStreamResource(new ByteArrayInputStream(videoData));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentLength(contentLength);
            headers.add("Accept-Ranges", "bytes");
            headers.add("Content-Range", String.format("bytes %d-%d/%d", start, end, fileSize));

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .headers(headers)
                    .body(resource);

        } catch (NumberFormatException e) {
            logger.error("Invalid range header: {}", rangeHeader, e);
            return ResponseEntity.badRequest().build();
        }
    }

    private String resolveBlobName(String videoId) {
        // If videoId already contains path separator or extension, use as-is
        if (videoId.contains("/") || videoId.contains(".")) {
            return videoId;
        }
        // Otherwise, assume it's in a videos folder with mp4 extension
        return "videos/" + videoId + ".mp4";
    }

    public static class VideoNotFoundException extends RuntimeException {
        public VideoNotFoundException(String message) {
            super(message);
        }
    }
}
