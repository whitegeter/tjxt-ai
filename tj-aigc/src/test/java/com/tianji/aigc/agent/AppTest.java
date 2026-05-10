package com.tianji.aigc.agent;

import cn.hutool.core.map.MapUtil;
import com.alibaba.dashscope.app.Application;
import com.alibaba.dashscope.app.ApplicationParam;
import com.alibaba.dashscope.app.ApplicationResult;
import com.alibaba.dashscope.utils.JsonUtils;
import io.reactivex.Flowable;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class AppTest {

    @Test

    public void testAppCall() throws Exception {
        // 构造业务参数
        String token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJ1c2VyIjp7InVzZXJJZCI6Miwicm9sZUlkIjoyLCJyZW1lbWJlck1lIjpmYWxzZX0sImV4cCI6MTc4MDk5NDg5MX0.DlwHZjwai3WB_VWjbsdfZlqzdPOzgTmjSwWn-2Uu7SNgqK9whdzpLq_zvXzBJHDndxR-IEpBx1QcD50gEU1kpgKPd7fnr8ftYV0WCtn963U3mwFHvqU-YQshygjR4pOabWKKjMjb0HfhjJV7Yl6xr2KTvm5LtPcHFBgIpdFXP1Jnc0zweTewq3O3gMxR9Nou1EY4FmRw8qznG65II32Wy1RO8iM4JNAh6EaEAadOOEE_3RjGd-3LIfpALn7023mFF4wX43TM569Msngp06oU4v5aIQt2XbHlSzLqQxUY_i1FsJwgcdjQUOBVCvaXboEjJ-gR7juhdXRZQGQ-8jeu5Q";
        Map<String, Object> bizParams = MapUtil.<String, Object>builder()
                .put("user_defined_tokens", MapUtil.of("tool_16a56d5d-c334-49fc-a85c-82708cd15e5e", // 工具id
                        MapUtil.of("user_token", token)))
                .build();

        // bizParams.add("user_defined_tokens", JsonObject);
        ApplicationParam param = ApplicationParam.builder()
                // 若没有配置环境变量，可用百炼API Key将下行替换为：.apiKey("sk-xxx")。但不建议在生产环境中直接将API Key硬编码到代码中，以减少API Key泄露风险。
                .apiKey(System.getenv("DASH_SCOPE_API_KEY"))
                .appId("9adba067471e40049235b1e16e398ac1") // 智能体id
                .prompt("查询课程，id为：1880533253575225346")
                .incrementalOutput(true) // 开启增量输出
                .bizParams(JsonUtils.toJsonObject(bizParams))
                .build();

        Application application = new Application();
        Flowable<ApplicationResult> result = application.streamCall(param);

        // 阻塞式的打印内容
        result.blockingForEach(data -> {
            System.out.printf("%s\n",data.getOutput().getText());
        });

    }

}
