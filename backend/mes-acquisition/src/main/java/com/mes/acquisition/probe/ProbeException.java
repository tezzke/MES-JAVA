package com.mes.acquisition.probe;

/** 探针请求在执行前被拒绝。 */
public class ProbeException extends RuntimeException {

    private final ProbeErrorCategory category;

    public ProbeException(ProbeErrorCategory category, String message) {
        super(message);
        this.category = category;
    }

    public ProbeException(ProbeErrorCategory category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public ProbeErrorCategory category() {
        return category;
    }
}
