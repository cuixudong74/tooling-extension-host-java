package com.github.cuixudong74.toolingextensionhostjava.tests.extension;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST 接口端到端：更新集合 -> 查询生效贡献 -> 解释覆盖链 -> 非法更新回退。
 */
@SpringBootTest(properties = {
        "extension-host.api-version=1.5.0",
        "extension-host.capabilities=git,docker",
        "extension-host.compatibility-policy=REJECT"
})
@AutoConfigureMockMvc
class ExtensionHostControllerTest {

    @Autowired
    private MockMvc mvc;

    private static String ext(String id, int priority, String value) {
        return """
                {
                  "id": "%s", "version": "1.0.0", "apiVersionRange": "[1.0,2.0)",
                  "requiredCapabilities": ["git"],
                  "contributions": [
                    {"type": "SETTING", "id": "editor.tabSize", "priority": %d, "payload": {"default": "%s"}}
                  ]
                }
                """.formatted(id, priority, value);
    }

    @Test
    void endToEndUpdateQueryExplainAndRollback() throws Exception {
        // 1. 提交两个冲突扩展，高优先级胜出
        mvc.perform(post("/api/extension-host/extensions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + ext("ext.low", 1, "2") + "," + ext("ext.high", 9, "8") + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committed").value(true))
                .andExpect(jsonPath("$.generation").value(1));

        // 2. 查询生效贡献
        mvc.perform(get("/api/extension-host/contributions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['SETTING:editor.tabSize'].winner.extensionId").value("ext.high"))
                .andExpect(jsonPath("$['SETTING:editor.tabSize'].winner.contribution.payload.default").value("8"));

        // 3. 解释覆盖链
        mvc.perform(get("/api/extension-host/explain/setting/editor.tabSize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.winner.extensionId").value("ext.high"))
                .andExpect(jsonPath("$.overriddenBy[0].extensionId").value("ext.low"))
                .andExpect(jsonPath("$.affectedBy[0]").value("ext.high"))
                .andExpect(jsonPath("$.affectedBy[1]").value("ext.low"));

        // 4. 非法更新被拒绝并回退，旧集合继续服务
        mvc.perform(post("/api/extension-host/extensions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"id\": \"ext.broken\", \"contributions\": []}]"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.committed").value(false));

        mvc.perform(get("/api/extension-host/contributions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['SETTING:editor.tabSize'].winner.extensionId").value("ext.high"));

        // 5. 停用后另一扩展生效
        mvc.perform(post("/api/extension-host/extensions/ext.high/disable"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/extension-host/explain/setting/editor.tabSize"))
                .andExpect(jsonPath("$.winner.extensionId").value("ext.low"));

        // 6. 状态接口
        mvc.perform(get("/api/extension-host/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeExtensions[0]").value("ext.low@1.0.0"));
    }
}
