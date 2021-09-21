package com.redhat.labs.lodestar.engagements.mock;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import javax.validation.constraints.NotBlank;

import com.redhat.labs.lodestar.engagements.model.Category;
import com.redhat.labs.lodestar.engagements.model.Engagement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitlabFile {

    @NotBlank private String filePath;
    @NotBlank private String fileName;
    @NotBlank private String ref;
    @NotBlank private String content;
    @NotBlank private String lastCommitId;
    @Builder.Default 
    private String encoding = "base64";
    private Long size;
    private String branch;
    private String authorEmail;
    private String authorName;
    private String commitMessage;
    
    Engagement engagement;
    List<Category> categories;

    public void encode() {
        try {
            this.filePath = URLEncoder.encode(filePath, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException();
        }

        // encode contents
        if (null != content) {
            byte[] encodedContents = Base64.getEncoder().encode(content.getBytes());
            this.content = new String(encodedContents, StandardCharsets.UTF_8);
        }
    }
}
