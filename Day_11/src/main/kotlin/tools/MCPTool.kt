package tools

interface MCPTool {
    val name: String
    val description: String
    fun execute(arguments: Map<String, Any>): String
}