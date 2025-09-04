
package com.itsaky.androidide.tooling.impl.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.Configurator
import ch.qos.logback.classic.spi.ConfiguratorRank
import ch.qos.logback.core.spi.ContextAwareBase
import com.google.auto.service.AutoService
import com.itsaky.androidide.logging.JvmStdErrAppender
import com.itsaky.androidide.logging.encoder.IDELogFormatEncoder

/**
 * Default logging configurator for the Tooling API Runtime.
 *
 * @author Akash Yadav
 */
@ConfiguratorRank(ConfiguratorRank.CUSTOM_TOP_PRIORITY)
@AutoService(Configurator::class)
@Suppress("UNUSED")
class ToolingLoggingConfigurator : ContextAwareBase(), Configurator {

  override fun configure(context: LoggerContext): Configurator.ExecutionStatus {
    addInfo("Setting up logging configuration for tooling API")

    val stdErrAppender = JvmStdErrAppender()
    stdErrAppender.encoder = IDELogFormatEncoder()
    stdErrAppender.start()

    val toolingApiAppender = ToolingApiAppender()
    toolingApiAppender.start()

    val rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME)
    rootLogger.addAppender(stdErrAppender)
    rootLogger.addAppender(toolingApiAppender)

    return Configurator.ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY
  }
}