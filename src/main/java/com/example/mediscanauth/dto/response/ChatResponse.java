package com.example.mediscanauth.dto.response;

public class ChatResponse {
    private String reply;
    private String action;

    public ChatResponse(String reply, String action) {
        this.reply = reply;
        this.action = action;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}
