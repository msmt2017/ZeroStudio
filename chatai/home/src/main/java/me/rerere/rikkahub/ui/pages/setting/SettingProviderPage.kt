package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedButton
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
import android.net.Uri
import com.composables.icons.lucide.Boxes
import com.composables.icons.lucide.Cable
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.GripHorizontal
import com.composables.icons.lucide.Hammer
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Import
import com.composables.icons.lucide.Lightbulb
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Share
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import com.dokar.sonner.ToastType
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConfigure
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.utils.ImageUtils
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SettingProviderPage(vm: SettingVM = koinViewModel()) {
  val settings by vm.settings.collectAsStateWithLifecycle()
  val navController = LocalNavController.current
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
                providers = listOf(it) + settings.providers
              )
            )
          }
          AddButton {
            vm.updateSettings(
              settings.copy(
                providers = listOf(it) + settings.providers
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
            onClick = {
              navController.navigate("setting/provider/${provider.id}")
            }
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
  var showImportDialog by remember { mutableStateOf(false) }

  val scanQrCodeLauncher = rememberLauncherForActivityResult(ScanQRCode()) { result ->
    handleQRResult(result, onAdd, toaster, context)
  }

  val pickImageLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    uri?.let {
      handleImageQRCode(it, onAdd, toaster, context)
    }
  }

  IconButton(
    onClick = {
      showImportDialog = true
    }
  ) {
    Icon(Lucide.Import, null)
  }

  if (showImportDialog) {
    AlertDialog(
      onDismissRequest = { showImportDialog = false },
      title = {
        Text(
          text = stringResource(R.string.setting_provider_page_import_dialog_title),
          style = MaterialTheme.typography.headlineSmall
        )
      },
      text = {
        Column(
          verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
          Text(
            text = stringResource(R.string.setting_provider_page_import_dialog_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            // 主要操作：扫描二维码
            Button(
              onClick = {
                showImportDialog = false
                scanQrCodeLauncher.launch(null)
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
              shape = MaterialTheme.shapes.large
            ) {
              Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
              ) {
                Icon(
                  imageVector = Lucide.Camera,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                  text = stringResource(R.string.setting_provider_page_scan_qr_code),
                  style = MaterialTheme.typography.labelLarge
                )
              }
            }

            // 次要操作：从相册选择
            OutlinedButton(
              onClick = {
                showImportDialog = false
                pickImageLauncher.launch(
                  androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                  )
                )
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
              shape = MaterialTheme.shapes.large
            ) {
              Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
              ) {
                Icon(
                  imageVector = Lucide.Image,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                  text = stringResource(R.string.setting_provider_page_select_from_gallery),
                  style = MaterialTheme.typography.labelLarge
                )
              }
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(
          onClick = { showImportDialog = false },
          shape = MaterialTheme.shapes.large
        ) {
          Text(
            text = stringResource(R.string.cancel),
            style = MaterialTheme.typography.labelLarge
          )
        }
      }
    )
  }
}

private fun handleQRResult(
  result: QRResult,
  onAdd: (ProviderSetting) -> Unit,
  toaster: com.dokar.sonner.ToasterState,
  context: android.content.Context
) {
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
  }.onFailure { error ->
    toaster.show(
      context.getString(R.string.setting_provider_page_qr_decode_failed, error.message ?: ""),
      type = ToastType.Error
    )
  }
}

private fun handleImageQRCode(
  uri: Uri,
  onAdd: (ProviderSetting) -> Unit,
  toaster: com.dokar.sonner.ToasterState,
  context: android.content.Context
) {
  runCatching {
    // 使用ImageUtils解析二维码
    val qrContent = ImageUtils.decodeQRCodeFromUri(context, uri)

    if (qrContent.isNullOrEmpty()) {
      toaster.show(
        context.getString(R.string.setting_provider_page_no_qr_found),
        type = ToastType.Error
      )
      return
    }

    val setting = decodeProviderSetting(qrContent)
    onAdd(setting)
    toaster.show(
      context.getString(R.string.setting_provider_page_import_success),
      type = ToastType.Success
    )
  }.onFailure { error ->
    toaster.show(
      context.getString(R.string.setting_provider_page_image_qr_decode_failed, error.message ?: ""),
      type = ToastType.Error
    )
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

@Composable
private fun ProviderItem(
  provider: ProviderSetting,
  modifier: Modifier = Modifier,
  dragHandle: @Composable () -> Unit,
  onClick: () -> Unit
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(
      containerColor = if (provider.enabled) {
        MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
      } else MaterialTheme.colorScheme.errorContainer,
    ),
    onClick = {
      onClick()
    }
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
    }
  }
}