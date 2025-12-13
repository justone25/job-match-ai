package com.jobmatch.exception;

/**
 * Standard error codes for JobMatch application.
 *
 * Error code ranges:
 * - 1xxx: Input errors (user can self-fix)
 * - 2xxx: Parse errors (content issues)
 * - 3xxx: LLM errors (service issues)
 * - 4xxx: Storage errors (file/data issues)
 * - 5xxx: System errors (internal errors)
 */
public enum ErrorCode {

    // ===================== 1xxx: Input Errors =====================
    EMPTY_RESUME(1001, "简历内容为空", "请提供有效的简历文本或文件"),
    EMPTY_JD(1002, "职位描述为空", "请提供有效的JD文本或文件"),
    INVALID_FILE_FORMAT(1003, "文件格式不支持", "支持的格式：.txt, .md, .pdf"),
    FILE_NOT_FOUND(1004, "文件不存在", "请检查文件路径是否正确"),
    FILE_TOO_LARGE(1005, "文件过大", "文件大小不能超过 1MB"),
    INVALID_CONFIG(1006, "配置无效", "请检查配置文件格式"),
    MISSING_REQUIRED_PARAM(1007, "缺少必需参数", "请提供所有必需的参数"),

    // ===================== 2xxx: Parse Errors =====================
    RESUME_PARSE_FAILED(2001, "简历解析失败", "请检查简历格式，确保包含基本信息"),
    JD_PARSE_FAILED(2002, "JD解析失败", "请检查JD格式，确保包含职位要求"),
    SKILL_EXTRACTION_FAILED(2003, "技能提取失败", "无法从内容中提取技能信息"),
    EXPERIENCE_EXTRACTION_FAILED(2004, "经验提取失败", "无法从内容中提取工作经验"),
    JSON_PARSE_ERROR(2005, "JSON解析错误", "LLM输出格式异常，正在重试"),
    CONTENT_TOO_SHORT(2006, "内容过短", "请提供更详细的简历/JD内容"),
    INVALID_DATE_FORMAT(2007, "日期格式无效", "请使用标准日期格式（如：2020.01-2022.06）"),

    // ===================== 3xxx: LLM Errors =====================
    LLM_CONNECTION_FAILED(3001, "LLM服务连接失败", "请检查 Ollama 服务是否启动"),
    LLM_TIMEOUT(3002, "LLM请求超时", "请稍后重试，或检查模型是否过大"),
    LLM_RESPONSE_INVALID(3003, "LLM响应无效", "模型返回了无效数据，正在重试"),
    LLM_MODEL_NOT_FOUND(3004, "LLM模型未找到", "请运行 'ollama pull <model>' 下载模型"),
    LLM_RATE_LIMITED(3005, "请求过于频繁", "请稍后重试"),
    LLM_CONTEXT_TOO_LONG(3006, "输入内容过长", "请缩短简历或JD的长度"),
    LLM_SERVICE_ERROR(3007, "LLM服务错误", "服务暂时不可用，请稍后重试"),

    // ===================== 4xxx: Storage Errors =====================
    STORAGE_READ_FAILED(4001, "读取存储失败", "请检查数据目录权限"),
    STORAGE_WRITE_FAILED(4002, "写入存储失败", "请检查磁盘空间和写入权限"),
    HISTORY_NOT_FOUND(4003, "历史记录不存在", "请使用 'jobmatch history' 查看可用记录"),
    CACHE_CORRUPTED(4004, "缓存数据损坏", "运行 'jobmatch cache clear' 清理缓存"),
    STORAGE_FULL(4005, "存储空间不足", "请清理历史数据或增加磁盘空间"),

    // ===================== 5xxx: System Errors =====================
    INTERNAL_ERROR(5001, "内部错误", "请检查日志文件获取详细信息"),
    CONFIG_LOAD_FAILED(5002, "配置加载失败", "请检查配置文件是否存在"),
    INITIALIZATION_FAILED(5003, "初始化失败", "请检查应用配置和依赖"),
    UNEXPECTED_ERROR(5999, "未知错误", "请提交问题报告");

    private final int code;
    private final String message;
    private final String suggestion;

    ErrorCode(int code, String message, String suggestion) {
        this.code = code;
        this.message = message;
        this.suggestion = suggestion;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getSuggestion() {
        return suggestion;
    }

    /**
     * Get formatted error message.
     */
    public String format() {
        return String.format("[Error %d] %s", code, message);
    }

    /**
     * Get formatted error message with suggestion.
     */
    public String formatWithSuggestion() {
        return String.format("[Error %d] %s\n建议: %s", code, message, suggestion);
    }

    /**
     * Find error code by code number.
     */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode ec : values()) {
            if (ec.code == code) {
                return ec;
            }
        }
        return UNEXPECTED_ERROR;
    }

    /**
     * Check if error is user-fixable (1xxx range).
     */
    public boolean isUserFixable() {
        return code >= 1000 && code < 2000;
    }

    /**
     * Check if error is retryable (some 2xxx, 3xxx).
     */
    public boolean isRetryable() {
        return this == JSON_PARSE_ERROR ||
               this == LLM_TIMEOUT ||
               this == LLM_RESPONSE_INVALID ||
               this == LLM_RATE_LIMITED ||
               this == LLM_SERVICE_ERROR;
    }
}
