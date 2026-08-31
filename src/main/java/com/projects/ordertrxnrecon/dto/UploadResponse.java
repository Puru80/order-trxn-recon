package com.projects.ordertrxnrecon.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadResponse {

    private String filename;
    private String type;
    private int totalRows;
    private int validRows;
    private int invalidRows;
    private String message;
}
