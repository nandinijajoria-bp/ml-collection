package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CrifResponseDTO {

    private boolean success = true;
    private String message;
    private String buttonBehaviour;
    private String question;
    private List<String> optionsList;

    public CrifResponseDTO(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public CrifResponseDTO(String buttonBehaviour, String question, List<String> optionsList) {
        this.buttonBehaviour = buttonBehaviour;
        this.question = question;
        this.optionsList = optionsList;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getButtonBehaviour() {
        return buttonBehaviour;
    }

    public void setButtonBehaviour(String buttonBehaviour) {
        this.buttonBehaviour = buttonBehaviour;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<String> getOptionsList() {
        return optionsList;
    }

    public void setOptionsList(List<String> optionsList) {
        this.optionsList = optionsList;
    }

    @Override
    public String toString() {
        return "CrifResponseDTO{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", buttonBehaviour='" + buttonBehaviour + '\'' +
                ", question='" + question + '\'' +
                ", optionsList='" + optionsList + '\'' +
                '}';
    }
}
