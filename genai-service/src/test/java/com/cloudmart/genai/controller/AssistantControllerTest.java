package com.cloudmart.genai.controller;

import com.cloudmart.genai.dto.ChatApiRequest;
import com.cloudmart.genai.service.AssistantChatService;
import com.cloudmart.genai.service.SemanticSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssistantController.class)
class AssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AssistantChatService assistantChatService;

    @MockBean
    private SemanticSearchService semanticSearchService;

    @Test
    void chatUsesTheHeaderUserIdNotAnythingFromTheBody() throws Exception {
        var request = new ChatApiRequest(null, "where is my order?");
        when(assistantChatService.chat(any(), eq(7L), any()))
                .thenReturn(new AssistantChatService.ChatResult("conv-1", "Let me check.", java.util.List.of(), java.util.List.of(), false));

        mockMvc.perform(post("/api/assistant/chat")
                        .header("X-User-Id", "7")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(assistantChatService).chat(any(), eq(7L), any());
    }

    @Test
    void chatRequiresTheUserIdHeader() throws Exception {
        var request = new ChatApiRequest(null, "hello");

        mockMvc.perform(post("/api/assistant/chat")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchRequiresNoAuthAtAll() throws Exception {
        when(semanticSearchService.search("keyboard", 10)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/assistant/search").param("q", "keyboard"))
                .andExpect(status().isOk());
    }
}
