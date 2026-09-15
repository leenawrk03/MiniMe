package dev.minime.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.minime.config.MiniMeProperties;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * Client for the official GitHub MCP server (github/github-mcp-server),
 * run separately as a Docker container in Streamable HTTP mode
 * (see docker-compose.yml, service "github-mcp").
 *
 * A new short-lived client is opened per call, matching the style of
 * {@link McpClientService}. Every public method fails soft (returns an
 * empty/error result rather than throwing) so a missing or unreachable
 * GitHub MCP container never breaks the rest of MiniMe.
 */
@Service
public class GitHubMcpClientService {

    private static final Logger log =
            LoggerFactory.getLogger(GitHubMcpClientService.class);

    private final ObjectMapper objectMapper;
    private final MiniMeProperties props;

    public GitHubMcpClientService(
            ObjectMapper objectMapper,
            MiniMeProperties props) {
        this.objectMapper = objectMapper;
        this.props = props;
    }

    private MiniMeProperties.Mcp.GitHub config() {
        return props.getMcp().getGithub();
    }

    public boolean isEnabled() {
        return config().isEnabled();
    }

    private McpSyncClient createClient() {

        String endpoint = config().getEndpoint();

        log.info("Connecting to GitHub MCP server: {}", endpoint);

        JacksonMcpJsonMapper jsonMapper =
                new JacksonMcpJsonMapper(objectMapper);

        String token = config().getToken();

        HttpClientSseClientTransport transport =
                HttpClientSseClientTransport
                        .builder(endpoint)
                        .sseEndpoint("/sse")
                        .jsonMapper(jsonMapper)
                        .connectTimeout(Duration.ofSeconds(10))
                        .customizeRequest(builder -> {
                            if (token != null && !token.isBlank()) {
                                builder.header(
                                        "Authorization",
                                        "Bearer " + token.trim()
                                );
                            }
                        })
                        .build();

        McpSyncClient client =
                McpClient.sync(transport)
                        .clientInfo(
                                new McpSchema.Implementation(
                                        "MiniMe-GitHub",
                                        "1.0.0",
                                        null
                                )
                        )
                        .requestTimeout(Duration.ofSeconds(30))
                        .initializationTimeout(Duration.ofSeconds(10))
                        .build();

        client.initialize();

        log.info("GitHub MCP client initialized successfully.");

        return client;
    }

    public boolean isAvailable() {

        if (!isEnabled()) {
            return false;
        }

        try {

            McpSyncClient client = createClient();

            try {
                return client.isInitialized();
            } finally {
                client.close();
            }

        } catch (Exception e) {

            log.warn(
                    "GitHub MCP server is unavailable: {}",
                    e.getMessage()
            );

            return false;
        }
    }

    /**
     * Lists the tools exposed by the GitHub MCP server.
     * Returns an empty result (never throws) when the feature is disabled
     * or the container isn't reachable, so callers can use this safely
     * on every chat turn.
     */
    public McpSchema.ListToolsResult listTools() {

        if (!isEnabled()) {
            return new McpSchema.ListToolsResult(
                    java.util.List.of(),
                    null
            );
        }

        try {

            McpSyncClient client = createClient();

            try {
                McpSchema.ListToolsResult result = client.listTools();

                log.info(
                        "GitHub MCP tools discovered: {}",
                        result.tools()
                                .stream()
                                .map(McpSchema.Tool::name)
                                .toList()
                );

                return result;

            } finally {
                client.close();
            }

        } catch (Exception e) {

            log.warn(
                    "Could not list GitHub MCP tools: {}",
                    e.getMessage()
            );

            return new McpSchema.ListToolsResult(
                    java.util.List.of(),
                    null
            );
        }
    }

    public McpSchema.CallToolResult callTool(
            String toolName,
            Map<String, Object> arguments) {

        McpSyncClient client = createClient();

        try {
            log.info(
                    "Calling GitHub MCP tool: {} with arguments: {}",
                    toolName,
                    arguments
            );

            McpSchema.CallToolResult result =
                    client.callTool(
                            new McpSchema.CallToolRequest(
                                    toolName,
                                    arguments
                            )
                    );

            log.info(
                    "GitHub MCP tool {} completed. Error={}",
                    toolName,
                    result.isError()
            );

            return result;

        } finally {
            client.close();
        }
    }
}
