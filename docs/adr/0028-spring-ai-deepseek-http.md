# AI 集成：DeepSeek HTTP API 直接调用

AI 能力用 **RestTemplate 直接调 DeepSeek HTTP 接口**实现，**不**用 Spring AI 框架：

- **API**：`POST https://api.deepseek.com/chat/completions`（OpenAI 兼容协议）
- **请求体**：
  ```json
  {
    "model": "deepseek-chat",
    "messages": [
      {"role": "system", "content": "你是一个企业工单分类助手..."},
      {"role": "user", "content": "标题：xxx\n内容：xxx"}
    ],
    "response_format": {"type": "json_object"}
  }
  ```
- **HTTP 客户端**：Spring `RestTemplate`（够用，不上 WebClient）
- **配置**：`application.yml` 配 `ai.deepseek.api-key=${DEEPSEEK_API_KEY}`（环境变量注入）
- **环境变量**：`DEEPSEEK_API_KEY` 让开发者自己配，**不进 Git**

## 简化（不做的）

- 不做多 provider failover（DeepSeek 一个就够）
- 不做 Spring AI 框架集成（直接 HTTP 调用更易讲清楚）
- 不做 Prompt 版本化（写代码里改即可）
- AI 失败时不重试，catch 异常返回模板回复

## 影响

- `AiService.classify(title, content)` 调 DeepSeek，返回 JSON `{type, priority, department}` 解析后落 `ai_ticket_record`
- `AiService.generateReply(ticket)` 调 DeepSeek，返回回复文本
- 失败时 catch 异常，记录 `ai_ticket_record.error_log = exception.getMessage()`，返回模板回复

## 面试怎么说

"我用 RestTemplate 调 DeepSeek 的 OpenAI 兼容接口，自己拼请求体 JSON 和解析返回。AI 分类返回 type/priority/department 三元组，落库到 `ai_ticket_record` 表，工单创建时同步落。失败时 catch 异常不阻塞工单创建，返回模板回复"。

## 开发者需要做的事

1. 去 https://platform.deepseek.com 注册账号
2. 充值（一般 ¥5 够用很久）
3. 创建 API Key
4. 配到环境变量 `DEEPSEEK_API_KEY=sk-xxxxxxxx`
5. Spring Boot 启动时 `@Value("${ai.deepseek.api-key}")` 读取