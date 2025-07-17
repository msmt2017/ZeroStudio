# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile



# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn com.github.appintro.AppIntro2
-dontwarn com.github.appintro.SlidePolicy
-dontwarn com.itsaky.androidide.editor.api.IEditor
-dontwarn com.itsaky.androidide.editor.api.ILspEditor
-dontwarn com.itsaky.androidide.eventbus.events.Event
-dontwarn com.itsaky.androidide.eventbus.events.EventReceiver
-dontwarn com.itsaky.androidide.eventbus.events.editor.ChangeType
-dontwarn com.itsaky.androidide.eventbus.events.editor.ColorSchemeInvalidatedEvent
-dontwarn com.itsaky.androidide.eventbus.events.editor.DocumentChangeEvent
-dontwarn com.itsaky.androidide.eventbus.events.editor.DocumentCloseEvent
-dontwarn com.itsaky.androidide.eventbus.events.editor.DocumentEvent
-dontwarn com.itsaky.androidide.eventbus.events.editor.DocumentOpenEvent
-dontwarn com.itsaky.androidide.eventbus.events.editor.DocumentSaveEvent
-dontwarn com.itsaky.androidide.eventbus.events.editor.DocumentSelectedEvent
-dontwarn com.itsaky.androidide.eventbus.events.editor.OnCreateEvent
-dontwarn com.itsaky.androidide.eventbus.events.editor.OnDestroyEvent
-dontwarn com.itsaky.androidide.eventbus.events.editor.OnPauseEvent
-dontwarn com.itsaky.androidide.eventbus.events.editor.OnResumeEvent
-dontwarn com.itsaky.androidide.eventbus.events.editor.OnStartEvent
-dontwarn com.itsaky.androidide.eventbus.events.editor.OnStopEvent
-dontwarn com.itsaky.androidide.eventbus.events.file.FileCreationEvent
-dontwarn com.itsaky.androidide.eventbus.events.file.FileDeletionEvent
-dontwarn com.itsaky.androidide.eventbus.events.file.FileRenameEvent
-dontwarn com.itsaky.androidide.eventbus.events.filetree.FileClickEvent
-dontwarn com.itsaky.androidide.eventbus.events.filetree.FileLongClickEvent
-dontwarn com.itsaky.androidide.eventbus.events.preferences.PreferenceChangeEvent
-dontwarn com.itsaky.androidide.events.LspApiEventsIndex
-dontwarn com.itsaky.androidide.events.LspJavaEventsIndex
-dontwarn com.itsaky.androidide.lsp.api.ICompletionProvider
-dontwarn com.itsaky.androidide.lsp.api.ILanguageClient
-dontwarn com.itsaky.androidide.lsp.api.ILanguageServer
-dontwarn com.itsaky.androidide.lsp.api.ILanguageServerRegistry
-dontwarn com.itsaky.androidide.lsp.api.IServerSettings
-dontwarn com.itsaky.androidide.lsp.java.JavaLanguageServer
-dontwarn com.itsaky.androidide.lsp.java.utils.CancelChecker$Companion
-dontwarn com.itsaky.androidide.lsp.java.utils.CancelChecker
-dontwarn com.itsaky.androidide.lsp.models.ClassCompletionData
-dontwarn com.itsaky.androidide.lsp.models.CodeFormatResult
-dontwarn com.itsaky.androidide.lsp.models.CompletionItem
-dontwarn com.itsaky.androidide.lsp.models.CompletionItemKind
-dontwarn com.itsaky.androidide.lsp.models.CompletionParams
-dontwarn com.itsaky.androidide.lsp.models.CompletionResult
-dontwarn com.itsaky.androidide.lsp.models.DiagnosticItem
-dontwarn com.itsaky.androidide.lsp.models.DiagnosticSeverity
-dontwarn com.itsaky.androidide.lsp.models.ExpandSelectionParams
-dontwarn com.itsaky.androidide.lsp.models.FailureType
-dontwarn com.itsaky.androidide.lsp.models.FormatCodeParams
-dontwarn com.itsaky.androidide.lsp.models.ICompletionData
-dontwarn com.itsaky.androidide.lsp.models.IndexedTextEdit
-dontwarn com.itsaky.androidide.lsp.models.InsertTextFormat
-dontwarn com.itsaky.androidide.lsp.models.LSPFailure
-dontwarn com.itsaky.androidide.lsp.models.MatchLevel
-dontwarn com.itsaky.androidide.lsp.models.MemberCompletionData
-dontwarn com.itsaky.androidide.lsp.models.MethodCompletionData
-dontwarn com.itsaky.androidide.lsp.models.ParameterInformation
-dontwarn com.itsaky.androidide.lsp.models.SignatureHelp
-dontwarn com.itsaky.androidide.lsp.models.SignatureHelpParams
-dontwarn com.itsaky.androidide.lsp.models.SignatureInformation
-dontwarn com.itsaky.androidide.lsp.models.TextEdit
-dontwarn com.itsaky.androidide.lsp.util.CommonCompletionUtilsKt
-dontwarn com.itsaky.androidide.lsp.util.DocumentationReferenceProvider
-dontwarn com.itsaky.androidide.lsp.xml.XMLLanguageServer
-dontwarn com.itsaky.androidide.lsp.xml.models.XMLServerSettings
-dontwarn com.itsaky.androidide.lsp.xml.providers.XmlCompletionProvider
-dontwarn com.itsaky.androidide.lsp.xml.providers.completion.AttrValueCompletionProvider
-dontwarn com.itsaky.androidide.lsp.xml.utils.XMLBuilder
-dontwarn com.itsaky.androidide.templates.ITemplateProvider$Companion
-dontwarn com.itsaky.androidide.templates.ITemplateProvider
-dontwarn com.itsaky.androidide.templates.ITemplateWidgetViewProvider$Companion
-dontwarn com.itsaky.androidide.templates.ITemplateWidgetViewProvider
-dontwarn com.itsaky.androidide.templates.Parameter
-dontwarn com.itsaky.androidide.templates.ProjectTemplate
-dontwarn com.itsaky.androidide.templates.ProjectTemplateData
-dontwarn com.itsaky.androidide.templates.ProjectTemplateRecipeResult
-dontwarn com.itsaky.androidide.templates.RecipeExecutor
-dontwarn com.itsaky.androidide.templates.StringParameter
-dontwarn com.itsaky.androidide.templates.Template$Companion
-dontwarn com.itsaky.androidide.templates.Template
-dontwarn com.itsaky.androidide.templates.TemplateData
-dontwarn com.itsaky.androidide.templates.TemplateRecipe
-dontwarn com.itsaky.androidide.templates.TemplateRecipeResult
-dontwarn com.itsaky.androidide.templates.Widget
-dontwarn com.itsaky.androidide.templates.impl.ConstraintVerifier
-dontwarn com.itsaky.androidide.templates.impl.TemplateProviderImpl
-dontwarn com.itsaky.androidide.templates.impl.TemplateWidgetViewProviderImpl
-dontwarn com.itsaky.androidide.xml.internal.resources.DefaultResourceTableRegistry
-dontwarn com.itsaky.androidide.xml.internal.versions.DefaultApiVersionsRegistry
-dontwarn com.itsaky.androidide.xml.internal.widgets.DefaultWidgetTableRegistry
-dontwarn com.itsaky.androidide.xml.resources.ResourceTableRegistry$Companion
-dontwarn com.itsaky.androidide.xml.resources.ResourceTableRegistry
-dontwarn com.itsaky.androidide.xml.versions.ApiVersions$Companion
-dontwarn com.itsaky.androidide.xml.versions.ApiVersions
-dontwarn com.itsaky.androidide.xml.versions.ApiVersionsRegistry$Companion
-dontwarn com.itsaky.androidide.xml.versions.ApiVersionsRegistry
-dontwarn com.itsaky.androidide.xml.versions.ClassInfo
-dontwarn com.itsaky.androidide.xml.versions.FieldInfo
-dontwarn com.itsaky.androidide.xml.versions.Info
-dontwarn com.itsaky.androidide.xml.versions.MethodInfo
-dontwarn com.itsaky.androidide.xml.widgets.Widget
-dontwarn com.itsaky.androidide.xml.widgets.WidgetTable
-dontwarn com.itsaky.androidide.xml.widgets.WidgetTableRegistry$Companion
-dontwarn com.itsaky.androidide.xml.widgets.WidgetTableRegistry
-dontwarn com.itsaky.androidide.xml.widgets.WidgetType
-dontwarn com.unnamed.b.atv.model.TreeNode$BaseNodeViewHolder
-dontwarn com.unnamed.b.atv.model.TreeNode$TreeNodeClickListener
-dontwarn com.unnamed.b.atv.model.TreeNode$TreeNodeLongClickListener
-dontwarn com.unnamed.b.atv.model.TreeNode
-dontwarn com.unnamed.b.atv.view.AndroidTreeView
-dontwarn io.github.rosemoe.sora.editor.ts.LocalsCaptureSpec$Companion
-dontwarn io.github.rosemoe.sora.editor.ts.LocalsCaptureSpec
-dontwarn io.github.rosemoe.sora.editor.ts.TsAnalyzeManager
-dontwarn io.github.rosemoe.sora.editor.ts.TsLanguageSpec
-dontwarn io.github.rosemoe.sora.editor.ts.TsTheme
-dontwarn io.github.rosemoe.sora.editor.ts.predicate.TsPredicate
-dontwarn io.github.rosemoe.sora.editor.ts.spans.DefaultSpanFactory
-dontwarn io.github.rosemoe.sora.editor.ts.spans.TsSpanFactory
-dontwarn java.awt.Component
-dontwarn java.lang.ProcessHandle
-dontwarn java.lang.management.ManagementFactory
-dontwarn javax.management.InstanceAlreadyExistsException
-dontwarn javax.management.InstanceNotFoundException
-dontwarn javax.management.JMException
-dontwarn javax.management.MBeanRegistrationException
-dontwarn javax.management.MBeanServer
-dontwarn javax.management.MalformedObjectNameException
-dontwarn javax.management.NotCompliantMBeanException
-dontwarn javax.management.ObjectInstance
-dontwarn javax.management.ObjectName
-dontwarn org.apache.log4j.Level
-dontwarn org.apache.log4j.Logger
-dontwarn org.apache.log4j.Priority
-dontwarn org.apache.logging.log4j.LogManager
-dontwarn org.apache.logging.log4j.Logger
-dontwarn org.apache.logging.log4j.message.MessageFactory
-dontwarn org.apache.logging.log4j.spi.ExtendedLogger
-dontwarn org.apache.logging.log4j.spi.ExtendedLoggerWrapper
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid
-dontwarn reactor.blockhound.integration.BlockHoundIntegration