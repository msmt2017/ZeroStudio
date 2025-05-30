package me.rerere.ai.ui.transformers

import android.content.Context
import android.os.Build
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.InputMessageTransformer
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.Temporal
import java.util.Locale
import java.util.TimeZone

object PlaceholderTransformer : InputMessageTransformer {
    val Placeholders = mapOf(
        "{cur_date}" to "日期",
        "{cur_time}" to "时间",
        "{cur_datetime}" to "日期和时间",
        "{model_id}" to "模型ID",
        "{model_name}" to "模型名称",
        "{locale}" to "语言环境",
        "{timezone}" to "时区",
        "{system_version}" to "系统版本",
        "{device_info}" to "设备信息",
        "{battery_level}" to "电池电量"
    )

    override suspend fun transform(context: Context, messages: List<UIMessage>, model: Model): List<UIMessage> {
        return messages.map {
            it.copy(
                parts = it.parts.map { part ->
                    if (part is UIMessagePart.Text) {
                        part.copy(
                            text = part.text
                                .replace("{cur_date}", LocalDate.now().toDateString())
                                .replace("{cur_time}", LocalTime.now().toTimeString())
                                .replace("{cur_datetime}", LocalDateTime.now().toDateTimeString())
                                .replace("{model_id}", model.modelId)
                                .replace("{model_name}", model.displayName)
                                .replace("{locale}", Locale.getDefault().displayName)
                                .replace("{timezone}", TimeZone.getDefault().displayName)
                                .replace(
                                    "{system_version}",
                                    "Android SDK v${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE}"
                                )
                                .replace("{device_info}", "${Build.BRAND} ${Build.MODEL}")
                                .replace("{battery_level}", context.batteryLevel().toString())
                        )
                    } else {
                        part
                    }
                }
            )
        }
    }

    private fun Temporal.toDateString() = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(this)

    private fun Temporal.toTimeString() = DateTimeFormatter
        .ofLocalizedTime(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(this)

    private fun Temporal.toDateTimeString() = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(this)

    private fun Context.batteryLevel(): Int {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        return batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}