// File: android/zero/mcp/McpApplication.kt
package android.zero.mcp

import android.app.Application
import android.zero.mcp.handlers.CodeEditorProvider
import android.zero.mcp.protocol.JsonAdapters
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.utils.ApkSignVerifier
import com.itsaky.androidide.utils.FileUtil // Assuming this is your project's FileUtil

/**
 * Custom Application class for the MCP Server Host.
 * This class serves as a central point for initializing the [McpService]
 * and making it accessible globally within the application. It also registers
 * necessary mock/actual IDE services with the [Lookup] utility.
 *
 * @author Android Zero
 */
class McpApplication : Application() {

    // The singleton instance of McpService.
    private lateinit var mcpService: McpService

    override fun onCreate() {
        super.onCreate()
        LogManager.addLog("McpApplication created. Initializing...", "INFO", "McpApplication")

        // Initialize Lookup and register core IDE services.
        // These are assumed to be your actual IDE's implementations or mock for development.
        val lookup = Lookup.getDefault()

        // Register IProjectManager (assuming ProjectManagerImpl is the concrete implementation or a mock)
        // If your IProjectManager has a static getInstance() method, you can call it directly.
        // Otherwise, you might need to create an instance and register it.
        // For this example, we assume IProjectManager.getInstance() provides a functional singleton.
        lookup.register(IProjectManager::class.java, IProjectManager.getInstance())
        LogManager.addLog("McpApplication: Registered IProjectManager.", "INFO", "McpApplication")

        // Register BuildService (assuming it's a singleton or can be instantiated)
        // The BuildService mock/real implementation should handle its own registration if it's designed that way.
        // If not, you'd instantiate and register it here. For the previous mock, it registers itself.
        // We ensure its static initializer runs here.
        BuildService.Companion.KEY_BUILD_SERVICE // Accessing to ensure static init runs
        LogManager.addLog("McpApplication: Ensured BuildService registration.", "INFO", "McpApplication")

        // Initialize McpService. It receives the application context.
        mcpService = McpService(applicationContext)
        LogManager.addLog("McpApplication: McpService initialized.", "INFO", "McpApplication")
    }

    /**
     * Provides access to the singleton [McpService] instance.
     * @return The [McpService] instance.
     */
    fun getMcpService(): McpService {
        return mcpService
    }

    override fun onTerminate() {
        super.onTerminate()
        LogManager.addLog("McpApplication: Terminating. Destroying McpService...", "INFO", "McpApplication")
        mcpService.destroy()
        LogManager.addLog("McpApplication: Termination complete.", "INFO", "McpApplication")
    }
}
