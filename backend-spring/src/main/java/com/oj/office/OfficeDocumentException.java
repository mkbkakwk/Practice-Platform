package com.oj.office;

public class OfficeDocumentException extends RuntimeException {

    public enum Category {
        INVALID_FILE_TYPE("仅支持有效的 DOCX 文档"),
        FILE_TOO_LARGE("文档超过允许的大小限制"),
        INVALID_DOCUMENT("文档格式无效或已损坏"),
        UNSUPPORTED_DOCUMENT("文档包含不支持的 Office 功能"),
        PASSWORD_PROTECTED("暂不支持密码保护的 Office 文档"),
        PARSING_FAILED("文档无法安全解析"),
        STORAGE_FAILED("文档暂时无法保存");

        private final String clientMessage;

        Category(String clientMessage) {
            this.clientMessage = clientMessage;
        }

        public String clientMessage() {
            return clientMessage;
        }
    }

    private final Category category;

    public OfficeDocumentException(Category category, String diagnostic) {
        super(diagnostic);
        this.category = category;
    }

    public OfficeDocumentException(Category category, String diagnostic, Throwable cause) {
        super(diagnostic, cause);
        this.category = category;
    }

    public Category category() {
        return category;
    }
}
