package dev.minime.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
public class McpTestController {

    private final McpClientService mcpClient;
    private final GitHubMcpClientService githubMcp;

    public McpTestController(
            McpClientService mcpClient,
            GitHubMcpClientService githubMcp) {
        this.mcpClient = mcpClient;
        this.githubMcp = githubMcp;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {

        return Map.of(
                "available",
                mcpClient.isAvailable()
        );
    }

    @GetMapping("/tools")
    public Object tools() {

        return mcpClient.listTools();
    }

    @PostMapping("/open-notes")
    public Object openNotes() {

        McpSchema.CallToolResult result =
                mcpClient.callTool(
                        "open_application",
                        Map.of(
                                "appName",
                                "Notes"
                        )
                );

        return result;
    }

    @GetMapping("/github-status")
    public Map<String, Object> githubStatus() {

        return Map.of(
                "enabled",
                githubMcp.isEnabled(),
                "available",
                githubMcp.isAvailable()
        );
    }

    @GetMapping("/github-tools")
    public Object githubTools() {
        return githubMcp.listTools();
    }
    @GetMapping("/github-repos")
    public Object githubRepos() {

        return githubMcp.callTool(
                "search_repositories",
                Map.of(
                        "query",
                        "MiniMe user:leenawrk03"
                )
        );
    }
}
