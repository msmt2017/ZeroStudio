package me.rerere.rikkahub.ui.components.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import com.composables.icons.lucide.Boxes
import com.composables.icons.lucide.Hammer
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.HeartOff
import com.composables.icons.lucide.Lightbulb
import com.composables.icons.lucide.Lucide
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.theme.extendColors
import kotlin.uuid.Uuid

@Composable
fun ModelSelector(
    modelId: Uuid,
    providers: List<ProviderSetting>,
    type: ModelType,
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    onUpdate: (List<ProviderSetting>) -> Unit = {},
    onSelect: (Model) -> Unit = {}
) {
    var popup by remember {
        mutableStateOf(false)
    }
    val model = providers.findModelById(modelId)

    if (!onlyIcon) {
        TextButton(
            onClick = {
                popup = true
            },
            modifier = modifier
        ) {
            model?.modelId?.let {
                AutoAIIcon(
                    it, Modifier
                        .padding(end = 4.dp)
                        .size(24.dp)
                )
            }
            Text(
                text = model?.displayName ?: stringResource(R.string.model_list_select_model),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        }
    } else {
        IconButton(
            onClick = {
                popup = true
            }
        ) {
            if (model != null) {
                AutoAIIcon(
                    modifier = Modifier.size(20.dp),
                    name = model.modelId
                )
            } else {
                Icon(
                    Lucide.Boxes,
                    contentDescription = stringResource(R.string.setting_model_page_chat_model),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (popup) {
        ModalBottomSheet(
            onDismissRequest = {
                popup = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val filteredProviderSettings = providers.fastFilter {
                    it.enabled && it.models.fastAny { model -> model.type == type }
                }
                ModelList(
                    providers = filteredProviderSettings,
                    modelType = type,
                    onSelect = {
                        popup = false
                        onSelect(it)
                    },
                    onUpdate = { newModel ->
                        onUpdate(providers.map { provider ->
                            provider.copyProvider(
                                models = provider.models.map {
                                    if (it.id == newModel.id) {
                                        newModel
                                    } else {
                                        it
                                    }
                                }
                            )
                        })
                    }
                )
            }
        }
    }
}

@Composable
fun ModelList(
    providers: List<ProviderSetting>,
    modelType: ModelType,
    onUpdate: (Model) -> Unit,
    onSelect: (Model) -> Unit,
) {
    val favoriteModels = providers
        .flatMap { it.models }
        .fastFilter {
            it.favorite && it.type == modelType
        }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (providers.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.model_list_no_providers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendColors.gray6,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        if (favoriteModels.isNotEmpty()) {
            stickyHeader {
                Text(
                    text = stringResource(R.string.model_list_favorite),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(bottom = 4.dp, top = 8.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            items(
                items = favoriteModels,
                key = { "favorite:" + it.id.toString() }
            ) { model ->
                ModelItem(
                    model = model,
                    onSelect = onSelect,
                    modifier = Modifier.animateItem()
                ) {
                    IconButton(
                        onClick = {
                            onUpdate(
                                model.copy(
                                    favorite = !model.favorite
                                )
                            )
                        }
                    ) {
                        if (model.favorite) {
                            Icon(
                                Lucide.Heart,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.extendColors.red6
                            )
                        } else {
                            Icon(
                                Lucide.HeartOff,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        providers.fastForEach { providerSetting ->
            stickyHeader {
                Text(
                    text = providerSetting.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(bottom = 4.dp, top = 8.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            items(
                items = providerSetting.models.fastFilter { it.type == modelType },
                key = { it.id }
            ) { model ->
                ModelItem(
                    model = model,
                    onSelect = onSelect,
                    modifier = Modifier.animateItem()
                ) {
                    IconButton(
                        onClick = {
                            onUpdate(
                                model.copy(
                                    favorite = !model.favorite
                                )
                            )
                        }
                    ) {
                        if (model.favorite) {
                            Icon(
                                Lucide.Heart,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.extendColors.red6
                            )
                        } else {
                            Icon(
                                Lucide.Heart,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModelItem(
    model: Model,
    onSelect: (Model) -> Unit,
    modifier: Modifier = Modifier,
    tail: @Composable RowScope.() -> Unit = {}
) {
    OutlinedCard(
        onClick = { onSelect(model) },
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            AutoAIIcon(model.modelId, modifier = Modifier.size(32.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    model.modelId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.extendColors.gray4
                )
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.labelMedium,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Tag(type = TagType.INFO) {
                        Text(
                            when (model.type) {
                                ModelType.CHAT -> stringResource(R.string.model_list_chat)
                                ModelType.EMBEDDING -> stringResource(R.string.model_list_embedding)
                            }
                        )
                    }

                    Tag(type = TagType.SUCCESS) {
                        Text(
                            text = buildString {
                                append(model.inputModalities.joinToString(",") { it.name.lowercase() })
                                append("->")
                                append(model.outputModalities.joinToString(",") { it.name.lowercase() })
                            },
                            maxLines = 1,
                        )
                    }

                    val iconHeight = with(LocalDensity.current) {
                        LocalTextStyle.current.fontSize.toDp() * 0.9f
                    }
                    model.abilities.fastForEach { ability ->
                        when (ability) {
                            ModelAbility.TOOL -> {
                                Tag(
                                    type = TagType.WARNING,
                                    modifier = Modifier.fillMaxHeight()
                                ) {
                                    Icon(
                                        imageVector = Lucide.Hammer,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .height(iconHeight)
                                            .aspectRatio(1f)
                                    )
                                }
                            }

                            ModelAbility.REASONING -> {
                                Tag(
                                    type = TagType.INFO,
                                    modifier = Modifier.fillMaxHeight()
                                ) {
                                    Icon(
                                        imageVector = Lucide.Lightbulb,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .height(iconHeight)
                                            .aspectRatio(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            tail()
        }
    }
}
