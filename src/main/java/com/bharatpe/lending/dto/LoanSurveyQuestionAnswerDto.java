package com.bharatpe.lending.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.springframework.util.CollectionUtils;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LoanSurveyQuestionAnswerDto {
  String question;
  String answer;
  Boolean isSelect;
  Map<String, String> metaData;

  public String getMetaData() {
    if(CollectionUtils.isEmpty(metaData)) {
      return null;
    }
    StringBuilder stringBuilder = new StringBuilder();
    for (Map.Entry<String,String> entry : metaData.entrySet()) {
      System.out.println("Key = " + entry.getKey() +
          ", Value = " + entry.getValue());
      stringBuilder.append(entry.getKey()).append(":").append(entry.getValue()).append(",");
    }

    return stringBuilder.toString();
  }

  public String getQuestion() {
    return question;
  }

  public void setQuestion(String question) {
    this.question = question;
  }

  public String getAnswer() {
    return answer;
  }

  public void setAnswer(String answer) {
    this.answer = answer;
  }

  public Boolean isSelect() {
    return isSelect == null ? Boolean.FALSE : isSelect;
  }

  public void setSelect(boolean select) {
    isSelect = select;
  }

  public void setMetaData(Map<String, String> metaData) {
    this.metaData = metaData;
  }
}
