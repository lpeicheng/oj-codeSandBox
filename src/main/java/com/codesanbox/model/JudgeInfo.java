package com.codesanbox.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


//判题信息
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JudgeInfo {
    private String message;
    private Long time;
    private Long memory;
}
