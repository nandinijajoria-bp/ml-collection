package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageDTO {
    private Long imageId;
    private String imageUrl;
    private String imageDescription;

    public ImageDTO(Long imageId, String imageUrl, String imageDescription) {
        this.imageId = imageId;
        this.imageUrl = imageUrl;
        this.imageDescription = imageDescription;
    }

    public ImageDTO() {
    }

    public Long getImageId() {
        return imageId;
    }

    public void setImageId(Long imageId) {
        this.imageId = imageId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageDescription() {
        return imageDescription;
    }

    public void setImageDescription(String imageDescription) {
        this.imageDescription = imageDescription;
    }

    @Override
    public String toString() {
        return "ImageDTO{" +
                "imageId=" + imageId +
                ", imageUrl='" + imageUrl + '\'' +
                ", imageDescription='" + imageDescription + '\'' +
                '}';
    }
}
