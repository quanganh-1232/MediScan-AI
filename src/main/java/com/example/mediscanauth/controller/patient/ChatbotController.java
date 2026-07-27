package com.example.mediscanauth.controller.patient;

import com.example.mediscanauth.dto.request.ChatRequest;
import com.example.mediscanauth.dto.response.ChatResponse;
import com.example.mediscanauth.service.ChatbotNLPService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    @Autowired
    private ChatbotNLPService chatbotNLPService;

    @PostMapping("/ask")
    public ResponseEntity<ChatResponse> askChatbot(@RequestBody ChatRequest request) {
        ChatResponse response = chatbotNLPService.processMessage(request.getMessage());
        return ResponseEntity.ok(response);
    }
}
