package com.theguy.app.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class CompleteJobDTO {
    @Size(max = 2000, message = "Completion notes cannot exceed 2000 characters")
    private String completionNotes;
    @Size(max = 10, message = "Maximum 10 completion photos allowed")
    private List<String> completionPhotos;
    private Double latitude;
    private Double longitude;
}