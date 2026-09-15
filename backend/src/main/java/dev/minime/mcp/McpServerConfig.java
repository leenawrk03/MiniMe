package dev.minime.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
public class McpServerConfig {

    private static final Set<String> ALLOWED_APPS = Set.of(
            "Google Chrome",
            "Safari",
            "Visual Studio Code",
            "Terminal",
            "Finder",
            "Notes",
            "Calendar",
            "Mail",
            "Messages",
            "Slack",
            "WhatsApp"
    );

    @Bean
    public HttpServletStreamableServerTransportProvider mcpTransport(
            ObjectMapper objectMapper) {

        JacksonMcpJsonMapper jsonMapper =
                new JacksonMcpJsonMapper(objectMapper);

        return HttpServletStreamableServerTransportProvider
                .builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint("/mcp")
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transportProvider) {

        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
                new ServletRegistrationBean<>(
                        transportProvider,
                        "/mcp"
                );

        registration.setName("minime-mcp");
        registration.setLoadOnStartup(1);

        return registration;
    }

    @Bean
    public McpSyncServer mcpServer(
            HttpServletStreamableServerTransportProvider transportProvider) {

        McpSchema.JsonSchema openApplicationSchema =
                new McpSchema.JsonSchema(
                        "object",
                        Map.of(
                                "appName",
                                Map.of(
                                        "type",
                                        "string",
                                        "description",
                                        "The exact macOS application name to open."
                                )
                        ),
                        List.of("appName"),
                        false,
                        null,
                        null
                );

        McpSchema.Tool openApplicationTool =
                McpSchema.Tool.builder()
                        .name("open_application")
                        .title("Open Application")
                        .description(
                                "Opens an allowed macOS application by name. " +
                                        "Use exact application names such as Safari, " +
                                        "Google Chrome, Terminal, Finder, Notes, Slack, " +
                                        "or WhatsApp."
                        )
                        .inputSchema(openApplicationSchema)
                        .annotations(
                                new McpSchema.ToolAnnotations(
                                        "Open Application",
                                        false,
                                        true,
                                        false,
                                        false,
                                        true
                                )
                        )
                        .build();

        McpSyncServer server =
                McpServer.sync(transportProvider)
                        .serverInfo("MiniMe MCP Server", "1.0.0")
                        .capabilities(
                                McpSchema.ServerCapabilities.builder()
                                        .tools(true)
                                        .build()
                        )
                        .toolCall(
                                openApplicationTool,
                                (exchange, request) -> {
                                    System.out.println(">>> MCP TOOL CALLED: open_application");
                                    Object rawAppName =
                                            request.arguments()
                                                    .get("appName");

                                    String appName =
                                            rawAppName == null
                                                    ? ""
                                                    : rawAppName
                                                    .toString()
                                                    .trim();

                                    if (appName.isEmpty()) {
                                        return McpSchema.CallToolResult.builder()
                                                .addTextContent(
                                                        "Application name is required."
                                                )
                                                .isError(true)
                                                .build();
                                    }

                                    if (!ALLOWED_APPS.contains(appName)) {
                                        return McpSchema.CallToolResult.builder()
                                                .addTextContent(
                                                        "Application is not allowed: "
                                                                + appName
                                                )
                                                .isError(true)
                                                .build();
                                    }

                                    try {
                                        Process process =
                                                new ProcessBuilder(
                                                        "open",
                                                        "-a",
                                                        appName
                                                )
                                                        .redirectErrorStream(true)
                                                        .start();

                                        int exitCode =
                                                process.waitFor();

                                        String output =
                                                new String(
                                                        process.getInputStream()
                                                                .readAllBytes(),
                                                        StandardCharsets.UTF_8
                                                ).trim();

                                        if (exitCode != 0) {
                                            return McpSchema.CallToolResult.builder()
                                                    .addTextContent(
                                                            "Could not open "
                                                                    + appName
                                                                    + ". "
                                                                    + output
                                                    )
                                                    .isError(true)
                                                    .build();
                                        }

                                        return McpSchema.CallToolResult.builder()
                                                .addTextContent(
                                                        "Successfully opened "
                                                                + appName
                                                )
                                                .build();

                                    } catch (IOException e) {

                                        return McpSchema.CallToolResult.builder()
                                                .addTextContent(
                                                        "Failed to open "
                                                                + appName
                                                                + ": "
                                                                + e.getMessage()
                                                )
                                                .isError(true)
                                                .build();

                                    } catch (InterruptedException e) {

                                        Thread.currentThread()
                                                .interrupt();

                                        return McpSchema.CallToolResult.builder()
                                                .addTextContent(
                                                        "Opening "
                                                                + appName
                                                                + " was interrupted."
                                                )
                                                .isError(true)
                                                .build();
                                    }
                                }
                        )
                        .build();

        return server;
    }
}
