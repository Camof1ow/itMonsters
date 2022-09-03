package com.example.itsquad.controller.response;

import com.example.itsquad.domain.SubComment;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class CommentResponseDto {


    private Long commentId;

    private String nickname;

    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime modifiedAt;

    private String profileImage;

    private List<SubComment> subCommentList;

}