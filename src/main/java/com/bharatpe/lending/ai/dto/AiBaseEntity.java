package com.bharatpe.lending.ai.dto;

import com.bharatpe.common.entities.BaseEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiBaseEntity {
    private Long id;
    private Date createdAt;
    private Date updatedAt;
    public AiBaseEntity(BaseEntity baseEntity){
        if (baseEntity != null) {
            this.id = baseEntity.getId();
            this.createdAt = baseEntity.getCreatedAt();
            this.updatedAt = baseEntity.getUpdatedAt();
        }
    }
}
