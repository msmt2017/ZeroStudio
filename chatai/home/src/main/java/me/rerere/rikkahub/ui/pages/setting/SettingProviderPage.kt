package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Boxes
import com.composables.icons.lucide.Cable
import com.composables.icons.lucide.CloudLightning
import com.composables.icons.lucide.GripHorizontal
import com.composables.icons.lucide.Hammer
import com.composables.icons.lucide.Import
import com.composables.icons.lucide.Lightbulb
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Share
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Zap
import com.dokar.sonner.ToastType
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.guessModalityFromModelId
import me.rerere.ai.provider.guessModelAbilityFromModelId
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.chat.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.ShareSheet
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.decodeProviderSetting
import me.rerere.rikkahub.ui.components.ui.rememberShareSheetState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConfigure
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SettingProviderPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.setting_provider_page_title))
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    ImportProviderButton {
                        vm.updateSettings(
                            settings.copy(
                                providers = settings.providers + it
                            )
                        )
                    }
                    AddButton {
                        vm.updateSettings(
                            settings.copy(
                                providers = settings.providers + it
                            )
                        )
                    }
                }
            )
        },
    ) { innerPadding ->
        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val newProviders = settings.providers.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            vm.updateSettings(settings.copy(providers = newProviders))
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            state = lazyListState
        ) {
            items(settings.providers, key = { it.id }) { provider ->
                ReorderableItem(
                    state = reorderableState,
                    key = provider.id
                ) { isDragging ->
                    ProviderItem(
                        modifier = Modifier
                            .scale(if (isDragging) 0.95f else 1f),
                        provider = provider,
                        onDelete = {
                            val newSettings = settings.copy(
                                providers = settings.providers - provider
                            )
                            vm.updateSettings(newSettings)
                        },
                        onEdit = { newProvider ->
                            val newSettings = settings.copy(
                                providers = settings.providers.map {
                                    if (newProvider.id == it.id) {
                                        newProvider
                                    } else {
                                        it
                                    }
                                }
                            )
                            vm.updateSettings(newSettings)
                        },
                        dragHandle = {
                            val haptic = LocalHapticFeedback.current
                            IconButton(
                                onClick = {},
                                modifier = Modifier
                                    .longPressDraggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        },
                                        onDragStopped = {
                                            haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                        }
                                    )
                            ) {
                                Icon(
                                    imageVector = Lucide.GripHorizontal,
                                    contentDescription = null
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportProviderButton(
    onAdd: (ProviderSetting) -> Unit
) {
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scanQrCodeLauncher = rememberLauncherForActivityResult(ScanQRCode()) { result ->
        // handle QRResult
        runCatching {
            when (result) {
                is QRResult.QRError -> {
                    toaster.show(
                        context.getString(
                            R.string.setting_provider_page_scan_error,
                            result
                        ), type = ToastType.Error
                    )
                }

                QRResult.QRMissingPermission -> {
                    toaster.show(
                        context.getString(R.string.setting_provider_page_no_permission),
                        type = ToastType.Error
                    )
                }

                is QRResult.QRSuccess -> {
                    val setting = decodeProviderSetting(result.content.rawValue ?: "")
                    onAdd(setting)
                    toaster.show(
                        context.getString(R.string.setting_provider_page_import_success),
                        type = ToastType.Success
                    )
                }

                QRResult.QRUserCanceled -> {}
            }
        }
    }
    IconButton(
        onClick = {
            scanQrCodeLauncher.launch(null)
        }
    ) {
        Icon(Lucide.Import, null)
    }
}

@Composable
private fun AddButton(onAdd: (ProviderSetting) -> Unit) {
    val dialogState = useEditState<ProviderSetting> {
        onAdd(it)
    }

    IconButton(
        onClick = {
            dialogState.open(ProviderSetting.OpenAI())
        }
    ) {
        Icon(Lucide.Plus, "Add")
    }

    if (dialogState.isEditing) {
        AlertDialog(
            onDismissRequest = {
                dialogState.dismiss()
            },
            title = {
                Text(stringResource(R.string.setting_provider_page_add_provider))
            },
            text = {
                dialogState.currentState?.let {
                    ProviderConfigure(it) { newState ->
                        dialogState.currentState = newState
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialogState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.setting_provider_page_add))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dialogState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}


private enum class ProviderExpandState {
    Setting,
    Models,
    None
}

@Composable
private fun ProviderItem(
    provider: ProviderSetting,
    modifier: Modifier = Modifier,
    dragHandle: @Composable () -> Unit,
    onEdit: (provider: ProviderSetting) -> Unit,
    onDelete: () -> Unit
) {
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 临时复制一份用于编辑, 因为data store是异步操作的，会导致UI编辑不同步
    var internalProvider by remember(provider) { mutableStateOf(provider) }

    var expand by remember { mutableStateOf(ProviderExpandState.None) }
    fun setExpand(state: ProviderExpandState) {
        expand = if (expand == state) {
            ProviderExpandState.None
        } else {
            state
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (provider.enabled) {
                MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
            } else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AutoAIIcon(
                    name = provider.name,
                    modifier = Modifier.size(32.dp)
                )
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Tag(type = if (provider.enabled) TagType.SUCCESS else TagType.WARNING) {
                            Text(stringResource(if (provider.enabled) R.string.setting_provider_page_enabled else R.string.setting_provider_page_disabled))
                        }
                        Tag(type = TagType.INFO) {
                            Text(
                                stringResource(
                                    R.string.setting_provider_page_model_count,
                                    provider.models.size
                                )
                            )
                        }
                    }
                }
                dragHandle()
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                val shareSheetState = rememberShareSheetState()
                ShareSheet(shareSheetState)
                TextButton(
                    onClick = {
                        shareSheetState.show(provider)
                    },
                ) {
                    Icon(
                        imageVector = Lucide.Share,
                        contentDescription = "Share",
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp)
                    )
                    Text(stringResource(R.string.setting_provider_page_share))
                }
                TextButton(
                    onClick = {
                        setExpand(ProviderExpandState.Models)
                    },
                ) {
                    Icon(
                        imageVector = Lucide.Boxes,
                        contentDescription = "Models",
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp)
                    )
                    Text(stringResource(R.string.setting_provider_page_models))
                }
                TextButton(
                    onClick = {
                        setExpand(ProviderExpandState.Setting)
                    }
                ) {
                    Icon(
                        imageVector = Lucide.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp)
                    )
                    Text(stringResource(R.string.setting_provider_page_configuration))
                }
            }

            if (expand == ProviderExpandState.Models) {
                ModelList(provider) {
                    onEdit(it)
                }
            }

            if (expand == ProviderExpandState.Setting) {
                ProviderConfigure(
                    provider = internalProvider,
                    modifier = Modifier.padding(8.dp),
                    onEdit = {
                        internalProvider = it
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConnectionTester(
                        provider = provider,
                        internalProvider = internalProvider,
                        scope = scope
                    )

                    Spacer(Modifier.weight(1f))

                    IconButton(
                        onClick = {
                            onDelete()
                        },
                        enabled = !internalProvider.builtIn
                    ) {
                        Icon(Lucide.Trash2, "Delete")
                    }

                    Button(
                        onClick = {
                            onEdit(internalProvider)
                            toaster.show(
                                context.getString(R.string.setting_provider_page_save_success),
                                type = ToastType.Success
                            )
                            expand = ProviderExpandState.None
                        }
                    ) {
                        Text(
                            stringResource(R.string.setting_provider_page_save),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionTester(
    provider: ProviderSetting,
    internalProvider: ProviderSetting,
    scope: CoroutineScope
) {
    var provider1 = provider
    var showTestDialog by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            showTestDialog = true
        }
    ) {
        Icon(Lucide.Cable, null)
    }
    if (showTestDialog) {
        var model by remember { mutableStateOf<Model?>(null) }
        var testState: UiState<String> by remember { mutableStateOf(UiState.Idle) }
        AlertDialog(
            onDismissRequest = { showTestDialog = false },
            title = {
                Text(stringResource(R.string.setting_provider_page_test_connection))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ModelSelector(
                        modelId = model?.id,
                        providers = listOf(provider1),
                        type = ModelType.CHAT,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        model = it
                    }
                    when (testState) {
                        is UiState.Loading -> {
                            LinearWavyProgressIndicator()
                        }

                        is UiState.Success -> {
                            Text(
                                text = stringResource(R.string.setting_provider_page_test_success),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.extendColors.green6
                            )
                        }

                        is UiState.Error -> {
                            Text(
                                text = (testState as UiState.Error).error.message ?: "Error",
                                color = MaterialTheme.extendColors.red6,
                                maxLines = 10
                            )
                        }

                        else -> {}
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showTestDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (model == null) return@TextButton
                        val provider = ProviderManager.getProviderByType(internalProvider)
                        scope.launch {
                            runCatching {
                                testState = UiState.Loading
                                provider.generateText(
                                    providerSetting = internalProvider,
                                    messages = listOf(
                                        UIMessage.user("hello")
                                    ),
                                    params = TextGenerationParams(
                                        model = model!!,
                                    )
                                )
                                testState = UiState.Success("Success")
                            }.onFailure {
                                testState = UiState.Error(it)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.setting_provider_page_test))
                }
            }
        )
    }
}

@Composable
private fun ModelList(
    providerSetting: ProviderSetting,
    onUpdateProvider: (ProviderSetting) -> Unit
) {
    val modelList by produceState(emptyList(), providerSetting) {
        runCatching {
            println("loading models...")
            value = ProviderManager.getProviderByType(providerSetting)
                .listModels(providerSetting)
                .sortedBy { it.modelId }
                .toList()
            // println(value)
        }.onFailure {
            it.printStackTrace()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 模型列表
        if (providerSetting.models.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.setting_provider_page_no_models),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.setting_provider_page_add_models_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        } else {
            ReorderableColumn(
                list = providerSetting.models,
                onSettle = { fromIndex, toIndex ->
                    onUpdateProvider(providerSetting.moveMove(fromIndex, toIndex))
                },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) { index, item, isDragging ->
                key(item.id) {
                    ModelCard(
                        model = item,
                        onDelete = {
                            onUpdateProvider(providerSetting.delModel(item))
                        },
                        onEdit = { editedModel ->
                            onUpdateProvider(providerSetting.editModel(editedModel))
                        },
                        modifier = Modifier
                            .longPressDraggableHandle()
                            .scale(if (isDragging) 0.95f else 1f)
                    )
                }
            }
        }
        AddModelButton(
            models = modelList,
            selectedModels = providerSetting.models,
            onAddModel = {
                onUpdateProvider(providerSetting.addModel(it))
            },
            onRemoveModel = {
                onUpdateProvider(providerSetting.delModel(it))
            }
        )
    }
}

@Composable
private fun AddModelButton(
    models: List<Model>,
    selectedModels: List<Model>,
    onAddModel: (Model) -> Unit,
    onRemoveModel: (Model) -> Unit
) {
    val dialogState = useEditState<Model> { onAddModel(it) }

    fun setModelId(id: String) {
        val modality = guessModalityFromModelId(id.lowercase())
        val abilities = guessModelAbilityFromModelId(id.lowercase())
        dialogState.currentState = dialogState.currentState?.copy(
            modelId = id,
            displayName = id.uppercase(),
            inputModalities = modality.first,
            outputModalities = modality.second,
            abilities = abilities
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelPicker(
            models = models,
            selectedModels = selectedModels,
            onModelSelected = { model ->
                val modality = guessModalityFromModelId(model.modelId)
                val abilities = guessModelAbilityFromModelId(model.modelId)
                onAddModel(
                    model.copy(
                        inputModalities = modality.first,
                        outputModalities = modality.second,
                        abilities = abilities
                    )
                )
            },
            onModelDeselected = { model ->
                onRemoveModel(model)
            }
        )

        Button(
            modifier = Modifier.weight(1f),
            onClick = {
                dialogState.open(Model())
            }
        ) {
            Row(
                modifier = Modifier,
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Lucide.Plus,
                    contentDescription = stringResource(R.string.setting_provider_page_add_model)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    stringResource(R.string.setting_provider_page_add_new_model),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    if (dialogState.isEditing) {
        dialogState.currentState?.let { modelState ->
            AlertDialog(
                onDismissRequest = {
                    dialogState.dismiss()
                },
                title = {
                    Text(stringResource(R.string.setting_provider_page_add_model))
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = modelState.modelId,
                            onValueChange = {
                                setModelId(it.trim())
                            },
                            label = { Text(stringResource(R.string.setting_provider_page_model_id)) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(stringResource(R.string.setting_provider_page_model_id_placeholder))
                            },
                        )

                        OutlinedTextField(
                            value = modelState.displayName,
                            onValueChange = {
                                dialogState.currentState = dialogState.currentState?.copy(
                                    displayName = it.trim()
                                )
                            },
                            label = { Text(stringResource(R.string.setting_provider_page_model_display_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(stringResource(R.string.setting_provider_page_model_display_name_placeholder))
                            }
                        )


                        ModelTypeSelector(
                            selectedType = modelState.type,
                            onTypeSelected = {
                                dialogState.currentState = dialogState.currentState?.copy(
                                    type = it
                                )
                            }
                        )

                        ModelModalitySelector(
                            inputModalities = modelState.inputModalities,
                            onUpdateInputModalities = {
                                dialogState.currentState = dialogState.currentState?.copy(
                                    inputModalities = it
                                )
                            },
                            outputModalities = modelState.outputModalities,
                            onUpdateOutputModalities = {
                                dialogState.currentState = dialogState.currentState?.copy(
                                    outputModalities = it
                                )
                            }
                        )

                        ModalAbilitySelector(
                            abilities = modelState.abilities,
                            onUpdateAbilities = {
                                dialogState.currentState = dialogState.currentState?.copy(
                                    abilities = it
                                )
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (modelState.modelId.isNotBlank() && modelState.displayName.isNotBlank()) {
                                dialogState.confirm()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.setting_provider_page_add))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            dialogState.dismiss()
                        },
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun ModelPicker(
    models: List<Model>,
    selectedModels: List<Model>,
    onModelSelected: (Model) -> Unit,
    onModelDeselected: (Model) -> Unit
) {
    var showModal by remember { mutableStateOf(false) }
    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = { showModal = false },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            )
        ) {
            var filterText by remember { mutableStateOf("") }
            val filterKeywords = filterText.split(" ").filter { it.isNotBlank() }
            val filteredModels = models.fastFilter {
                if (filterKeywords.isEmpty()) {
                    true
                } else {
                    filterKeywords.all { keyword ->
                        it.modelId.contains(keyword, ignoreCase = true) ||
                                it.displayName.contains(keyword, ignoreCase = true)
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .padding(8.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    items(filteredModels) {
                        Card {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(
                                    8.dp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                            ) {
                                AutoAIIcon(
                                    it.modelId,
                                    Modifier.size(32.dp)
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(
                                        4.dp
                                    ),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = it.modelId,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (selectedModels.any { model -> model.modelId == it.modelId }) {
                                            // 从selectedModels中计算出要删除的model，因为删除需要id匹配，而不是ModelId
                                            onModelDeselected(selectedModels.firstOrNull { model -> model.modelId == it.modelId }
                                                ?: it)
                                        } else {
                                            onModelSelected(it)
                                        }
                                    }
                                ) {
                                    if (selectedModels.any { model -> model.modelId == it.modelId }) {
                                        Icon(Lucide.X, null)
                                    } else {
                                        Icon(Lucide.Plus, null)
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = filterText,
                    onValueChange = {
                        filterText = it
                    },
                    label = { Text(stringResource(R.string.setting_provider_page_filter_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(stringResource(R.string.setting_provider_page_filter_example))
                    },
                )
            }
        }
    }
    BadgedBox(
        badge = {
            if (models.isNotEmpty()) {
                Badge {
                    Text(models.size.toString())
                }
            }
        }
    ) {
        IconButton(
            onClick = {
                showModal = true
            }
        ) {
            Icon(Lucide.Boxes, null)
        }
    }
}

@Composable
private fun ModelTypeSelector(
    selectedType: ModelType,
    onTypeSelected: (ModelType) -> Unit
) {
    Text(
        stringResource(R.string.setting_provider_page_model_type),
        style = MaterialTheme.typography.titleSmall
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth()
    ) {
        ModelType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index, ModelType.entries.size),
                label = {
                    Text(
                        text = stringResource(
                            when (type) {
                                ModelType.CHAT -> R.string.setting_provider_page_chat_model
                                ModelType.EMBEDDING -> R.string.setting_provider_page_embedding_model
                            }
                        )
                    )
                },
                selected = selectedType == type,
                onClick = { onTypeSelected(type) }
            )
        }
    }
}

@Composable
private fun ModelModalitySelector(
    inputModalities: List<Modality>,
    onUpdateInputModalities: (List<Modality>) -> Unit,
    outputModalities: List<Modality>,
    onUpdateOutputModalities: (List<Modality>) -> Unit
) {
    Text(
        stringResource(R.string.setting_provider_page_input_modality),
        style = MaterialTheme.typography.titleSmall
    )
    MultiChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Modality.entries.forEachIndexed { index, modality ->
            SegmentedButton(
                checked = modality in inputModalities,
                shape = SegmentedButtonDefaults.itemShape(index, Modality.entries.size),
                onCheckedChange = {
                    if (it) {
                        onUpdateInputModalities(inputModalities + modality)
                    } else {
                        onUpdateInputModalities(inputModalities - modality)
                    }
                }
            ) {
                Text(
                    text = stringResource(
                        when (modality) {
                            Modality.TEXT -> R.string.setting_provider_page_text
                            Modality.IMAGE -> R.string.setting_provider_page_image
                        }
                    )
                )
            }
        }
    }
    Text(
        stringResource(R.string.setting_provider_page_output_modality),
        style = MaterialTheme.typography.titleSmall
    )
    MultiChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Modality.entries.forEachIndexed { index, modality ->
            SegmentedButton(
                checked = modality in outputModalities,
                shape = SegmentedButtonDefaults.itemShape(index, Modality.entries.size),
                onCheckedChange = {
                    if (it) {
                        onUpdateOutputModalities(outputModalities + modality)
                    } else {
                        onUpdateOutputModalities(outputModalities - modality)
                    }
                }
            ) {
                Text(
                    text = stringResource(
                        when (modality) {
                            Modality.TEXT -> R.string.setting_provider_page_text
                            Modality.IMAGE -> R.string.setting_provider_page_image
                        }
                    )
                )
            }
        }
    }
}

@Composable
fun ModalAbilitySelector(
    abilities: List<ModelAbility>,
    onUpdateAbilities: (List<ModelAbility>) -> Unit
) {
    Text(
        stringResource(R.string.setting_provider_page_abilities),
        style = MaterialTheme.typography.titleSmall
    )
    MultiChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        ModelAbility.entries.forEachIndexed { index, ability ->
            SegmentedButton(
                checked = ability in abilities,
                shape = SegmentedButtonDefaults.itemShape(index, ModelAbility.entries.size),
                onCheckedChange = {
                    if (it) {
                        onUpdateAbilities(abilities + ability)
                    } else {
                        onUpdateAbilities(abilities - ability)
                    }
                },
                label = {
                    Text(
                        text = stringResource(
                            when (ability) {
                                ModelAbility.TOOL -> R.string.setting_provider_page_tool
                                ModelAbility.REASONING -> R.string.setting_provider_page_reasoning
                            }
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun ModelCard(
    model: Model,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onEdit: (Model) -> Unit
) {
    val dialogState = useEditState<Model> {
        onEdit(it)
    }
    val swipeToDismissBoxState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()


    dialogState.EditStateContent { editingModel, updateEditingModel ->
        AlertDialog(
            onDismissRequest = { dialogState.dismiss() },
            title = {
                Text(stringResource(R.string.setting_provider_page_edit_model))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editingModel.modelId,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.setting_provider_page_model_id)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    )

                    OutlinedTextField(
                        value = editingModel.displayName,
                        onValueChange = {
                            updateEditingModel(editingModel.copy(displayName = it.trim()))
                        },
                        label = { Text(stringResource(R.string.setting_provider_page_model_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    ModelTypeSelector(
                        selectedType = editingModel.type,
                        onTypeSelected = {
                            updateEditingModel(editingModel.copy(type = it))
                        }
                    )
                    ModelModalitySelector(
                        inputModalities = editingModel.inputModalities,
                        onUpdateInputModalities = {
                            updateEditingModel(editingModel.copy(inputModalities = it))
                        },
                        outputModalities = editingModel.outputModalities,
                        onUpdateOutputModalities = {
                            updateEditingModel(editingModel.copy(outputModalities = it))
                        }
                    )
                    ModalAbilitySelector(
                        abilities = editingModel.abilities,
                        onUpdateAbilities = {
                            updateEditingModel(editingModel.copy(abilities = it))
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editingModel.displayName.isNotBlank()) {
                            dialogState.confirm()
                        }
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dialogState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    SwipeToDismissBox(
        state = swipeToDismissBoxState,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        scope.launch {
                            swipeToDismissBoxState.reset()
                        }
                    }
                ) {
                    Icon(Lucide.X, null)
                }
                FilledIconButton(
                    onClick = {
                        scope.launch {
                            onDelete()
                            swipeToDismissBoxState.reset()
                        }
                    }
                ) {
                    Icon(
                        Lucide.Trash2,
                        contentDescription = stringResource(R.string.chat_page_delete)
                    )
                }
            }
        },
        enableDismissFromStartToEnd = false,
        gesturesEnabled = true,
        modifier = modifier
    ) {
        OutlinedCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AutoAIIcon(
                    name = model.modelId,
                    modifier = Modifier.size(28.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.modelId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Tag(
                            type = TagType.INFO
                        ) {
                            Text(
                                text = stringResource(
                                    when (model.type) {
                                        ModelType.CHAT -> R.string.setting_provider_page_chat_model
                                        ModelType.EMBEDDING -> R.string.setting_provider_page_embedding_model
                                    }
                                )
                            )
                        }
                        Tag(
                            type = TagType.SUCCESS
                        ) {
                            Text(
                                text = buildString {
                                    append(model.inputModalities.joinToString(",") { it.name.lowercase() })
                                    append("->")
                                    append(model.outputModalities.joinToString(",") { it.name.lowercase() })
                                },
                                maxLines = 1,
                            )
                        }
                        model.abilities.fastForEach { ability ->
                            when (ability) {
                                ModelAbility.TOOL -> {
                                    Tag(
                                        type = TagType.WARNING
                                    ) {
                                        Icon(Lucide.Hammer, null, modifier = Modifier.size(14.dp))
                                    }
                                }

                                ModelAbility.REASONING -> {
                                    Tag(
                                        type = TagType.INFO
                                    ) {
                                        Icon(
                                            Lucide.Lightbulb,
                                            null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Edit button
                IconButton(
                    onClick = {
                        dialogState.open(model.copy())
                    }
                ) {
                    Icon(Lucide.Pencil, "Edit")
                }
            }
        }
    }
}