package com.theguy.app.dto;

import lombok.Data;
import java.util.List;

@Data
public class CompleteJobDTO {
    private String completionNotes;
    private List<String> completionPhotos;
    private Double latitude;
    private Double longitude;
}