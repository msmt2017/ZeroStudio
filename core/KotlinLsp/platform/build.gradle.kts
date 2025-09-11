plugins { id("java-platform") }

javaPlatform { allowDependencies() }

dependencies {
    constraints {
        api(ktlsp.org.jetbrains.kotlin.stdlib)
        api(ktlsp.hamcrest.all)
        api(ktlsp.junit.junit)
        api(ktlsp.org.eclipse.lsp4j.lsp4j)
        api(ktlsp.org.eclipse.lsp4j.jsonrpc)
        api(ktlsp.org.jetbrains.kotlin.compiler)

        api(ktlsp.org.jetbrains.kotlin.ktscompiler)
        api(ktlsp.org.jetbrains.kotlin.kts.jvm.host.unshaded)
        api(ktlsp.org.jetbrains.kotlin.sam.with.receiver.compiler.plugin)
        api(ktlsp.org.jetbrains.kotlin.reflect)
        api(ktlsp.org.jetbrains.kotlin.jvm)
        api(ktlsp.org.jetbrains.fernflower)
        api(ktlsp.org.jetbrains.exposed.core)
        api(ktlsp.org.jetbrains.exposed.dao)
        api(ktlsp.org.jetbrains.exposed.jdbc)
        api(ktlsp.com.h2database.h2)
        api(ktlsp.com.google.guava.guava)
        api(ktlsp.com.github.fwcd.ktfmt)
        api(ktlsp.com.beust.jcommander)
        api(ktlsp.org.openjdk.jmh.core)
        api(ktlsp.org.jetbrains.kotlin.kotlin.scripting.jvm.host)
        api(ktlsp.org.openjdk.jmh.generator.annprocess)
        api(ktlsp.org.xerial.sqlite.jdbc)
    }
}
