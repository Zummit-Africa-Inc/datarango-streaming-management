package com.datarango.streaming.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoStreamInfoDto {
    private String videoId;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String streamUrl;
    private Integer durationSeconds;
}
