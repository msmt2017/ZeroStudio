package org.javacs.kt

class DefaultLanguageServerRegistry : ILanguageServerRegistry() {
    private val mRegister = mutableMapOf<String, ILanguageServer>()
    override fun register(server: ILanguageServer) {
        val old = mRegister.put(server.serverId ?: return, server)
        if (old != null) {
            mRegister[old.serverId!!] = old
        }
    }
    override fun connectClient(client: ILanguageClient) {
        mRegister.values.forEach { it.connectClient(client) }
    }
    override fun unregister(serverId: String) {
        val registered = mRegister.remove(serverId)
        if (registered == null) {
            throw IllegalStateException("No server found for the given server ID")
        }
    }
    override fun destroy() {
        mRegister.values.forEach { it.shutdown() }
        mRegister.clear()
    }
    override fun getServer(serverId: String): ILanguageServer? {
        return mRegister[serverId]
    }
} 