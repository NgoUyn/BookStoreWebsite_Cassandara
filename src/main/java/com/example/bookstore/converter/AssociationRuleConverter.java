package com.example.bookstore.converter;

import com.example.bookstore.model.AssociationRule;
import com.example.bookstore.dto.AssociationRuleDTO;
import org.springframework.stereotype.Component;

/**
 * Converter between AssociationRule entity and DTO
 * Handles the mapping logic for API responses
 */
@Component
public class AssociationRuleConverter {

    /**
     * Convert AssociationRule entity to DTO
     * Extracts only necessary fields and avoids lazy-loading proxies
     */
    public AssociationRuleDTO toDTO(AssociationRule entity) {
        if (entity == null) {
            return null;
        }

        AssociationRuleDTO dto = new AssociationRuleDTO();
        dto.setRuleId(entity.getRuleId());
        dto.setBookIdA(entity.getBookA() != null ? entity.getBookA().getId() : null);
        dto.setBookIdB(entity.getBookB() != null ? entity.getBookB().getId() : null);
        dto.setSupport(entity.getSupport());
        dto.setConfidence(entity.getConfidence());
        dto.setLift(entity.getLift());
        dto.setUpdatedAt(entity.getUpdatedAt());

        return dto;
    }

    /**
     * Batch convert list of entities to DTOs
     */
    public java.util.List<AssociationRuleDTO> toDTOList(java.util.List<AssociationRule> entities) {
        if (entities == null || entities.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return entities.stream()
                .map(this::toDTO)
                .toList();
    }

}
