package cn.iocoder.yudao.module.adapter.engine;

import lombok.Data;

@Data
public class CodeResolution {
    private String platformCode;
    private boolean success;
    private boolean charge;
    private boolean retryable;
    private boolean triggerSwitch;
    private boolean mapped;
}
