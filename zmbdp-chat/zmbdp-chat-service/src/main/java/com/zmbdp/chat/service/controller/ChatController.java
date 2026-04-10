package com.zmbdp.chat.service.controller;

import com.zmbdp.chat.service.domain.dto.ChatMessageDTO;
import com.zmbdp.chat.service.domain.vo.ChatMessageVO;
import com.zmbdp.chat.service.domain.vo.ChatSessionVO;
import com.zmbdp.chat.service.domain.vo.StreamReqDTO;
import com.zmbdp.chat.service.service.IChatService;
import com.zmbdp.common.domain.domain.Result;
import com.zmbdp.common.security.utils.JwtUtil;
import com.zmbdp.common.security.utils.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/chat")
public class ChatController {

    /**
     * 聊天客户端
     */
    @Autowired
    private ChatClient chatClient;

    /**
     * Spring AI 内存（仅进程内）。
     * 注意：这里的隔离粒度取决于 conversationId 的构造方式。
     */
    @Autowired
    private ChatMemory chatMemory;

    /**
     * 聊天记录
     */
    @Autowired
    private IChatService chatService;

    /**
     * jwt 的密钥
     */
    @Value("${jwt.token.secret}")
    private String secret;

    /**
     * 构造 Spring AI 的 conversationId。<br>
     * 为避免不同用户共享同一个 chatId 导致内存上下文串话，这里必须引入 userId 做隔离。
     */
    private static String buildConversationId(Long userId, String chatId) {
        return userId + ":" + chatId;
    }

    /**
     * 按 URL 后缀粗略推断图片 MIME，供 Spring AI {@link Media} 使用。
     */
    private static MimeType mimeTypeForImageUrl(String url) {
        String lower = url.toLowerCase();
        if (lower.endsWith(".png")) {
            return MimeTypeUtils.IMAGE_PNG;
        }
        if (lower.endsWith(".gif")) {
            return MimeTypeUtils.IMAGE_GIF;
        }
        if (lower.endsWith(".webp")) {
            return MimeTypeUtils.parseMimeType("image/webp");
        }
        return MimeTypeUtils.IMAGE_JPEG;
    }

    /**
     * 获取当前登录用户 id
     */
    private Long getCurrentUserId() {
        return Long.valueOf(JwtUtil.getUserId(SecurityUtil.getToken(), secret));
    }

    /**
     * 聊天接口
     *
     * @param streamReqDTO 聊天请求参数
     * @return 聊天结果
     */
    @PostMapping(value = "/stream", produces = "text/html;charset=utf-8")
    public Result<Flux<String>> stream(@Validated @RequestBody StreamReqDTO streamReqDTO) throws Exception {
        String prompt = streamReqDTO.getPrompt();
        String chatId = streamReqDTO.getChatId();
        String imageUrl = streamReqDTO.getImageUrl();

        Long userId = getCurrentUserId();
        String conversationId = buildConversationId(userId, chatId);

        log.info("chatId: {}, userId: {}, prompt: {}, imageUrl: {}", chatId, userId, prompt, imageUrl);

        // 把历史消息同步到 Spring AI 内存中（按 userId + chatId 隔离）
        syncHistoryToMemory(chatId, conversationId);

        // 保存会话标题（只首次写或空标题补写）
        chatService.save(chatId, prompt);

        // 保存用户消息：带图时持久化图片 URL，否则重启后无法从库中恢复多模态消息
        List<String> userMediaUrls = (imageUrl == null || imageUrl.isBlank()) ? null : List.of(imageUrl.trim());
        chatService.saveMessage(chatId, "user", prompt, userMediaUrls);

        Flux<String> contentFlux;
        if (imageUrl == null || imageUrl.isBlank()) {
            // 如果说没有图片，则使用普通方式进行聊天
            contentFlux = this.chatClient.prompt()
                    .user(prompt)
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .stream()
                    .content();
        } else {
            // 如果说有图片，则使用图片方式进行聊天
            log.info("使用图片进行聊天, chatId: {}, userId: {}, prompt: {}, imageUrl: {}", chatId, userId, prompt, imageUrl);
            List<Media> mediaList = List.of(new Media(mimeTypeForImageUrl(imageUrl), new URI(imageUrl.trim()).toURL().toURI()));
            // 构建用户提示词
            UserMessage userMessage = UserMessage.builder()
                    .text(prompt)
                    .media(mediaList)
                    .build();
            contentFlux = this.chatClient.prompt(new Prompt(userMessage))
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .stream()
                    .content();
        }

        StringBuilder assistantReply = new StringBuilder();
        return Result.success(contentFlux
                .doOnNext(assistantReply::append)
                .doOnComplete(() -> {
                    if (!assistantReply.isEmpty()) {
                        chatService.saveMessage(chatId, "assistant", assistantReply.toString(), null);
                    }
                }));
    }

    /**
     * 获取会话列表
     *
     * @return 会话列表
     */
    @GetMapping("/session_history")
    public Result<List<ChatSessionVO>> sessionHistory() {
        return Result.success(chatService.getSessionHistory()
                .stream()
                .map(dto -> new ChatSessionVO(dto.getId(), dto.getTitle()))
                .collect(Collectors.toList()));
    }

    /**
     * 根据聊天 id 获取会话记录
     *
     * @param chatId 聊天 id
     */
    @GetMapping("/message_history")
    public Result<List<ChatMessageVO>> getMessageHistory(@RequestParam String chatId) {
        log.info("获取会话记录, chatId:{}", chatId);
        return Result.success(chatService.getMessageHistory(chatId)
                .stream()
                .map(ChatMessageVO::new)
                .collect(Collectors.toList()));
    }

    /**
     * 根据聊天 id 删除会话
     *
     * @param chatId 聊天 id
     * @return 删除结果
     */
    @DeleteMapping("/delete_by_chat_id")
    public Result<Boolean> deleteByChatId(@RequestParam("chatId") String chatId) {
        Long userId = getCurrentUserId();
        String conversationId = buildConversationId(userId, chatId);

        log.info("删除会话, chatId: {}, userId: {}", chatId, userId);
        try {
            chatService.deleteByChatId(chatId);
            chatMemory.clear(conversationId);
        } catch (Exception e) {
            log.error("删除会话失败, chatId: {}, userId: {}", chatId, userId, e);
            return Result.fail();
        }
        return Result.success();
    }

    /**
     * 把已持久化的历史记录同步到 Spring AI 内存中。
     * 说明：chatMemory 是进程内内存，重启会丢失，所以需要从数据库恢复。
     *
     * @param chatId         聊天 id（用于查库）
     * @param conversationId Spring AI 内存的会话隔离 key（必须包含 userId）
     */
    private void syncHistoryToMemory(String chatId, String conversationId) {
        List<Message> currentMessages = chatMemory.get(conversationId);
        if (currentMessages != null && !currentMessages.isEmpty()) {
            return;
        }

        List<ChatMessageDTO> history = chatService.getMessageHistory(chatId);
        if (history.isEmpty()) {
            return;
        }

        List<Message> messages = history.stream()
                .map(this::buildMessage)
                .collect(Collectors.toList());
        chatMemory.add(conversationId, messages);
    }

    /**
     * 持久化消息转 Spring AI Message
     *
     * @param chatMessageDTO 历史消息
     * @return Message
     */
    private Message buildMessage(ChatMessageDTO chatMessageDTO) {
        if ("assistant".equals(chatMessageDTO.getRole())) {
            return new AssistantMessage(chatMessageDTO.getContent());
        }

        List<String> urls = chatMessageDTO.getMediaUrls();
        if (urls != null && !urls.isEmpty()) {
            List<Media> mediaList = new ArrayList<>();
            for (String url : urls) {
                if (url == null || url.isBlank()) {
                    continue;
                }
                String trimmed = url.trim();
                try {
                    mediaList.add(new Media(mimeTypeForImageUrl(trimmed), new URI(trimmed).toURL().toURI()));
                } catch (Exception e) {
                    log.warn("历史消息中的媒体地址无效，已跳过: {}", trimmed, e);
                }
            }
            if (!mediaList.isEmpty()) {
                return UserMessage.builder()
                        .text(chatMessageDTO.getContent())
                        .media(mediaList)
                        .build();
            }
        }
        return new UserMessage(chatMessageDTO.getContent());
    }
}