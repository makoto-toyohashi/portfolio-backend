package com.portfolio.ai.dto;
import java.util.List;
/** 会話履歴は呼び出し元が保持する。 */
public record ChatRequest(String message, List<Turn> history) {
    public record Turn(String role, String content) {}
}
