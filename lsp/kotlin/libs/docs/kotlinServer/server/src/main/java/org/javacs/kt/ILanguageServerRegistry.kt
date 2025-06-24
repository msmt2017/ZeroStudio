package org.javacs.kt

abstract class ILanguageServerRegistry {
    companion object {
        private var sRegistry: ILanguageServerRegistry? = null
        fun getDefault(): ILanguageServerRegistry {
            if (sRegistry == null) {
                sRegistry = DefaultLanguageServerRegistry()
            }
            return sRegistry!!
        }
    }
    abstract fun register(server: ILanguageServer)
    abstract fun connectClient(client: ILanguageClient)
    abstract fun unregister(serverId: String)
    abstract fun destroy()
    abstract fun getServer(serverId: String): ILanguageServer?
} 