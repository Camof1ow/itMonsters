package com.example.itsquad.controller;


import com.example.itsquad.controller.request.CommentRequestDto;
import com.example.itsquad.controller.request.SubCommentRequestDto;
import com.example.itsquad.controller.response.SubCommentResponseDto;
import com.example.itsquad.security.UserDetailsImpl;
import com.example.itsquad.service.SubCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SubCommentController {

    public final SubCommentService subCommentService;

    @PostMapping("/api/quests/{questId}/comments/{commentId}/subComments")
    public ResponseEntity<SubCommentResponseDto> createSubComment(@PathVariable Long commentId, @RequestBody SubCommentRequestDto subCommentRequestDto, @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return subCommentService.createSubComment(subCommentRequestDto, commentId, userDetails);
    }
/*
    @GetMapping("/api/quests/{questId}/comments/{commentId}/subComments")
    public ResponseEntity <?> getSubComments(@PathVariable Long commentId) {
        return
    }

    @PutMapping("/api/quests/{questId}/comments/{commentId}/subComments/{subCommentId}")
    public void updateSubComment(@PathVariable Long commentId, @RequestBody SubCommentRequestDto subCommentRequestDto) {
    }

    @DeleteMapping("/api/quests/{questId}/comments/{commentId}/subComments/{subCommentId}")
    public void deleteSubComment(@PathVariable Long commentId) {
    }

 */
}