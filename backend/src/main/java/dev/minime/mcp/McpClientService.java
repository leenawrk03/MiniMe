package dev.minime.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
public class McpClientService {

    private static final Logger log =
            LoggerFactory.getLogger(McpClientService.class);

    private static final String MCP_ENDPOINT =
            "http://localhost:842/mcp";

    private final ObjectMapper objectMapper;

    public McpClientService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private McpSyncClient createClient() {

        log.info("Connecting to MCP endpoint: {}", MCP_ENDPOINT);

        JacksonMcpJsonMapper jsonMapper =
                new JacksonMcpJsonMapper(objectMapper);

        HttpClientStreamableHttpTransport transport =
                HttpClientStreamableHttpTransport
                        .builder(MCP_ENDPOINT)
                        .jsonMapper(jsonMapper)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

        McpSyncClient client =
                McpClient.sync(transport)
                        .clientInfo(
                                new McpSchema.Implementation(
                                        "MiniMe",
                                        "1.0.0",
                                        null
                                )
                        )
                        .requestTimeout(Duration.ofSeconds(30))
                        .initializationTimeout(Duration.ofSeconds(10))
                        .build();

        client.initialize();

        log.info("MCP client initialized successfully.");

        return client;
    }

    public boolean isAvailable() {

        try {

            McpSyncClient client = createClient();

            try {
                return client.isInitialized();
            } finally {
                client.close();
            }

        } catch (Exception e) {

            log.warn(
                    "MCP server is unavailable: {}",
                    e.getMessage()
            );

            return false;
        }
    }

    public McpSchema.ListToolsResult listTools() {

        McpSyncClient client = createClient();

        try {

            McpSchema.ListToolsResult result =
                    client.listTools();

            log.info(
                    "MCP tools discovered: {}",
                    result.tools()
                            .stream()
                            .map(McpSchema.Tool::name)
                            .toList()
            );

            return result;

        } finally {

            client.close();
        }
    }

    public McpSchema.CallToolResult callTool(
            String toolName,
            Map<String, Object> arguments) {

        McpSyncClient client = createClient();

        try {

            log.info(
                    "Calling MCP tool: {} with arguments: {}",
                    toolName,
                    arguments
            );

            McpSchema.CallToolRequest request =
                    new McpSchema.CallToolRequest(
                            toolName,
                            arguments
                    );

            McpSchema.CallToolResult result =
                    client.callTool(request);

            log.info(
                    "MCP tool {} completed. Error={}",
                    toolName,
                    result.isError()
            );

            return result;

        } finally {

            client.close();
        }
    }
}