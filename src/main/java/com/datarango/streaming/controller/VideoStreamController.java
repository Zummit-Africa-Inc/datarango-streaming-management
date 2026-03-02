package com.datarango.streaming.controller;

import com.datarango.streaming.dto.ApiResponse;
import com.datarango.streaming.dto.VideoStreamInfoDto;
import com.datarango.streaming.service.VideoStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/videos")
@Tag(name = "Video Streaming", description = "APIs for streaming course videos")
public class VideoStreamController {

    private static final Logger logger = LoggerFactory.getLogger(VideoStreamController.class);

    private final VideoStreamService videoStreamService;

    public VideoStreamController(VideoStreamService videoStreamService) {
        this.videoStreamService = videoStreamService;
    }

    @GetMapping("/{videoId}/info")
    @Operation(summary = "Get video information", description = "Retrieve metadata about a video")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Video info retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Video not found")
    })
    public ResponseEntity<ApiResponse<VideoStreamInfoDto>> getVideoInfo(
            @Parameter(description = "Video ID or blob name") @PathVariable String videoId) {
        try {
            VideoStreamInfoDto videoInfo = videoStreamService.getVideoInfo(videoId);
            return ResponseEntity.ok(ApiResponse.success(videoInfo));
        } catch (VideoStreamService.VideoNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error getting video info: {}", videoId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get video info"));
        }
    }

    @GetMapping("/{videoId}/stream")
    @Operation(summary = "Stream video", description = "Stream video content with support for range requests (seeking)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Full video returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "206", description = "Partial content returned (range request)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Video not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "416", description = "Range not satisfiable")
    })
    public ResponseEntity<Resource> streamVideo(
            @Parameter(description = "Video ID or blob name") @PathVariable String videoId,
            @Parameter(description = "Range header for partial content requests")
            @RequestHeader(value = "Range", required = false) String rangeHeader) {
        try {
            return videoStreamService.streamVideo(videoId, rangeHeader);
        } catch (VideoStreamService.VideoNotFoundException e) {
            logger.warn("Video not found: {}", videoId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error streaming video: {}", videoId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{videoId}/url")
    @Operation(summary = "Get signed URL", description = "Get a time-limited signed URL for direct video access")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Signed URL generated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Video not found")
    })
    public ResponseEntity<ApiResponse<Map<String, String>>> getVideoSignedUrl(
            @Parameter(description = "Video ID or blob name") @PathVariable String videoId) {
        try {
            String signedUrl = videoStreamService.getVideoSasUrl(videoId);
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "videoId", videoId,
                    "url", signedUrl,
                    "expiresInMinutes", "60"
            )));
        } catch (VideoStreamService.VideoNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error generating signed URL: {}", videoId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to generate signed URL"));
        }
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload video", description = "Upload a video file to Azure Blob Storage")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Video uploaded successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file or empty file"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Upload failed")
    })
    public ResponseEntity<ApiResponse<VideoStreamInfoDto>> uploadVideo(
            @Parameter(description = "Video file to upload") @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("File is empty"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("File must be a video"));
        }

        try {
            VideoStreamInfoDto videoInfo = videoStreamService.uploadVideo(file);
            logger.info("Video uploaded: {}", videoInfo.getVideoId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(videoInfo));
        } catch (Exception e) {
            logger.error("Error uploading video: {}", file.getOriginalFilename(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to upload video"));
        }
    }
}
