package com.cloudmart.genai.controller;

import com.cloudmart.genai.dto.ChatApiRequest;
import com.cloudmart.genai.dto.ChatApiResponse;
import com.cloudmart.genai.dto.ProductMatch;
import com.cloudmart.genai.service.AssistantChatService;
import com.cloudmart.genai.service.SemanticSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
@Validated
public class AssistantController {

    private final AssistantChatService assistantChatService;
    private final SemanticSearchService semanticSearchService;

    @PostMapping("/chat")
    public ChatApiResponse chat(@Valid @RequestBody ChatApiRequest request) {
        var result = assistantChatService.chat(request.conversationId(), request.userId(), request.message());
        return new ChatApiResponse(result.conversationId(), result.reply(), result.productCards());
    }

    @GetMapping("/search")
    public List<ProductMatch> search(@RequestParam @NotBlank String q,
                                      @RequestParam(defaultValue = "10") int limit) {
        return semanticSearchService.search(q, limit);
    }
}
