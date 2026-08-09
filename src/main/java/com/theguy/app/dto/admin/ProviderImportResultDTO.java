package com.theguy.app.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProviderImportResultDTO {
    private int totalRows;
    private int imported;
    private int skippedDuplicatePhone;
    private List<String> invalidRows;
}
