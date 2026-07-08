package cn.iocoder.yudao.module.adapter.engine;

import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceDO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceMapper;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceParamMapper;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_INTERFACE_NOT_EXISTS;

/**
 * HTTP 适配引擎编排器——串联入参映射（{@link RequestMapper}）、URL/Body 渲染
 * （{@link TemplateRenderer}）、HTTP 外调（{@link HttpInvoker}）、出参抽取
 * （{@link ResponseExtractor}）与应答码解析（{@link ResponseCodeResolver}）。
 *
 * <p>原始码取值约定（本期）：以出参映射中 {@code platformField == "__rawCode__"} 的定义为
 * 原始码抽取项（其 {@code jsonPath} 指向上游响应里的原始码）；抽取后取该键为 rawCode 交解析器
 * （并从 mappedData 移除该内部键）；若无此约定项则 rawCode 为空 → 必然未映射 → 3001。
 * 此约定后续将升级为接口级独立字段。</p>
 */
@Component
public class HttpAdapterEngine {

    private static final String RAW_CODE_KEY = "__rawCode__";

    @Resource private DsInterfaceMapper interfaceMapper;
    @Resource private DsInterfaceParamMapper paramMapper;
    @Resource private ResponseCodeResolver codeResolver;
    @Resource private HttpInvoker httpInvoker;

    public EngineResult invoke(Long dsInterfaceId, Map<String, Object> platformParams) {
        DsInterfaceDO dif = interfaceMapper.selectById(dsInterfaceId);
        if (dif == null) {
            throw exception(DS_INTERFACE_NOT_EXISTS);
        }
        List<DsInterfaceParamDO> params = paramMapper.selectListByInterface(dsInterfaceId, null);

        // 1. 入参映射
        Map<String, Object> providerParams = RequestMapper.mapInParams(platformParams, params);
        // 2. 渲染 URL 模板 + JSON body
        String url = TemplateRenderer.render(dif.getUri(), providerParams);
        String body = JSONUtil.toJsonStr(providerParams);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        // 3. HTTP 外调
        HttpCallResult call = httpInvoker.call(dif.getMethod(), url, headers, body,
                dif.getTimeoutMs() == null ? 3000 : dif.getTimeoutMs(),
                dif.getRetryCount() == null ? 0 : dif.getRetryCount());
        // 4. 出参抽取 + 原始码
        EngineResult result = new EngineResult();
        result.setRawCall(call);
        Map<String, Object> mapped;
        String rawCode = "";
        try {
            mapped = ResponseExtractor.extract(call.getRawResponseBody(), params);
            Object rc = mapped.remove(RAW_CODE_KEY);
            rawCode = rc == null ? "" : String.valueOf(rc);
        } catch (IllegalArgumentException e) {
            mapped = new HashMap<>(); // 响应非法 JSON → 空数据 + rawCode 空 → 3001
        }
        result.setMappedData(mapped);
        // 5. 应答码解析
        CodeResolution cr = codeResolver.resolve(dif.getDataSourceId(), dsInterfaceId, rawCode);
        result.setPlatformCode(cr.getPlatformCode());
        result.setSuccess(cr.isSuccess());
        result.setCharge(cr.isCharge());
        result.setRetryable(cr.isRetryable());
        result.setTriggerSwitch(cr.isTriggerSwitch());
        result.setCodeMapped(cr.isMapped());
        return result;
    }
}
