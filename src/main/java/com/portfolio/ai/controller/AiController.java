package com.portfolio.ai.controller;

import com.portfolio.ai.config.GeminiProperties;
import com.portfolio.ai.dto.ChatRequest;
import com.portfolio.ai.dto.ChatResponse;
import com.portfolio.ai.service.AiChatService;
import com.portfolio.ai.service.AnalysisService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * AI機能に関するAPIコントローラー。
 *
 * サーバー間共有キーによるアクセス制御を行う。
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final GeminiProperties properties;
    private final AiChatService chat;
    private final AnalysisService analysis;

    public AiController(
            GeminiProperties properties,
            AiChatService chat,
            AnalysisService analysis) {

        this.properties = properties;
        this.chat = chat;
        this.analysis = analysis;
    }

    /**
     * AIチャットの回答を生成する。
     */
    @PostMapping("/chat")
    public ChatResponse chat(
            @RequestHeader(value = "X-AI-Key", defaultValue = "") String key,
            @RequestBody ChatRequest request) {

        log.info("[AI] 認証処理開始");

        authorize(key);

        log.info("[AI] 認証処理成功");

        try {
            log.info("[AI] チャット処理開始");

            ChatResponse response = chat.chat(request);

            log.info("[AI] チャット処理成功");

            return response;

        } catch (ResponseStatusException e) {

            log.error(
                    "[AI] チャット処理失敗: status={}, reason={}",
                    e.getStatusCode(),
                    e.getReason());

            throw e;

        } catch (RuntimeException e) {

            log.error(
                    "[AI] チャット処理失敗: type={}",
                    e.getClass().getName());

            throw e;
        }
    }

    /**
     * GitHubの開発活動を分析する。
     */
    @GetMapping("/analysis/{period}")
    public AnalysisService.Report report(
            @RequestHeader(value = "X-AI-Key", defaultValue = "") String key,
            @PathVariable String period) {

        authorize(key);

        // 分析期間の妥当性を確認する
        if (!"weekly".equals(period)
                && !"monthly".equals(period)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND);
        }

        return analysis.report(period);
    }

    /**
     * AI機能の設定状態および共有キーを検証する。
     *
     * @param key リクエストヘッダーの共有キー
     */
    private void authorize(String key) {

        // 設定状態の確認
        boolean enabled = properties.enabled();

        boolean hasApiKey = StringUtils.hasText(properties.apiKey());

        boolean hasAccessKey = StringUtils.hasText(properties.accessKey());

        boolean hasRequestKey = StringUtils.hasText(key);

        // 秘密情報そのものはログに出力しない
        log.info("===== AI CONFIG =====");
        log.info("enabled: {}", enabled);
        log.info("apiKey: {}", hasApiKey);
        log.info("accessKey: {}", hasAccessKey);
        log.info("requestKey: {}", hasRequestKey);
        log.info("=====================");

        // AI機能の必須設定を確認する
        if (!enabled || !hasApiKey || !hasAccessKey) {

            log.warn("[AI] 必須設定が不足しています");

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI disabled or not configured");
        }

        // リクエストヘッダーの必須チェック
        if (!hasRequestKey) {

            log.warn("[AI] リクエストキーが未指定です");

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Access key is required");
        }

        // 共有キーの一致確認
        boolean validAccessKey = MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8),
                properties.accessKey().getBytes(
                        StandardCharsets.UTF_8));

        log.info("validAccessKey: {}", validAccessKey);

        if (!validAccessKey) {

            log.warn("[AI] 共有キーが一致しません");

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid access key");
        }
    }
}