package org.example.dsdemo.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.dsdemo.dto.ApiRequest;
import org.example.dsdemo.dto.Message;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson.JSONObject;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONUtil;


@RestController
@CrossOrigin(origins = "*")
public class DeepSeekController {

    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private static final String API_KEY = "sk-xxxxxxxxxxxxxxxxxxxxxxxx";
    private final Path uploadDir = Paths.get("uploads");
    private final ConcurrentHashMap<String, List<Message>> userSessions = new ConcurrentHashMap<>();
    
    // 存储上传的文件内容缓存
    private final Map<String, String> fileContentCache = new HashMap<>();
    
    // 知识图谱提示词
    private static final String KNOWLEDGE_GRAPH_PROMPT = "如果用户请求总结文章内容、概括文本或生成摘要，请分析文章中的知识点和概念之间的关系，按照以下要求输出：\n" +
            "1. 首先分析文章提取主要知识点、概念和它们之间的关系\n" +
            "2. 输出结果需要按照特殊格式排列\n" +
            "3. 使用\"===RELATION_START===\"和\"===RELATION_END===\"作为起始和结束标记\n" +
            "4. 每行格式为: \"知识点名称,概念名称,关系描述\"\n\n" +
            "请确保输出具有以下特征：\n" +
            "- 知识点和概念名称应简洁明了，代表文章中的关键实体\n" +
            "- 所有关系都用中文描述，简明扼要\n" +
            "- 确保覆盖文章中所有重要的知识点、概念和关系\n" +
            "- 关系描述应准确反映知识点和概念之间的语义关系\n" +
            "- 知识点和概念应有明确区分，不要重复\n\n" +
            "在输出上述内容后，请提供一个简短的总结，解释提取出了哪些核心知识点、概念和关系。\n" +
            "最后，请再次确认所有数据都已按照正确的格式输出，不要漏掉开始和结束标记。";
    
    // 普通对话提示词 - 不主动提及文件内容
    private static final String NORMAL_CHAT_PROMPT = "在与用户的普通对话中，除非用户明确要求，否则不要主动提及已上传的文件内容。\n" +
            "只有当用户明确询问文件内容、要求参考文件回答问题，或者使用类似\"看看文件\"、\"文件中说什么\"等表达时，才引用或讨论文件内容。\n" +
            "在其他情况下，像普通对话助手一样响应用户的问题，不用提及有文件上传这件事。";
    
    // 用于检测用户是否请求总结或概括的正则表达式
    private static final Pattern SUMMARY_PATTERN = Pattern.compile("(?i).*(总结|概括|摘要|归纳|提炼|要点|重点|梳理|总览|综述).*");
    
    // 用于检测用户是否明确要求查看文件内容的正则表达式
    private static final Pattern FILE_CONTENT_PATTERN = Pattern.compile("(?i).*(文件内容|文件说什么|查看文件|看看文件|文件中的|参考文件).*");
    
    // 在应用启动时加载文件内容
    public DeepSeekController() {
        try {
            // 确保uploads目录存在
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            // 预加载所有已上传文件
            loadAllFilesFromUploads();
        } catch (Exception e) {
            // 初始化时出错只记录，不影响应用启动
            System.err.println("初始化加载文件失败: " + e.getMessage());
        }
    }
    
    // 加载uploads文件夹中的所有文件
    private void loadAllFilesFromUploads() {
        try (Stream<Path> paths = Files.list(uploadDir)) {
            paths.filter(Files::isRegularFile)
                 .forEach(filePath -> {
                     try {
                         String fileName = filePath.getFileName().toString();
                         String contentType = getContentTypeByExtension(fileName);
                         if (contentType != null && isAllowedType(contentType)) {
                             String content = extractContent(filePath, contentType);
                             fileContentCache.put(fileName, content);
                             System.out.println("已加载文件: " + fileName);
                         }
                     } catch (Exception e) {
                         System.err.println("加载文件失败: " + filePath + ", 错误: " + e.getMessage());
                     }
                 });
        } catch (IOException e) {
            System.err.println("读取uploads文件夹失败: " + e.getMessage());
        }
    }
    
    // 根据文件扩展名获取内容类型
    private String getContentTypeByExtension(String fileName) {
        String extension = FilenameUtils.getExtension(fileName).toLowerCase();
        switch (extension) {
            case "pdf":
                return "application/pdf";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            default:
                return null;
        }
    }
    
    // 获取已上传文件列表的API
    @GetMapping("/api/files")
    public ResponseEntity<List<Map<String, String>>> getUploadedFiles() {
        try {
            List<Map<String, String>> files = new ArrayList<>();
            for (Map.Entry<String, String> entry : fileContentCache.entrySet()) {
                Map<String, String> fileInfo = new HashMap<>();
                fileInfo.put("fileName", entry.getKey());
                // 限制内容预览长度
                String preview = entry.getValue();
                if (preview.length() > 100) {
                    preview = preview.substring(0, 100) + "...";
                }
                fileInfo.put("preview", preview);
                files.add(fileInfo);
            }
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Collections.emptyList());
        }
    }

    @GetMapping("/api/history")
    public ResponseEntity<List<Message>> getChatHistory(@RequestParam String userId) {
        List<Message> history = userSessions.getOrDefault(userId, Collections.emptyList());
        return ResponseEntity.ok(history);
    }
    
    @PostMapping(value = "/api/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> unifiedChat(
            @RequestPart("message") String userMessage,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart("userId") String userId) {

        try {
            // 处理新上传的文件
            List<String> newFileContents = processUploadedFiles(files);
            List<Message> conversationHistory = userSessions.computeIfAbsent(userId, k -> new ArrayList<>());

            List<Message> messages = new ArrayList<>();

            // 检查用户消息是否请求总结或概括
            boolean isSummaryRequest = SUMMARY_PATTERN.matcher(userMessage).matches();
            // 检查用户是否明确要求查看文件内容
            boolean isFileContentRequest = FILE_CONTENT_PATTERN.matcher(userMessage).matches();
            
            // 添加适当的系统提示词
            if (isSummaryRequest) {
                // 如果是总结请求，添加知识图谱提示词作为系统消息
                Message systemPrompt = new Message();
                systemPrompt.setRole("system");
                systemPrompt.setContent(KNOWLEDGE_GRAPH_PROMPT);
                messages.add(systemPrompt);
            } else {
                // 如果是普通对话，添加普通对话提示词
                Message systemPrompt = new Message();
                systemPrompt.setRole("system");
                systemPrompt.setContent(NORMAL_CHAT_PROMPT);
                messages.add(systemPrompt);
            }

            // 只在总结请求或明确要求查看文件内容时才添加文件内容
            if (isSummaryRequest || isFileContentRequest) {
                // 添加所有已上传文件的内容作为上下文
                StringBuilder allFileContents = new StringBuilder();
                
                // 首先添加新上传的文件内容
                if (!newFileContents.isEmpty()) {
                    allFileContents.append("新上传文件内容：\n").append(String.join("\n", newFileContents)).append("\n\n");
                }
                
                // 然后添加之前上传的文件内容
                if (!fileContentCache.isEmpty()) {
                    allFileContents.append("已存储的文件内容：\n");
                    for (Map.Entry<String, String> entry : fileContentCache.entrySet()) {
                        allFileContents.append("文件名：").append(entry.getKey()).append("\n");
                        allFileContents.append("内容：").append(entry.getValue()).append("\n\n");
                    }
                }
                
                // 如果有文件内容，添加为系统消息
                if (allFileContents.length() > 0) {
                    Message fileContentMessage = new Message();
                    fileContentMessage.setRole("system");
                    fileContentMessage.setContent(allFileContents.toString());
                    messages.add(fileContentMessage);
                }
            }
            
            messages.addAll(conversationHistory);
            // 当前用户消息
            Message currentUserMessage = new Message();
            currentUserMessage.setRole("user");
            currentUserMessage.setContent(userMessage);
            messages.add(currentUserMessage);

            ApiRequest apiRequest = new ApiRequest();
            apiRequest.setMessages(messages);

            String responseJson = HttpRequest.post(API_URL)
                    .header("Authorization", "Bearer " + API_KEY)
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(apiRequest))
                    .execute().body();

            JSONObject jsonResponse = JSONObject.parseObject(responseJson);
            String generatedText = jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            // 处理生成的CSV内容并保存到文件
            if (isSummaryRequest) {
                saveCsvToFile(generatedText);
            }

            // 对话历史
            conversationHistory.add(currentUserMessage);
            Message assistantMessage = new Message();
            assistantMessage.setRole("assistant");
            assistantMessage.setContent(generatedText);
            conversationHistory.add(assistantMessage);

            return ResponseEntity.ok(generatedText);

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("请求处理失败: " + e.getMessage());
        }
    }

    // 将CSV内容保存到文件
    private void saveCsvToFile(String content) {
        try {
            // 创建Output目录（如果不存在）
            Path outputDir = Paths.get("Output");
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
            
            // 清空Output目录中的所有文件
            try (Stream<Path> paths = Files.list(outputDir)) {
                paths.filter(Files::isRegularFile)
                     .forEach(file -> {
                         try {
                             Files.delete(file);
                             System.out.println("已删除文件: " + file.getFileName());
                         } catch (IOException e) {
                             System.err.println("删除文件失败: " + file.getFileName() + ", 错误: " + e.getMessage());
                         }
                     });
            }
            System.out.println("已清空Output目录");
            
            // 定义输出文件路径
            Path filePath = outputDir.resolve("relationship.csv");
            
            // 解析内容
            String[] lines = content.split("\n");
            
            // 提取关系列表
            List<String> relationList = extractSection(lines, "===RELATION_START===", "===RELATION_END===");
            
            // 如果成功提取了数据，生成CSV文件
            if (!relationList.isEmpty()) {
                // 创建CSV内容
                StringBuilder csvContent = new StringBuilder();
                csvContent.append("knowledge_id,knowledge_name,concept_id,concept_name,relation\n");
                
                // 为知识点和概念创建唯一ID映射
                Map<String, Integer> knowledgeMap = new HashMap<>();
                Map<String, Integer> conceptMap = new HashMap<>();
                int knowledgeCounter = 1;
                int conceptCounter = 1;
                
                // 处理所有关系
                for (String relation : relationList) {
                    String[] parts = relation.split(",", 3);
                    if (parts.length >= 3) {
                        String knowledgeName = parts[0].trim();
                        String conceptName = parts[1].trim();
                        String connectionDesc = parts[2].trim();
                        
                        // 获取或分配知识点ID
                        Integer knId = knowledgeMap.get(knowledgeName);
                        if (knId == null) {
                            knId = knowledgeCounter++;
                            knowledgeMap.put(knowledgeName, knId);
                        }
                        
                        // 获取或分配概念ID
                        Integer cId = conceptMap.get(conceptName);
                        if (cId == null) {
                            cId = conceptCounter++;
                            conceptMap.put(conceptName, cId);
                        }
                        
                        // 添加CSV行
                        csvContent.append(knId).append(",")
                                  .append(knowledgeName).append(",")
                                  .append(cId).append(",")
                                  .append(conceptName).append(",")
                                  .append(connectionDesc).append("\n");
                    }
                }
                
                // 写入CSV文件
                Files.write(filePath, csvContent.toString().getBytes());
                System.out.println("relationship.csv文件已保存到Output目录");
            } else {
                System.out.println("未找到有效的关系数据");
            }
        } catch (Exception e) {
            System.err.println("保存CSV文件失败: " + e.getMessage());
        }
    }
    
    // 从文本中提取指定部分的内容
    private List<String> extractSection(String[] lines, String startMarker, String endMarker) {
        List<String> result = new ArrayList<>();
        boolean hasFoundMarker = false;
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            // 找到开始标记
            if (trimmedLine.equals(startMarker)) {
                hasFoundMarker = true;
                continue;
            }
            
            // 找到结束标记
            if (trimmedLine.equals(endMarker)) {
                break;
            }
            
            // 如果找到标记，开始收集内容
            if (hasFoundMarker && !trimmedLine.isEmpty()) {
                result.add(trimmedLine);
            }
        }
        
        return result;
    }

    private List<String> processUploadedFiles(List<MultipartFile> files) {
        if (files == null) return Collections.emptyList();

        List<String> contents = new ArrayList<>();
        try {
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            for (MultipartFile file : files) {
                // 文件类型
                if (!isAllowedType(file.getContentType())) {
                    continue;
                }
                String fileId = UUID.randomUUID().toString();
                String extension = FilenameUtils.getExtension(file.getOriginalFilename());
                String fileName = fileId + "." + extension;
                Path filePath = uploadDir.resolve(fileName);
                Files.copy(file.getInputStream(), filePath);
                
                String content = extractContent(filePath, file.getContentType());
                contents.add(content);
                
                // 存储到缓存中
                fileContentCache.put(fileName, content);
            }
        } catch (IOException e) {
            throw new RuntimeException("文件处理失败", e);
        }
        return contents;
    }

    private String extractContent(Path filePath, String contentType) {
        try {
            if (contentType.equals("application/pdf")) {
                return extractPdfContent(filePath);
            } else if (contentType.startsWith("image/")) {
                return "[图片文件内容无法解析]";
            }
            return "[不支持的文件类型]";
        } catch (Exception e) {
            return "[内容解析失败]";
        }
    }

    private String extractPdfContent(Path filePath) throws IOException {
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private boolean isAllowedType(String contentType) {
        return List.of(
                "application/pdf",
                "image/png",
                "image/jpeg"
        ).contains(contentType);
    }
}