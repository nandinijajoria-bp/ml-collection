package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class UploadImageDTO {

    private Long applicationId;
    
    
    public String getModule() {
		return module;
	}

	public void setModule(String module) {
		this.module = module;
	}

	private String module;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private SelectedLoanDTO selectedLoan;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ImageData imageData;

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public SelectedLoanDTO getSelectedLoan() {
        return selectedLoan;
    }

    public void setSelectedLoan(SelectedLoanDTO selectedLoan) {
        this.selectedLoan = selectedLoan;
    }

    public ImageData getImageData() {
        return imageData;
    }

    public void setImageData(ImageData imageData) {
        this.imageData = imageData;
    }

    @Override
	public String toString() {
		return "UploadImageDTO [applicationId=" + applicationId + ", module=" + module + ", selectedLoan="
				+ selectedLoan + ", imageData=" + imageData + "]";
	}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class ImageData {

        private String imageName;

        private String imageUrl;

        private String imageDescription;

        private Long imageId;

        public String getImageName() {
            return imageName;
        }

        public void setImageName(String imageName) {
            this.imageName = imageName;
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

        public Long getImageId() {
            return imageId;
        }

        public void setImageId(Long imageId) {
            this.imageId = imageId;
        }

        @Override
        public String toString() {
            return "ImageData{" +
                    "imageName='" + imageName + '\'' +
                    ", imageUrl='" + imageUrl + '\'' +
                    ", imageDescription='" + imageDescription + '\'' +
                    ", imageId=" + imageId +
                    '}';
        }
    }
}