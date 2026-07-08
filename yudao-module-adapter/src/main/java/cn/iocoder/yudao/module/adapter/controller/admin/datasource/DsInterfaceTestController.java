package cn.iocoder.yudao.module.adapter.controller.admin.datasource;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceTestReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceTestRespVO;
import cn.iocoder.yudao.module.adapter.engine.EngineResult;
import cn.iocoder.yudao.module.adapter.engine.HttpAdapterEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 数据源接口联调测试台")
@RestController
@RequestMapping("/adapter/ds-interface")
@Validated
public class DsInterfaceTestController {

    @Resource
    private HttpAdapterEngine engine;

    @PostMapping("/{id}/test")
    @Operation(summary = "联调测试台：按接口发起真实外调并展示映射结果（不计费）")
    @Parameter(name = "id", description = "接口ID", required = true)
    @PreAuthorize("@ss.hasPermission('adapter:ds-interface:test')")
    public CommonResult<DsInterfaceTestRespVO> test(@PathVariable("id") Long id,
            @Valid @RequestBody DsInterfaceTestReqVO reqVO) {
        EngineResult r = engine.invoke(id, reqVO.getParams());
        DsInterfaceTestRespVO vo = new DsInterfaceTestRespVO();
        vo.setRequestMethod(r.getRawCall().getRequestMethod());
        vo.setRequestUrl(r.getRawCall().getRequestUrl());
        vo.setRequestHeaders(r.getRawCall().getRequestHeaders());
        vo.setRequestBody(r.getRawCall().getRequestBody());
        vo.setStatusCode(r.getRawCall().getStatusCode());
        vo.setRawResponseBody(r.getRawCall().getRawResponseBody());
        vo.setMappedData(r.getMappedData());
        vo.setPlatformCode(r.getPlatformCode());
        vo.setSuccess(r.isSuccess());
        vo.setCharge(r.isCharge());
        vo.setRetryable(r.isRetryable());
        vo.setTriggerSwitch(r.isTriggerSwitch());
        vo.setCodeMapped(r.isCodeMapped());
        return success(vo);
    }
}
