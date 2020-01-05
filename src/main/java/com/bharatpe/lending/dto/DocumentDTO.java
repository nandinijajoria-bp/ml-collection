package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DocumentDTO {
	
	@JsonProperty(value = "id")
	private Long id;
	
	@JsonProperty(value = "proof_type")
	private String proofType;
	
	@JsonProperty(value = "single_page_document")
	private Boolean singlePageDocument;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getProofType() {
		return proofType;
	}

	public void setProofType(String proofType) {
		this.proofType = proofType;
	}

	public Boolean getSinglePageDocument() {
		return singlePageDocument;
	}

	public void setSinglePageDocument(Boolean singlePageDocument) {
		this.singlePageDocument = singlePageDocument;
	}

	@Override
	public String toString() {
		return "DocumentDTO [id=" + id + 
				", proofType=" + proofType + 
				", singlePageDocument=" + singlePageDocument
				+ "]";
	}

}
