/*
 * Decompiled with CFR 0.152.
 * 
 */
package org.jetbrains.kotlin.cli.jvm.compiler;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.InferredAnnotationsManager;
import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreJavaFileManager;
import com.intellij.core.JavaCoreProjectEnvironment;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.PersistentFSConstants;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.impl.ZipHandler;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.JavaClassSupersImpl;
import com.intellij.psi.impl.PsiElementFinderImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessor;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.JavaClassSupers;
import com.intellij.util.lang.UrlClassLoader;
import java.io.Closeable;
import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import kotlin.Deprecated;
import kotlin.ExceptionsKt;
import kotlin.Metadata;
import kotlin.Pair;
import kotlin.PublishedApi;
import kotlin.ReplaceWith;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.collections.SetsKt;
import kotlin.comparisons.ComparisonsKt;
import kotlin.io.CloseableKt;
import kotlin.jvm.JvmStatic;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.InlineMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.Reflection;
import kotlin.jvm.internal.SourceDebugExtension;
import kotlin.reflect.KDeclarationContainer;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport;
import org.jetbrains.kotlin.asJava.LightClassGenerationSupport;
import org.jetbrains.kotlin.asJava.finder.JavaElementFinder;
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension;
import org.jetbrains.kotlin.backend.jvm.extensions.ClassGeneratorExtension;
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys;
import org.jetbrains.kotlin.cli.common.CliModuleVisibilityManagerImpl;
import org.jetbrains.kotlin.cli.common.CompilerSystemProperties;
import org.jetbrains.kotlin.cli.common.PropertiesKt;
import org.jetbrains.kotlin.cli.common.config.ContentRoot;
import org.jetbrains.kotlin.cli.common.config.KotlinSourceRoot;
import org.jetbrains.kotlin.cli.common.environment.UtilKt;
import org.jetbrains.kotlin.cli.common.extensions.ScriptEvaluationExtension;
import org.jetbrains.kotlin.cli.common.extensions.ShellExtension;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.compiler.ClasspathRootsResolver;
import org.jetbrains.kotlin.cli.jvm.compiler.CliKotlinAsJavaSupport;
import org.jetbrains.kotlin.cli.jvm.compiler.CliLightClassGenerationSupport;
import org.jetbrains.kotlin.cli.jvm.compiler.CliModuleAnnotationsResolver;
import org.jetbrains.kotlin.cli.jvm.compiler.CliTraceHolder;
import org.jetbrains.kotlin.cli.jvm.compiler.CliVirtualFileFinderFactory;
import org.jetbrains.kotlin.cli.jvm.compiler.CompatKt;
import org.jetbrains.kotlin.cli.jvm.compiler.CoreEnvironmentUtilsKt;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.cli.jvm.compiler.IdeaExtensionPoints;
import org.jetbrains.kotlin.cli.jvm.compiler.JavaLanguageLevelKt;
import org.jetbrains.kotlin.cli.jvm.compiler.JvmPackagePartProvider;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCliJavaFileManagerImpl;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironment;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironmentMode;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment;
import org.jetbrains.kotlin.cli.jvm.compiler.MockExternalAnnotationsManager;
import org.jetbrains.kotlin.cli.jvm.compiler.MockInferredAnnotationsManager;
import org.jetbrains.kotlin.cli.jvm.compiler.jarfs.FastJarFileSystem;
import org.jetbrains.kotlin.cli.jvm.config.JavaSourceRoot;
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot;
import org.jetbrains.kotlin.cli.jvm.config.JvmContentRoot;
import org.jetbrains.kotlin.cli.jvm.config.JvmContentRootBase;
import org.jetbrains.kotlin.cli.jvm.config.JvmContentRootsKt;
import org.jetbrains.kotlin.cli.jvm.config.JvmModulePathRoot;
import org.jetbrains.kotlin.cli.jvm.config.VirtualJvmClasspathRoot;
import org.jetbrains.kotlin.cli.jvm.index.JavaRoot;
import org.jetbrains.kotlin.cli.jvm.index.JvmDependenciesDynamicCompoundIndex;
import org.jetbrains.kotlin.cli.jvm.index.JvmDependenciesIndex;
import org.jetbrains.kotlin.cli.jvm.index.JvmDependenciesIndexImpl;
import org.jetbrains.kotlin.cli.jvm.index.SingleJavaFileRootsIndex;
import org.jetbrains.kotlin.cli.jvm.javac.JavacWrapperRegistrar;
import org.jetbrains.kotlin.cli.jvm.modules.CliJavaModuleFinder;
import org.jetbrains.kotlin.cli.jvm.modules.CliJavaModuleResolver;
import org.jetbrains.kotlin.codegen.extensions.ClassBuilderInterceptorExtension;
import org.jetbrains.kotlin.codegen.extensions.ClassFileFactoryFinalizerExtension;
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension;
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar;
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrarKt;
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar;
import org.jetbrains.kotlin.config.AppendJavaSourceRootsHandlerKeyKt;
import org.jetbrains.kotlin.config.CommonConfigurationKeys;
import org.jetbrains.kotlin.config.CommonConfigurationKeysKt;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.config.JVMConfigurationKeys;
import org.jetbrains.kotlin.extensions.CollectAdditionalSourcesExtension;
import org.jetbrains.kotlin.extensions.CompilerConfigurationExtension;
import org.jetbrains.kotlin.extensions.DeclarationAttributeAltererExtension;
import org.jetbrains.kotlin.extensions.PreprocessedVirtualFileFactoryExtension;
import org.jetbrains.kotlin.extensions.ProcessSourcesBeforeCompilingExtension;
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor;
import org.jetbrains.kotlin.extensions.TypeAttributeTranslatorExtension;
import org.jetbrains.kotlin.extensions.internal.CandidateInterceptor;
import org.jetbrains.kotlin.extensions.internal.TypeResolutionInterceptor;
import org.jetbrains.kotlin.fir.extensions.FirAnalysisHandlerExtension;
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.kotlin.js.translate.extensions.JsSyntheticTranslateExtension;
import org.jetbrains.kotlin.load.java.structure.impl.source.JavaElementSourceFactory;
import org.jetbrains.kotlin.load.java.structure.impl.source.JavaFixedElementSourceFactory;
import org.jetbrains.kotlin.load.kotlin.KotlinBinaryClassCache;
import org.jetbrains.kotlin.load.kotlin.MetadataFinderFactory;
import org.jetbrains.kotlin.load.kotlin.ModuleVisibilityManager;
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider;
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinderFactory;
import org.jetbrains.kotlin.modules.Module;
import org.jetbrains.kotlin.parsing.KotlinParserDefinition;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.resolve.CodeAnalyzerInitializer;
import org.jetbrains.kotlin.resolve.ModuleAnnotationsResolver;
import org.jetbrains.kotlin.resolve.diagnostics.DiagnosticSuppressor;
import org.jetbrains.kotlin.resolve.extensions.AssignResolutionAltererExtension;
import org.jetbrains.kotlin.resolve.extensions.ExtraImportsProviderExtension;
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension;
import org.jetbrains.kotlin.resolve.jvm.KotlinCliJavaFileManager;
import org.jetbrains.kotlin.resolve.jvm.KotlinJavaPsiFacade;
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension;
import org.jetbrains.kotlin.resolve.jvm.extensions.PackageFragmentProviderExtension;
import org.jetbrains.kotlin.resolve.jvm.extensions.SyntheticJavaResolveExtension;
import org.jetbrains.kotlin.resolve.jvm.modules.JavaModule;
import org.jetbrains.kotlin.resolve.jvm.modules.JavaModuleResolver;
import org.jetbrains.kotlin.resolve.lazy.declarations.CliDeclarationProviderFactoryService;
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactoryService;
import org.jetbrains.kotlin.serialization.DescriptorSerializerPlugin;
import org.jetbrains.kotlin.utils.PathUtil;

@Metadata(mv={2, 0, 0}, k=1, xi=48, d1={" À\n\n \n \n\n \n\n \n\n\b\n!\n\n \n\n \n\n \n\n \n\n\n \n\n \n\n\b\n \n\n\b\n\n \n\n\b\n\n\b\n\n\n\b\n\n\b\n\n\b\n\b\n\b\n\n\b\n\n\b\n\n\b\n\n\b\n\n\b  U20:TUB!\b000¢\b\b\tJ020HJ02\f\b0!0 J\"02#0$Ja,0-2\b%\b0!0 2\b.\b00 2\b/\n01002\b2\n0!0 2\b3\n0!0 ¢4J<0=2\f\b00 J>02?0@HJA\n0!0 2\fB\b0C0 JD0&2E0FHJG0&2H01JI0&2E0J2K01HJL0&2M0!HJ\fN\b00 JO02P0Q2R01H ¢\bSR0¢\b\n \b\nR0¢\b\n \b\f\rR\b00X¢\n R0X¢\n R\b00X¢\n R0X¢\n R\b00X¢\n R%\b0&0 *0&8BX¢\b'(R)\b0!0 8BX¢\b*+R5068BX¢\b78R098F¢\b:;¨V"}, d2={"Lorg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment;", "", "projectEnvironment", "Lorg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment$ProjectEnvironment;", "configuration", "Lorg/jetbrains/kotlin/config/CompilerConfiguration;", "configFiles", "Lorg/jetbrains/kotlin/cli/jvm/compiler/EnvironmentConfigFiles;", "<init>", "(Lorg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment$ProjectEnvironment;Lorg/jetbrains/kotlin/config/CompilerConfiguration;Lorg/jetbrains/kotlin/cli/jvm/compiler/EnvironmentConfigFiles;)V", "getProjectEnvironment", "()Lorg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment$ProjectEnvironment;", "getConfiguration", "()Lorg/jetbrains/kotlin/config/CompilerConfiguration;", "sourceFiles", "", "Lorg/jetbrains/kotlin/psi/KtFile;", "rootsIndex", "Lorg/jetbrains/kotlin/cli/jvm/index/JvmDependenciesDynamicCompoundIndex;", "packagePartProviders", "Lorg/jetbrains/kotlin/cli/jvm/compiler/JvmPackagePartProvider;", "classpathRootsResolver", "Lorg/jetbrains/kotlin/cli/jvm/compiler/ClasspathRootsResolver;", "initialRoots", "Ljava/util/ArrayList;", "Lorg/jetbrains/kotlin/cli/jvm/index/JavaRoot;", "collectAdditionalSources", "", "project", "Lcom/intellij/mock/MockProject;", "addKotlinSourceRoots", "rootDirs", "", "Ljava/io/File;", "createPackagePartProvider", "scope", "Lcom/intellij/psi/search/GlobalSearchScope;", "javaFiles", "Lcom/intellij/openapi/vfs/VirtualFile;", "getJavaFiles", "(Lcom/intellij/openapi/vfs/VirtualFile;)Ljava/util/List;", "allJavaFiles", "getAllJavaFiles", "()Ljava/util/List;", "registerJavac", "", "kotlinFiles", "arguments", "", "", "bootClasspath", "sourcePath", "(Ljava/util/List;Ljava/util/List;[Ljava/lang/String;Ljava/util/List;Ljava/util/List;)Z", "applicationEnvironment", "Lcom/intellij/core/CoreApplicationEnvironment;", "getApplicationEnvironment", "()Lcom/intellij/core/CoreApplicationEnvironment;", "Lcom/intellij/openapi/project/Project;", "getProject", "()Lcom/intellij/openapi/project/Project;", "countLinesOfCode", "", "updateClasspathFromRootsIndex", "index", "Lorg/jetbrains/kotlin/cli/jvm/index/JvmDependenciesIndex;", "updateClasspath", "contentRoots", "Lorg/jetbrains/kotlin/cli/common/config/ContentRoot;", "contentRootToVirtualFile", "root", "Lorg/jetbrains/kotlin/cli/jvm/config/JvmContentRootBase;", "findLocalFile", "path", "findExistingRoot", "Lorg/jetbrains/kotlin/cli/jvm/config/JvmContentRoot;", "rootDescription", "findJarRoot", "file", "getSourceFiles", "report", "severity", "Lorg/jetbrains/kotlin/cli/common/messages/CompilerMessageSeverity;", "message", "report$cli_base", "ProjectEnvironment", "Companion", "cli-base"})
@SourceDebugExtension(value={"SMAP\nKotlinCoreEnvironment.kt\nKotlin\n*S Kotlin\n*F\n+ 1 KotlinCoreEnvironment.kt\norg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n+ 4 _Sequences.kt\nkotlin/sequences/SequencesKt___SequencesKt\n*L\n1#1,846:1\n1010#2,2:847\n3193#2,10:850\n827#2:860\n855#2,2:861\n1557#2:863\n1628#2,3:864\n1611#2,9:867\n1863#2:876\n1864#2:878\n1620#2:879\n1368#2:880\n1454#2,5:881\n1557#2:886\n1628#2,3:887\n2979#2,5:890\n1797#2,3:897\n1557#2:900\n1628#2,3:901\n1#3:849\n1#3:877\n1317#4,2:895\n*S KotlinDebug\n*F\n+ 1 KotlinCoreEnvironment.kt\norg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment\n*L\n223#1:847,2\n260#1:850,10\n312#1:860\n312#1:861,2\n318#1:863\n318#1:864,3\n342#1:867,9\n342#1:876\n342#1:878\n342#1:879\n343#1:880\n343#1:881,5\n344#1:886\n344#1:887,3\n366#1:890,5\n428#1:897,3\n285#1:900\n285#1:901,3\n342#1:877\n372#1:895,2\n*E\n"})
public final class KotlinCoreEnvironment {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final ProjectEnvironment projectEnvironment;
    @NotNull
    private final CompilerConfiguration configuration;
    @NotNull
    private final List<KtFile> sourceFiles;
    @NotNull
    private final JvmDependenciesDynamicCompoundIndex rootsIndex;
    @NotNull
    private final List<JvmPackagePartProvider> packagePartProviders;
    @NotNull
    private final ClasspathRootsResolver classpathRootsResolver;
    @NotNull
    private final ArrayList<JavaRoot> initialRoots;
    @NotNull
    private static final Logger LOG;
    @NotNull
    private static final Object APPLICATION_LOCK;
    @Nullable
    private static KotlinCoreApplicationEnvironment ourApplicationEnvironment;
    private static int ourProjectCount;
    private static String ourProjecount = "android_zero";

    /*
     * WARNING - void declaration
     */
    private KotlinCoreEnvironment(ProjectEnvironment projectEnvironment, CompilerConfiguration configuration, EnvironmentConfigFiles configFiles) {
        void $this$_init__u24lambda_u242;
        VirtualFile virtualFile;
        KotlinCoreEnvironment kotlinCoreEnvironment;
        this.projectEnvironment = projectEnvironment;
        this.configuration = configuration;
        this.sourceFiles = (List)new ArrayList();
        this.packagePartProviders = (List)new ArrayList();
        this.initialRoots = new ArrayList();
        Companion.configureProjectEnvironment(this.projectEnvironment, this.configuration, configFiles);
        MockProject mockProject = this.projectEnvironment.getProject();
        Intrinsics.checkNotNullExpressionValue((Object)mockProject, (String)"getProject(...)");
        MockProject project = mockProject;
        project.registerService(DeclarationProviderFactoryService.class, (Object)new CliDeclarationProviderFactoryService((Collection)this.sourceFiles));
        CollectionsKt.addAll((Collection)((Collection)this.sourceFiles), (Iterable)((Iterable)CoreEnvironmentUtilsKt.createSourceFilesFromSourceRoots$default(this.configuration, (Project)project, CoreEnvironmentUtilsKt.getSourceRootsCheckingForDuplicates(this.configuration, (MessageCollector)this.configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY)), null, 8, null)));
        this.collectAdditionalSources(project);
        List<KtFile> $this$sortBy$iv = this.sourceFiles;
        boolean $i$f$sortBy = false;
        if ($this$sortBy$iv.size() > 1) {
            CollectionsKt.sortWith($this$sortBy$iv, (Comparator)new Comparator(){

                public final int compare(T a, T b) {
                    KtFile it = (KtFile)a;
                    boolean bl = false;
                    Comparable comparable = (Comparable)it.getVirtualFile().getPath();
                    it = (KtFile)b;
                    Comparable comparable2 = comparable;
                    bl = false;
                    return ComparisonsKt.compareValues((Comparable)comparable2, (Comparable)((Comparable)it.getVirtualFile().getPath()));
                }
            });
        }
        Object object = project.getService(CoreJavaFileManager.class);
        Intrinsics.checkNotNull((Object)object, (String)"null cannot be cast to non-null type org.jetbrains.kotlin.cli.jvm.compiler.KotlinCliJavaFileManagerImpl");
        KotlinCliJavaFileManagerImpl javaFileManager = (KotlinCliJavaFileManagerImpl)((Object)object);
        MessageCollector messageCollector = (MessageCollector)this.configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY);
        File jdkHome = (File)this.configuration.get(JVMConfigurationKeys.JDK_HOME);
        Integer releaseTarget = (Integer)this.configuration.get(JVMConfigurationKeys.JDK_RELEASE);
        CliJavaModuleFinder javaModuleFinder = new CliJavaModuleFinder(jdkHome, messageCollector, (KotlinCliJavaFileManager)javaFileManager, (Project)project, releaseTarget);
        Object object2 = (List)this.configuration.get(JVMConfigurationKeys.MODULES);
        if (object2 == null || (object2 = (Module)CollectionsKt.singleOrNull((List)object2)) == null || (object2 = object2.getOutputDirectory()) == null) {
            File file = (File)this.configuration.get(JVMConfigurationKeys.OUTPUT_DIRECTORY);
            object2 = file != null ? file.getAbsolutePath() : null;
        }
        Object outputDirectory = object2;
        KotlinCoreEnvironment kotlinCoreEnvironment2 = this;
        PsiManager psiManager = PsiManager.getInstance((Project)((Project)project));
        PsiManager psiManager2 = psiManager;
        Intrinsics.checkNotNullExpressionValue((Object)psiManager, (String)"getInstance(...)");
        MessageCollector messageCollector2 = messageCollector;
        List list = this.configuration.getList(JVMConfigurationKeys.ADDITIONAL_JAVA_MODULES);
        List list2 = list;
        Intrinsics.checkNotNullExpressionValue((Object)list, (String)"getList(...)");
        Function1 function1 = (Function1)new Function1<JvmContentRootBase, VirtualFile>((Object)this){

            public final VirtualFile invoke(JvmContentRootBase p0) {
                Intrinsics.checkNotNullParameter((Object)p0, (String)"p0");
                return ((KotlinCoreEnvironment)this.receiver).contentRootToVirtualFile(p0);
            }

            public final String getSignature() {
                return "contentRootToVirtualFile(Lorg/jetbrains/kotlin/cli/jvm/config/JvmContentRootBase;)Lcom/intellij/openapi/vfs/VirtualFile;";
            }

            public final String getName() {
                return "contentRootToVirtualFile";
            }

            public final KDeclarationContainer getOwner() {
                return (KDeclarationContainer)Reflection.getOrCreateKotlinClass(KotlinCoreEnvironment.class);
            }
        };
        CliJavaModuleFinder cliJavaModuleFinder = javaModuleFinder;
        boolean bl = !this.configuration.getBoolean(CLIConfigurationKeys.ALLOW_KOTLIN_PACKAGE);
        Object object3 = outputDirectory;
        if (object3 != null) {
            void p0;
            Object object4 = object3;
            boolean bl2 = bl;
            CliJavaModuleFinder cliJavaModuleFinder2 = cliJavaModuleFinder;
            Function1 function12 = function1;
            List list3 = list2;
            MessageCollector messageCollector3 = messageCollector2;
            PsiManager psiManager3 = psiManager2;
            kotlinCoreEnvironment = kotlinCoreEnvironment2;
            boolean bl3 = false;
            VirtualFile virtualFile2 = this.findLocalFile((String)p0);
            kotlinCoreEnvironment2 = kotlinCoreEnvironment;
            psiManager2 = psiManager3;
            messageCollector2 = messageCollector3;
            list2 = list3;
            function1 = function12;
            cliJavaModuleFinder = cliJavaModuleFinder2;
            bl = bl2;
            virtualFile = virtualFile2;
        } else {
            virtualFile = null;
        }
        Integer n = releaseTarget;
        KotlinCliJavaFileManager kotlinCliJavaFileManager = javaFileManager;
        VirtualFile virtualFile3 = virtualFile;
        boolean bl4 = bl;
        CliJavaModuleFinder cliJavaModuleFinder3 = cliJavaModuleFinder;
        Function1 function13 = function1;
        List list4 = list2;
        MessageCollector messageCollector4 = messageCollector2;
        PsiManager psiManager4 = psiManager2;
        kotlinCoreEnvironment2.classpathRootsResolver = new ClasspathRootsResolver(psiManager4, messageCollector4, (List<String>)list4, (Function1<? super JvmContentRootBase, ? extends VirtualFile>)function13, cliJavaModuleFinder3, bl4, virtualFile3, kotlinCliJavaFileManager, n);
        List list5 = this.configuration.getList(CLIConfigurationKeys.CONTENT_ROOTS);
        Intrinsics.checkNotNullExpressionValue((Object)list5, (String)"getList(...)");
        ClasspathRootsResolver.RootsAndModules rootsAndModules = this.classpathRootsResolver.convertClasspathRoots((List<? extends ContentRoot>)list5);
        List<JavaRoot> initialRoots = rootsAndModules.component1();
        List<JavaModule> javaModules = rootsAndModules.component2();
        this.initialRoots.addAll((Collection)initialRoots);
        Iterable $this$partition$iv = (Iterable)initialRoots;
        boolean $i$f$partition = false;
        ArrayList first$iv = new ArrayList();
        ArrayList second$iv = new ArrayList();
        for (Object element$iv : $this$partition$iv) {
            JavaRoot javaRoot = (JavaRoot)element$iv;
            boolean bl5 = false;
            VirtualFile file = javaRoot.component1();
            if (file.isDirectory() || !Intrinsics.areEqual((Object)file.getExtension(), (Object)"java")) {
                first$iv.add(element$iv);
                continue;
            }
            second$iv.add(element$iv);
        }
        Pair pair = new Pair((Object)first$iv, (Object)second$iv);
        List roots = (List)pair.component1();
        List singleJavaFileRoots = (List)pair.component2();
        second$iv = first$iv = new JvmDependenciesDynamicCompoundIndex();
        kotlinCoreEnvironment = this;
        boolean bl6 = false;
        $this$_init__u24lambda_u242.addIndex((JvmDependenciesIndex)new JvmDependenciesIndexImpl(roots));
        this.updateClasspathFromRootsIndex((JvmDependenciesIndex)$this$_init__u24lambda_u242);
        kotlinCoreEnvironment.rootsIndex = first$iv;
        javaFileManager.initialize((JvmDependenciesIndex)this.rootsIndex, this.packagePartProviders, new SingleJavaFileRootsIndex(singleJavaFileRoots), this.configuration.getBoolean(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING));
        project.registerService(JavaModuleResolver.class, (Object)new CliJavaModuleResolver(this.classpathRootsResolver.getJavaModuleGraph(), javaModules, SequencesKt.toList((Sequence)javaModuleFinder.getSystemModules()), (Project)project));
        CliVirtualFileFinderFactory finderFactory = new CliVirtualFileFinderFactory((JvmDependenciesIndex)this.rootsIndex, releaseTarget != null);
        project.registerService(MetadataFinderFactory.class, (Object)finderFactory);
        project.registerService(VirtualFileFinderFactory.class, (Object)finderFactory);
        project.putUserData(AppendJavaSourceRootsHandlerKeyKt.getAPPEND_JAVA_SOURCE_ROOTS_HANDLER_KEY(), arg_0 -> KotlinCoreEnvironment._init_$lambda$4(this, arg_0));
        JavaLanguageLevelKt.setupHighestLanguageLevel((Project)project);
    }

    @NotNull
    public final ProjectEnvironment getProjectEnvironment() {
        return this.projectEnvironment;
    }

    @NotNull
    public final CompilerConfiguration getConfiguration() {
        return this.configuration;
    }

    /*
     * WARNING - void declaration
     */
    private final void collectAdditionalSources(MockProject project) {
        Collection unprocessedSources = (Collection)this.sourceFiles;
        HashSet processedSources = new HashSet();
        HashMap processedSourcesByExtension = new HashMap();
        int sourceCollectionIterations = 0;
        while (!unprocessedSources.isEmpty()) {
            void $this$filterNotTo$iv$iv;
            Iterable sourcesToProcess;
            if (sourceCollectionIterations++ > 10) {
                throw new IllegalStateException("Unable to collect additional sources in reasonable number of iterations");
            }
            processedSources.addAll(unprocessedSources);
            ArrayList allNewSources = new ArrayList();
            for (CollectAdditionalSourcesExtension extension : CollectAdditionalSourcesExtension.Companion.getInstances((Project)project)) {
                Collection collection = (Collection)processedSourcesByExtension.get((Object)extension);
                sourcesToProcess = CollectionsKt.minus((Iterable)((Iterable)unprocessedSources), (Iterable)(collection != null ? (Iterable)collection : (Iterable)CollectionsKt.emptyList()));
                Collection newSources = extension.collectAdditionalSourcesAndUpdateConfiguration((Collection)sourcesToProcess, this.configuration, (Project)project);
                if (!(!newSources.isEmpty())) continue;
                allNewSources.addAll(newSources);
                ((Map)processedSourcesByExtension).put((Object)extension, (Object)newSources);
            }
            Iterable $this$filterNot$iv = (Iterable)allNewSources;
            boolean $i$f$filterNot = false;
            sourcesToProcess = $this$filterNot$iv;
            Collection destination$iv$iv = (Collection)new ArrayList();
            boolean $i$f$filterNotTo = false;
            for (Object element$iv$iv : $this$filterNotTo$iv$iv) {
                KtFile it = (KtFile)element$iv$iv;
                boolean bl = false;
                if (processedSources.contains((Object)it)) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            unprocessedSources = (Collection)CollectionsKt.distinct((Iterable)((Iterable)((List)destination$iv$iv)));
            CollectionsKt.addAll((Collection)((Collection)this.sourceFiles), (Iterable)((Iterable)unprocessedSources));
        }
    }

    /*
     * WARNING - void declaration
     */
    public final void addKotlinSourceRoots(@NotNull List<? extends File> rootDirs) {
        void $this$mapTo$iv$iv;
        Intrinsics.checkNotNullParameter(rootDirs, (String)"rootDirs");
        Iterable $this$map$iv = (Iterable)rootDirs;
        boolean $i$f$map = false;
        Iterable iterable = $this$map$iv;
        Collection destination$iv$iv = (Collection)new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            void it;
            File file = (File)item$iv$iv;
            Collection collection = destination$iv$iv;
            boolean bl = false;
            String string = it.getAbsolutePath();
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getAbsolutePath(...)");
            collection.add((Object)new KotlinSourceRoot(string, false, null));
        }
        List roots = (List)destination$iv$iv;
        CollectionsKt.addAll((Collection)((Collection)this.sourceFiles), (Iterable)((Iterable)SetsKt.minus((Set)CollectionsKt.toSet((Iterable)((Iterable)CoreEnvironmentUtilsKt.createSourceFilesFromSourceRoots$default(this.configuration, this.getProject(), roots, null, 8, null))), (Iterable)((Iterable)this.sourceFiles))));
    }

    @NotNull
    public final JvmPackagePartProvider createPackagePartProvider(@NotNull GlobalSearchScope scope) {
        JvmPackagePartProvider jvmPackagePartProvider;
        Intrinsics.checkNotNullParameter((Object)scope, (String)"scope");
        JvmPackagePartProvider $this$createPackagePartProvider_u24lambda_u247 = jvmPackagePartProvider = new JvmPackagePartProvider(CommonConfigurationKeysKt.getLanguageVersionSettings((CompilerConfiguration)this.configuration), scope);
        boolean bl = false;
        List list = (List)this.initialRoots;
        Object object = this.configuration.getNotNull(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY);
        Intrinsics.checkNotNullExpressionValue((Object)object, (String)"getNotNull(...)");
        $this$createPackagePartProvider_u24lambda_u247.addRoots((List<JavaRoot>)list, (MessageCollector)object);
        ((Collection)this.packagePartProviders).add((Object)$this$createPackagePartProvider_u24lambda_u247);
        ModuleAnnotationsResolver moduleAnnotationsResolver = ModuleAnnotationsResolver.Companion.getInstance(this.getProject());
        Intrinsics.checkNotNull((Object)moduleAnnotationsResolver, (String)"null cannot be cast to non-null type org.jetbrains.kotlin.cli.jvm.compiler.CliModuleAnnotationsResolver");
        ((CliModuleAnnotationsResolver)moduleAnnotationsResolver).addPackagePartProvider((PackagePartProvider)$this$createPackagePartProvider_u24lambda_u247);
        return jvmPackagePartProvider;
    }

    private final List<VirtualFile> getJavaFiles(VirtualFile $this$javaFiles) {
        List list;
        List $this$_get_javaFiles__u24lambda_u2410 = list = (List)new ArrayList();
        boolean bl = false;
        VfsUtilCore.processFilesRecursively((VirtualFile)$this$javaFiles, arg_0 -> KotlinCoreEnvironment._get_javaFiles_$lambda$10$lambda$9(arg_0 -> KotlinCoreEnvironment._get_javaFiles_$lambda$10$lambda$8($this$_get_javaFiles__u24lambda_u2410, arg_0), arg_0));
        return list;
    }

    /*
     * WARNING - void declaration
     */
    private final List<File> getAllJavaFiles() {
        void $this$mapTo$iv$iv;
        Iterable list$iv$iv;
        VirtualFile it;
        Iterable $this$flatMapTo$iv$iv;
        Iterable $this$mapNotNullTo$iv$iv;
        Iterable $this$mapNotNull$iv = (Iterable)JvmContentRootsKt.getJavaSourceRoots((CompilerConfiguration)this.configuration);
        boolean $i$f$mapNotNull = false;
        Iterable iterable = $this$mapNotNull$iv;
        Collection destination$iv$iv = (Collection)new ArrayList();
        boolean $i$f$mapNotNullTo = false;
        void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv$iv$iv.iterator();
        while (iterator.hasNext()) {
            VirtualFile it$iv$iv;
            Object element$iv$iv$iv;
            Object element$iv$iv = element$iv$iv$iv = iterator.next();
            boolean bl = false;
            String p0 = (String)element$iv$iv;
            boolean bl2 = false;
            if (this.findLocalFile(p0) == null) continue;
            boolean bl3 = false;
            destination$iv$iv.add((Object)it$iv$iv);
        }
        Iterable $this$flatMap$iv = (Iterable)((List)destination$iv$iv);
        boolean $i$f$flatMap = false;
        $this$mapNotNullTo$iv$iv = $this$flatMap$iv;
        destination$iv$iv = (Collection)new ArrayList();
        boolean $i$f$flatMapTo = false;
        for (Object element$iv$iv : $this$flatMapTo$iv$iv) {
            it = (VirtualFile)element$iv$iv;
            boolean bl = false;
            list$iv$iv = (Iterable)this.getJavaFiles(it);
            CollectionsKt.addAll((Collection)destination$iv$iv, (Iterable)list$iv$iv);
        }
        Iterable $this$map$iv = (Iterable)((List)destination$iv$iv);
        boolean $i$f$map = false;
        $this$flatMapTo$iv$iv = $this$map$iv;
        destination$iv$iv = (Collection)new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            list$iv$iv = (VirtualFile)item$iv$iv;
            Collection collection = destination$iv$iv;
            boolean bl = false;
            collection.add((Object)new File(it.getCanonicalPath()));
        }
        return (List)destination$iv$iv;
    }

    public final boolean registerJavac(@NotNull List<? extends File> javaFiles, @NotNull List<? extends KtFile> kotlinFiles, @Nullable String[] arguments, @Nullable List<? extends File> bootClasspath, @Nullable List<? extends File> sourcePath) {
        Intrinsics.checkNotNullParameter(javaFiles, (String)"javaFiles");
        Intrinsics.checkNotNullParameter(kotlinFiles, (String)"kotlinFiles");
        MockProject mockProject = this.projectEnvironment.getProject();
        Intrinsics.checkNotNullExpressionValue((Object)mockProject, (String)"getProject(...)");
        return JavacWrapperRegistrar.INSTANCE.registerJavac(mockProject, this.configuration, javaFiles, kotlinFiles, arguments, bootClasspath, sourcePath, LightClassGenerationSupport.Companion.getInstance(this.getProject()), this.packagePartProviders);
    }

    public static /* synthetic */ boolean registerJavac$default(KotlinCoreEnvironment kotlinCoreEnvironment, List list, List list2, String[] stringArray, List list3, List list4, int n, Object object) {
        if ((n & 1) != 0) {
            list = kotlinCoreEnvironment.getAllJavaFiles();
        }
        if ((n & 2) != 0) {
            list2 = kotlinCoreEnvironment.sourceFiles;
        }
        if ((n & 4) != 0) {
            stringArray = null;
        }
        if ((n & 8) != 0) {
            list3 = null;
        }
        if ((n & 0x10) != 0) {
            list4 = null;
        }
        return kotlinCoreEnvironment.registerJavac(list, list2, stringArray, (List<? extends File>)list3, (List<? extends File>)list4);
    }

    private final CoreApplicationEnvironment getApplicationEnvironment() {
        CoreApplicationEnvironment coreApplicationEnvironment = this.projectEnvironment.getEnvironment();
        Intrinsics.checkNotNullExpressionValue((Object)coreApplicationEnvironment, (String)"getEnvironment(...)");
        return coreApplicationEnvironment;
    }

    @NotNull
    public final Project getProject() {
        MockProject mockProject = this.projectEnvironment.getProject();
        Intrinsics.checkNotNullExpressionValue((Object)mockProject, (String)"getProject(...)");
        return (Project)mockProject;
    }

    /*
     * WARNING - void declaration
     */
    public final int countLinesOfCode(@NotNull List<? extends KtFile> sourceFiles) {
        Intrinsics.checkNotNullParameter(sourceFiles, (String)"sourceFiles");
        Iterable $this$sumBy$iv = (Iterable)sourceFiles;
        boolean $i$f$sumBy = false;
        int sum$iv = 0;
        for (Object element$iv : $this$sumBy$iv) {
            void sourceFile;
            KtFile ktFile = (KtFile)element$iv;
            int n = sum$iv;
            boolean bl = false;
            String text = sourceFile.getText();
            int n2 = StringUtil.getLineBreakCount((CharSequence)text) + (StringUtil.endsWithLineBreak((CharSequence)text) ? 0 : 1);
            sum$iv = n + n2;
        }
        return sum$iv;
    }

    private final void updateClasspathFromRootsIndex(JvmDependenciesIndex index) {
        Sequence $this$forEach$iv = index.getIndexedRoots();
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            JavaRoot it = (JavaRoot)element$iv;
            boolean bl = false;
            this.projectEnvironment.addSourcesToClasspath(it.getFile());
        }
    }

    @Nullable
    public final List<File> updateClasspath(@NotNull List<? extends ContentRoot> contentRoots) {
        List list;
        List list2;
        Intrinsics.checkNotNullParameter(contentRoots, (String)"contentRoots");
        List newRoots = CollectionsKt.minus((Iterable)((Iterable)this.classpathRootsResolver.convertClasspathRoots(contentRoots).getRoots()), (Iterable)((Iterable)this.initialRoots));
        if (this.packagePartProviders.isEmpty()) {
            this.initialRoots.addAll((Collection)newRoots);
        } else {
            for (JvmPackagePartProvider packagePartProvider : this.packagePartProviders) {
                Object object = this.configuration.getNotNull(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY);
                Intrinsics.checkNotNullExpressionValue((Object)object, (String)"getNotNull(...)");
                packagePartProvider.addRoots((List<JavaRoot>)newRoots, (MessageCollector)object);
            }
        }
        Iterable iterable = (Iterable)contentRoots;
        List list3 = this.configuration.getList(CLIConfigurationKeys.CONTENT_ROOTS);
        Intrinsics.checkNotNullExpressionValue((Object)list3, (String)"getList(...)");
        this.configuration.addAll(CLIConfigurationKeys.CONTENT_ROOTS, (Collection)CollectionsKt.minus((Iterable)iterable, (Iterable)((Iterable)list3)));
        JvmDependenciesIndex jvmDependenciesIndex = this.rootsIndex.addNewIndexForRoots((Iterable)newRoots);
        if (jvmDependenciesIndex != null) {
            JvmDependenciesIndex newIndex = jvmDependenciesIndex;
            boolean bl = false;
            this.updateClasspathFromRootsIndex(newIndex);
            list2 = SequencesKt.toList((Sequence)SequencesKt.mapNotNull((Sequence)newIndex.getIndexedRoots(), KotlinCoreEnvironment::updateClasspath$lambda$17$lambda$16));
        } else {
            list2 = list = null;
        }
        if (list2 == null) {
            list = CollectionsKt.emptyList();
        }
        return list;
    }

    private final VirtualFile contentRootToVirtualFile(JvmContentRootBase root) {
        VirtualFile virtualFile;
        JvmContentRootBase jvmContentRootBase = root;
        if (jvmContentRootBase instanceof JvmClasspathRoot) {
            virtualFile = ((JvmClasspathRoot)root).getFile().isFile() ? this.findJarRoot(((JvmClasspathRoot)root).getFile()) : this.findExistingRoot((JvmContentRoot)root, "Classpath entry");
        } else if (jvmContentRootBase instanceof JvmModulePathRoot) {
            virtualFile = ((JvmModulePathRoot)root).getFile().isFile() ? this.findJarRoot(((JvmModulePathRoot)root).getFile()) : this.findExistingRoot((JvmContentRoot)root, "Java module root");
        } else if (jvmContentRootBase instanceof JavaSourceRoot) {
            virtualFile = this.findExistingRoot((JvmContentRoot)root, "Java source root");
        } else if (jvmContentRootBase instanceof VirtualJvmClasspathRoot) {
            virtualFile = ((VirtualJvmClasspathRoot)root).getFile();
        } else {
            throw new IllegalStateException("Unexpected root: " + root);
        }
        return virtualFile;
    }

    @Nullable
    public final VirtualFile findLocalFile(@NotNull String path) {
        Intrinsics.checkNotNullParameter((Object)path, (String)"path");
        return this.getApplicationEnvironment().getLocalFileSystem().findFileByPath(path);
    }

    private final VirtualFile findExistingRoot(JvmContentRoot root, String rootDescription) {
        VirtualFile virtualFile;
        String string = root.getFile().getAbsolutePath();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getAbsolutePath(...)");
        VirtualFile it = virtualFile = this.findLocalFile(string);
        boolean bl = false;
        if (it == null) {
            this.report$cli_base(CompilerMessageSeverity.STRONG_WARNING, rootDescription + " points to a non-existent location: " + root.getFile());
        }
        return virtualFile;
    }

    private final VirtualFile findJarRoot(File file) {
        return this.projectEnvironment.getJarFileSystem().findFileByPath(file + "!/");
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public final List<KtFile> getSourceFiles() {
        void $this$fold$iv;
        Iterable iterable = (Iterable)ProcessSourcesBeforeCompilingExtension.Companion.getInstances(this.getProject());
        List<KtFile> list = this.sourceFiles;
        Intrinsics.checkNotNull(list, (String)"null cannot be cast to non-null type kotlin.collections.Collection<org.jetbrains.kotlin.psi.KtFile>");
        Collection initial$iv = (Collection)list;
        boolean $i$f$fold = false;
        Collection accumulator$iv = initial$iv;
        for (Object element$iv : $this$fold$iv) {
            void extension;
            ProcessSourcesBeforeCompilingExtension processSourcesBeforeCompilingExtension = (ProcessSourcesBeforeCompilingExtension)element$iv;
            Collection files = accumulator$iv;
            boolean bl = false;
            accumulator$iv = extension.processSources(files, this.configuration);
        }
        return CollectionsKt.toList((Iterable)((Iterable)accumulator$iv));
    }

    public final void report$cli_base(@NotNull CompilerMessageSeverity severity, @NotNull String message) {
        Intrinsics.checkNotNullParameter((Object)severity, (String)"severity");
        Intrinsics.checkNotNullParameter((Object)message, (String)"message");
        CoreEnvironmentUtilsKt.report$default(this.configuration, severity, message, null, 4, null);
    }

    /*
     * WARNING - void declaration
     */
    private static final Unit _init_$lambda$4(KotlinCoreEnvironment this$0, List<? extends File> roots) {
        void $this$mapTo$iv$iv;
        void $this$map$iv;
        Iterable iterable = (Iterable)roots;
        KotlinCoreEnvironment kotlinCoreEnvironment = this$0;
        boolean $i$f$map = false;
        void var4_5 = $this$map$iv;
        Collection destination$iv$iv = (Collection)new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            void it;
            File file = (File)item$iv$iv;
            Collection collection = destination$iv$iv;
            boolean bl = false;
            collection.add((Object)new JavaSourceRoot((File)it, null));
        }
        kotlinCoreEnvironment.updateClasspath((List<? extends ContentRoot>)((List)destination$iv$iv));
        return Unit.INSTANCE;
    }

    private static final boolean _get_javaFiles_$lambda$10$lambda$8(List $this_apply, VirtualFile file) {
        Intrinsics.checkNotNullParameter((Object)$this_apply, (String)"$this_apply");
        if (Intrinsics.areEqual((Object)file.getExtension(), (Object)"java") || Intrinsics.areEqual((Object)file.getFileType(), (Object)JavaFileType.INSTANCE)) {
            Intrinsics.checkNotNull((Object)file);
            $this_apply.add((Object)file);
        }
        return true;
    }

    private static final boolean _get_javaFiles_$lambda$10$lambda$9(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        return (Boolean)$tmp0.invoke(p0);
    }

    private static final File updateClasspath$lambda$17$lambda$16(JavaRoot javaRoot) {
        Intrinsics.checkNotNullParameter((Object)javaRoot, (String)"<destruct>");
        VirtualFile file = javaRoot.component1();
        VirtualFile virtualFile = VfsUtilCore.getVirtualFileForJar((VirtualFile)file);
        if (virtualFile == null) {
            virtualFile = file;
        }
        return VfsUtilCore.virtualToIoFile((VirtualFile)virtualFile);
    }

    @JvmStatic
    @NotNull
    public static final KotlinCoreEnvironment createForProduction(@NotNull Disposable projectDisposable, @NotNull CompilerConfiguration configuration, @NotNull EnvironmentConfigFiles configFiles) {
        return Companion.createForProduction(projectDisposable, configuration, configFiles);
    }

    @JvmStatic
    @NotNull
    public static final KotlinCoreEnvironment createForProduction(@NotNull ProjectEnvironment projectEnvironment, @NotNull CompilerConfiguration configuration, @NotNull EnvironmentConfigFiles configFiles) {
        return Companion.createForProduction(projectEnvironment, configuration, configFiles);
    }

    @JvmStatic
    @NotNull
    public static final KotlinCoreEnvironment createForTests(@NotNull Disposable parentDisposable, @NotNull CompilerConfiguration initialConfiguration, @NotNull EnvironmentConfigFiles extensionConfigs) {
        return Companion.createForTests(parentDisposable, initialConfiguration, extensionConfigs);
    }

    @JvmStatic
    @NotNull
    public static final KotlinCoreEnvironment createForParallelTests(@NotNull Disposable projectDisposable, @NotNull CompilerConfiguration initialConfiguration, @NotNull EnvironmentConfigFiles extensionConfigs) {
        return Companion.createForParallelTests(projectDisposable, initialConfiguration, extensionConfigs);
    }

    @JvmStatic
    @NotNull
    public static final KotlinCoreEnvironment createForTests(@NotNull ProjectEnvironment projectEnvironment, @NotNull CompilerConfiguration initialConfiguration, @NotNull EnvironmentConfigFiles extensionConfigs) {
        return Companion.createForTests(projectEnvironment, initialConfiguration, extensionConfigs);
    }

    @JvmStatic
    public static final void disposeApplicationEnvironment() {
        Companion.disposeApplicationEnvironment();
    }

    @JvmStatic
    public static final void resetApplicationManager(@Nullable Application applicationToReset) {
        Companion.resetApplicationManager(applicationToReset);
    }

    @JvmStatic
    public static final void configureProjectEnvironment(@NotNull ProjectEnvironment $this$configureProjectEnvironment, @NotNull CompilerConfiguration configuration, @NotNull EnvironmentConfigFiles configFiles) {
        Companion.configureProjectEnvironment($this$configureProjectEnvironment, configuration, configFiles);
    }

    @JvmStatic
    public static final void registerPluginExtensionPoints(@NotNull MockProject project) {
        Companion.registerPluginExtensionPoints(project);
    }

    @JvmStatic
    public static final void registerApplicationServices(@NotNull KotlinCoreApplicationEnvironment applicationEnvironment) {
        Companion.registerApplicationServices(applicationEnvironment);
    }

    @JvmStatic
    public static final void registerProjectExtensionPoints(@NotNull ExtensionsArea area) {
        Companion.registerProjectExtensionPoints(area);
    }

    @JvmStatic
    @Deprecated(message="Use registerProjectServices(project) instead.", replaceWith=@ReplaceWith(expression="registerProjectServices(projectEnvironment.project)", imports={}))
    public static final void registerProjectServices(@NotNull JavaCoreProjectEnvironment projectEnvironment, @Nullable MessageCollector messageCollector) {
        Companion.registerProjectServices(projectEnvironment, messageCollector);
    }

    @JvmStatic
    public static final void registerProjectServices(@NotNull MockProject project) {
        Companion.registerProjectServices(project);
    }

    @JvmStatic
    public static final void registerKotlinLightClassSupport(@NotNull MockProject project) {
        Companion.registerKotlinLightClassSupport(project);
    }

    public /* synthetic */ KotlinCoreEnvironment(ProjectEnvironment projectEnvironment, CompilerConfiguration configuration, EnvironmentConfigFiles configFiles, DefaultConstructorMarker $constructor_marker) {
        this(projectEnvironment, configuration, configFiles);
    }

    static {
        Logger logger = Logger.getInstance(KotlinCoreEnvironment.class);
        Intrinsics.checkNotNullExpressionValue((Object)logger, (String)"getInstance(...)");
        LOG = logger;
        APPLICATION_LOCK = new Object();
    }

    @Metadata(mv={2, 0, 0}, k=1, xi=48, d1={" \n\n \n\b\n\n\n \n\n\b\n\n \n\b\n\b\n\n\b\n\n \n\n \n\n \n\n \n\n\b\r\n\n \n\n\b\n\n\b\n\n\b\n\n\b\n\n \n\n \n\n\b\b 20B\t\b¢\bJ%H\"\b 2\f\bH0H\bø ¢J 0202020HJ 0202020HJ 02 02!02\"0HJ #0202!02\"0HJ 0202!02\"0HJ$02020J(0\r2020J)0\r2020J*0\r20202+0,J\b-0.HJ/0.2\n\b001HJ20.*02020HJ 30\r2 0202+0,HJ40.202506HJ70.2809HJ:0.280920H ¢\b;J<0.2%0\rHJ=0.2%0\rHJ>0.2?0@HJA0.20B2\bC0DHJA0.2809HJE0.20BJF0.2809HR0¢\bX¢\n R0\b8 X¢\n \b\t\b\nR\f0\rX¢\n R0X¢\n R%0\r8F¢\b&'\n\b20¨G"}, d2={"Lorg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment$Companion;", "", "<init>", "()V", "LOG", "Lcom/intellij/openapi/diagnostic/Logger;", "Lorg/jetbrains/annotations/NotNull;", "APPLICATION_LOCK", "Ljava/lang/Object;", "getAPPLICATION_LOCK$annotations", "getAPPLICATION_LOCK", "()Ljava/lang/Object;", "ourApplicationEnvironment", "Lorg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreApplicationEnvironment;", "ourProjectCount", "", "underApplicationLock", "R", "action", "Lkotlin/Function0;", "(Lkotlin/jvm/functions/Function0;)Ljava/lang/Object;", "createForProduction", "Lorg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment;", "projectDisposable", "Lcom/intellij/openapi/Disposable;", "configuration", "Lorg/jetbrains/kotlin/config/CompilerConfiguration;", "configFiles", "Lorg/jetbrains/kotlin/cli/jvm/compiler/EnvironmentConfigFiles;", "projectEnvironment", "Lorg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment$ProjectEnvironment;", "createForTests", "parentDisposable", "initialConfiguration", "extensionConfigs", "createForParallelTests", "createProjectEnvironmentForTests", "applicationEnvironment", "getApplicationEnvironment", "()Lorg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreApplicationEnvironment;", "getOrCreateApplicationEnvironmentForProduction", "getOrCreateApplicationEnvironmentForTests", "getOrCreateApplicationEnvironment", "environmentMode", "Lorg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreApplicationEnvironmentMode;", "disposeApplicationEnvironment", "", "resetApplicationManager", "applicationToReset", "Lcom/intellij/openapi/application/Application;", "configureProjectEnvironment", "createApplicationEnvironment", "registerApplicationExtensionPointsAndExtensionsFrom", "configFilePath", "", "registerPluginExtensionPoints", "project", "Lcom/intellij/mock/MockProject;", "registerExtensionsFromPlugins", "registerExtensionsFromPlugins$cli_base", "registerApplicationServicesForCLI", "registerApplicationServices", "registerProjectExtensionPoints", "area", "Lcom/intellij/openapi/extensions/ExtensionsArea;", "registerProjectServices", "Lcom/intellij/core/JavaCoreProjectEnvironment;", "messageCollector", "Lorg/jetbrains/kotlin/cli/common/messages/MessageCollector;", "registerProjectServicesForCLI", "registerKotlinLightClassSupport", "cli-base"})
    @SourceDebugExtension(value={"SMAP\nKotlinCoreEnvironment.kt\nKotlin\n*S Kotlin\n*F\n+ 1 KotlinCoreEnvironment.kt\norg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment$Companion\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,846:1\n1#2:847\n*E\n"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final Object getAPPLICATION_LOCK() {
            return APPLICATION_LOCK;
        }

        @PublishedApi
        public static /* synthetic */ void getAPPLICATION_LOCK$annotations() {
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public final <R> R underApplicationLock(@NotNull Function0<? extends R> action) {
            Intrinsics.checkNotNullParameter(action, (String)"action");
            boolean $i$f$underApplicationLock = false;
            Object object = this.getAPPLICATION_LOCK();
            synchronized (object) {
                Object object2;
                try {
                    boolean bl = false;
                    object2 = action.invoke();
                }
                finally {
                    InlineMarker.finallyStart((int)1);
                    // MONITOREXIT @DISABLED, blocks:[1, 3] lbl12 : MonitorExitStatement: MONITOREXIT : var3_3
                    InlineMarker.finallyEnd((int)1);
                }
                return (R)object2;
            }
        }

        @JvmStatic
        @NotNull
        public final KotlinCoreEnvironment createForProduction(@NotNull Disposable projectDisposable, @NotNull CompilerConfiguration configuration, @NotNull EnvironmentConfigFiles configFiles) {
            Intrinsics.checkNotNullParameter((Object)projectDisposable, (String)"projectDisposable");
            Intrinsics.checkNotNullParameter((Object)configuration, (String)"configuration");
            Intrinsics.checkNotNullParameter((Object)((Object)configFiles), (String)"configFiles");
            CompatKt.setupIdeaStandaloneExecution();
            KotlinCoreApplicationEnvironment appEnv = this.getOrCreateApplicationEnvironmentForProduction(projectDisposable, configuration);
            ProjectEnvironment projectEnv = new ProjectEnvironment(projectDisposable, appEnv, configuration);
            KotlinCoreEnvironment environment = new KotlinCoreEnvironment(projectEnv, configuration, configFiles, null);
            return environment;
        }

        @JvmStatic
        @NotNull
        public final KotlinCoreEnvironment createForProduction(@NotNull ProjectEnvironment projectEnvironment, @NotNull CompilerConfiguration configuration, @NotNull EnvironmentConfigFiles configFiles) {
            Intrinsics.checkNotNullParameter((Object)((Object)projectEnvironment), (String)"projectEnvironment");
            Intrinsics.checkNotNullParameter((Object)configuration, (String)"configuration");
            Intrinsics.checkNotNullParameter((Object)((Object)configFiles), (String)"configFiles");
            return new KotlinCoreEnvironment(projectEnvironment, configuration, configFiles, null);
        }

        @JvmStatic
        @NotNull
        public final KotlinCoreEnvironment createForTests(@NotNull Disposable parentDisposable, @NotNull CompilerConfiguration initialConfiguration, @NotNull EnvironmentConfigFiles extensionConfigs) {
            Intrinsics.checkNotNullParameter((Object)parentDisposable, (String)"parentDisposable");
            Intrinsics.checkNotNullParameter((Object)initialConfiguration, (String)"initialConfiguration");
            Intrinsics.checkNotNullParameter((Object)((Object)extensionConfigs), (String)"extensionConfigs");
            CompilerConfiguration configuration = initialConfiguration.copy();
            Intrinsics.checkNotNull((Object)configuration);
            KotlinCoreApplicationEnvironment appEnv = this.createApplicationEnvironment(parentDisposable, configuration, KotlinCoreApplicationEnvironmentMode.UnitTest.INSTANCE);
            ProjectEnvironment projectEnv = new ProjectEnvironment(parentDisposable, appEnv, configuration);
            return new KotlinCoreEnvironment(projectEnv, configuration, extensionConfigs, null);
        }

        @JvmStatic
        @NotNull
        public final KotlinCoreEnvironment createForParallelTests(@NotNull Disposable projectDisposable, @NotNull CompilerConfiguration initialConfiguration, @NotNull EnvironmentConfigFiles extensionConfigs) {
            Intrinsics.checkNotNullParameter((Object)projectDisposable, (String)"projectDisposable");
            Intrinsics.checkNotNullParameter((Object)initialConfiguration, (String)"initialConfiguration");
            Intrinsics.checkNotNullParameter((Object)((Object)extensionConfigs), (String)"extensionConfigs");
            CompilerConfiguration configuration = initialConfiguration.copy();
            Intrinsics.checkNotNull((Object)configuration);
            KotlinCoreApplicationEnvironment appEnv = this.getOrCreateApplicationEnvironmentForTests(projectDisposable, configuration);
            ProjectEnvironment projectEnv = new ProjectEnvironment(projectDisposable, appEnv, configuration);
            return new KotlinCoreEnvironment(projectEnv, configuration, extensionConfigs, null);
        }

        @JvmStatic
        @NotNull
        public final KotlinCoreEnvironment createForTests(@NotNull ProjectEnvironment projectEnvironment, @NotNull CompilerConfiguration initialConfiguration, @NotNull EnvironmentConfigFiles extensionConfigs) {
            Intrinsics.checkNotNullParameter((Object)((Object)projectEnvironment), (String)"projectEnvironment");
            Intrinsics.checkNotNullParameter((Object)initialConfiguration, (String)"initialConfiguration");
            Intrinsics.checkNotNullParameter((Object)((Object)extensionConfigs), (String)"extensionConfigs");
            return new KotlinCoreEnvironment(projectEnvironment, initialConfiguration, extensionConfigs, null);
        }

        @NotNull
        public final ProjectEnvironment createProjectEnvironmentForTests(@NotNull Disposable projectDisposable, @NotNull CompilerConfiguration configuration) {
            Intrinsics.checkNotNullParameter((Object)projectDisposable, (String)"projectDisposable");
            Intrinsics.checkNotNullParameter((Object)configuration, (String)"configuration");
            KotlinCoreApplicationEnvironment appEnv = this.createApplicationEnvironment(projectDisposable, configuration, KotlinCoreApplicationEnvironmentMode.UnitTest.INSTANCE);
            return new ProjectEnvironment(projectDisposable, appEnv, configuration);
        }

        @Nullable
        public final KotlinCoreApplicationEnvironment getApplicationEnvironment() {
            return ourApplicationEnvironment;
        }

        @NotNull
        public final KotlinCoreApplicationEnvironment getOrCreateApplicationEnvironmentForProduction(@NotNull Disposable projectDisposable, @NotNull CompilerConfiguration configuration) {
            Intrinsics.checkNotNullParameter((Object)projectDisposable, (String)"projectDisposable");
            Intrinsics.checkNotNullParameter((Object)configuration, (String)"configuration");
            return this.getOrCreateApplicationEnvironment(projectDisposable, configuration, KotlinCoreApplicationEnvironmentMode.Production.INSTANCE);
        }

        @NotNull
        public final KotlinCoreApplicationEnvironment getOrCreateApplicationEnvironmentForTests(@NotNull Disposable projectDisposable, @NotNull CompilerConfiguration configuration) {
            Intrinsics.checkNotNullParameter((Object)projectDisposable, (String)"projectDisposable");
            Intrinsics.checkNotNullParameter((Object)configuration, (String)"configuration");
            return this.getOrCreateApplicationEnvironment(projectDisposable, configuration, KotlinCoreApplicationEnvironmentMode.UnitTest.INSTANCE);
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        @NotNull
        public final KotlinCoreApplicationEnvironment getOrCreateApplicationEnvironment(@NotNull Disposable projectDisposable, @NotNull CompilerConfiguration configuration, @NotNull KotlinCoreApplicationEnvironmentMode environmentMode) {
            Intrinsics.checkNotNullParameter((Object)projectDisposable, (String)"projectDisposable");
            Intrinsics.checkNotNullParameter((Object)configuration, (String)"configuration");
            Intrinsics.checkNotNullParameter((Object)environmentMode, (String)"environmentMode");
            Object object = this.getAPPLICATION_LOCK();
            synchronized (object) {
                boolean bl = false;
                if (ourApplicationEnvironment == null) {
                    Disposable disposable = Disposer.newDisposable((String)"Disposable for the KotlinCoreApplicationEnvironment");
                    Intrinsics.checkNotNullExpressionValue((Object)disposable, (String)"newDisposable(...)");
                    Disposable disposable2 = disposable;
                    ourApplicationEnvironment = Companion.createApplicationEnvironment(disposable2, configuration, environmentMode);
                    ourProjectCount = 0;
                    Disposer.register((Disposable)disposable2, Companion::getOrCreateApplicationEnvironment$lambda$3$lambda$2);
                }
                try {
                    boolean disposeAppEnv = !Intrinsics.areEqual((Object)PropertiesKt.toBooleanLenient((String)CompilerSystemProperties.KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY.getValue()), (Object)true);
                    Disposer.register((Disposable)projectDisposable, (Disposable)new Disposable(disposeAppEnv){
                        final /* synthetic */ boolean $disposeAppEnv;
                        {
                            this.$disposeAppEnv = $disposeAppEnv;
                        }

                        /*
                         * WARNING - Removed try catching itself - possible behaviour change.
                         */
                        public void dispose() {
                            Object object = KotlinCoreEnvironment.Companion.getAPPLICATION_LOCK();
                            boolean bl = this.$disposeAppEnv;
                            Object object2 = object;
                            synchronized (object2) {
                                boolean bl2 = false;
                                KotlinCoreEnvironment.access$setOurProjectCount$cp(KotlinCoreEnvironment.access$getOurProjectCount$cp() + -1);
                                if (KotlinCoreEnvironment.access$getOurProjectCount$cp() <= 0) {
                                    if (bl) {
                                        KotlinCoreEnvironment.Companion.disposeApplicationEnvironment();
                                    } else {
                                        KotlinCoreApplicationEnvironment kotlinCoreApplicationEnvironment = KotlinCoreEnvironment.access$getOurApplicationEnvironment$cp();
                                        if (kotlinCoreApplicationEnvironment != null) {
                                            kotlinCoreApplicationEnvironment.idleCleanup();
                                        }
                                    }
                                }
                                Unit unit = Unit.INSTANCE;
                            }
                        }
                    });
                }
                finally {
                    int n = ourProjectCount;
                    ourProjectCount = n + 1;
                }
                KotlinCoreApplicationEnvironment kotlinCoreApplicationEnvironment = ourApplicationEnvironment;
                Intrinsics.checkNotNull((Object)((Object)kotlinCoreApplicationEnvironment));
                KotlinCoreApplicationEnvironment kotlinCoreApplicationEnvironment2 = kotlinCoreApplicationEnvironment;
                return kotlinCoreApplicationEnvironment2;
            }
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        @JvmStatic
        public final void disposeApplicationEnvironment() {
            Object object = this.getAPPLICATION_LOCK();
            synchronized (object) {
                boolean bl = false;
                KotlinCoreApplicationEnvironment kotlinCoreApplicationEnvironment = ourApplicationEnvironment;
                if (kotlinCoreApplicationEnvironment == null) {
                    return;
                }
                KotlinCoreApplicationEnvironment environment = kotlinCoreApplicationEnvironment;
                ourApplicationEnvironment = null;
                Disposer.dispose((Disposable)environment.getParentDisposable());
                Companion.resetApplicationManager((Application)environment.getApplication());
                ZipHandler.clearFileAccessorCache();
                Unit unit = Unit.INSTANCE;
            }
        }

        @JvmStatic
        public final void resetApplicationManager(@Nullable Application applicationToReset) {
            block4: {
                Application application = ApplicationManager.getApplication();
                if (application == null) {
                    return;
                }
                Application currentApplication = application;
                if (applicationToReset != null && !Intrinsics.areEqual((Object)applicationToReset, (Object)currentApplication)) {
                    return;
                }
                try {
                    Field ourApplicationField = ApplicationManager.class.getDeclaredField("ourApplication");
                    ourApplicationField.setAccessible(true);
                    ourApplicationField.set(null, null);
                }
                catch (Exception exception) {
                    if (!currentApplication.isUnitTestMode()) break block4;
                    throw exception;
                }
            }
        }

        public static /* synthetic */ void resetApplicationManager$default(Companion companion, Application application, int n, Object object) {
            if ((n & 1) != 0) {
                application = null;
            }
            companion.resetApplicationManager(application);
        }

        @JvmStatic
        public final void configureProjectEnvironment(@NotNull ProjectEnvironment $this$configureProjectEnvironment, @NotNull CompilerConfiguration configuration, @NotNull EnvironmentConfigFiles configFiles) {
            Field field;
            Intrinsics.checkNotNullParameter((Object)((Object)$this$configureProjectEnvironment), (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)configuration, (String)"configuration");
            Intrinsics.checkNotNullParameter((Object)((Object)configFiles), (String)"configFiles");
            Field $this$configureProjectEnvironment_u24lambda_u245 = field = PersistentFSConstants.class.getDeclaredField("ourMaxIntellisenseFileSize");
            boolean bl = false;
            $this$configureProjectEnvironment_u24lambda_u245.setAccessible(true);
            field.setInt(null, FileUtilRt.LARGE_FOR_CONTENT_LOADING);
            $this$configureProjectEnvironment.registerExtensionsFromPlugins(configuration);
            boolean isJvm = configFiles == EnvironmentConfigFiles.JVM_CONFIG_FILES;
            $this$configureProjectEnvironment.getProject().registerService(ModuleVisibilityManager.class, (Object)new CliModuleVisibilityManagerImpl(isJvm));
            this.registerProjectServicesForCLI($this$configureProjectEnvironment);
            MockProject mockProject = $this$configureProjectEnvironment.getProject();
            Intrinsics.checkNotNullExpressionValue((Object)mockProject, (String)"getProject(...)");
            this.registerProjectServices(mockProject);
            MockProject mockProject2 = $this$configureProjectEnvironment.getProject();
            Intrinsics.checkNotNullExpressionValue((Object)mockProject2, (String)"getProject(...)");
            for (CompilerConfigurationExtension extension : CompilerConfigurationExtension.Companion.getInstances((Project)mockProject2)) {
                extension.updateConfiguration(configuration);
            }
        }

        // private final KotlinCoreApplicationEnvironment createApplicationEnvironment(Disposable parentDisposable, CompilerConfiguration configuration, KotlinCoreApplicationEnvironmentMode environmentMode) {
            // KotlinCoreApplicationEnvironment applicationEnvironment = KotlinCoreApplicationEnvironment.Companion.create(parentDisposable, environmentMode);
            // this.registerApplicationExtensionPointsAndExtensionsFrom(configuration, "extensions/compiler.xml");
            // this.registerApplicationServicesForCLI(applicationEnvironment);
            // this.registerApplicationServices(applicationEnvironment);
            // return applicationEnvironment;
        // }
private final KotlinCoreApplicationEnvironment createApplicationEnvironment(Disposable parentDisposable, CompilerConfiguration configuration, KotlinCoreApplicationEnvironmentMode environmentMode) {
    // 获取当前线程原始的上下文类加载器，以便稍后恢复
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try {
        // 将当前线程的上下文类加载器设置为此类的加载器（即应用的ClassLoader）
        // 这使得后续代码可以从应用的DEX文件中加载javax.xml.*等类
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

        // 执行原始的初始化逻辑
        KotlinCoreApplicationEnvironment applicationEnvironment = KotlinCoreApplicationEnvironment.Companion.create(parentDisposable, environmentMode);
        // 现在这个调用将成功，因为它可以在应用的类加载器中找到所需的XML处理类
        this.registerApplicationExtensionPointsAndExtensionsFrom(configuration, "extensions/compiler.xml");
        this.registerApplicationServicesForCLI(applicationEnvironment);
        this.registerApplicationServices(applicationEnvironment);
        return applicationEnvironment;
    } finally {
        // 无论try块中是否发生异常，都必须恢复原始的类加载器，以避免对应用的其他部分产生副作用
        Thread.currentThread().setContextClassLoader(originalClassLoader);
    }
}
        private final void registerApplicationExtensionPointsAndExtensionsFrom(CompilerConfiguration configuration, String configFilePath) {
            Object object;
            String string = (String)configuration.get(CLIConfigurationKeys.INTELLIJ_PLUGIN_ROOT);
            if (string != null) {
                String p0 = string;
                boolean bl = false;
                object = new File(p0);
            } else {
                File file;
                File it = file = PathUtil.getResourcePathForClass((Class)this.getClass());
                boolean bl = false;
                object = org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment$Companion.registerApplicationExtensionPointsAndExtensionsFrom$hasConfigFile(it, configFilePath) ? file : null;
                if (object == null) {
                    File file2;
                    File it2 = file2 = new File("compiler/cli/cli-common/resources");
                    boolean bl2 = false;
                    object = org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment$Companion.registerApplicationExtensionPointsAndExtensionsFrom$hasConfigFile(it2, configFilePath) ? file2 : null;
                    if (object == null) {
                        Object object2;
                        File file3 = (File)configuration.get(CLIConfigurationKeys.PATH_TO_KOTLIN_COMPILER_JAR);
                        if (file3 != null) {
                            File file4;
                            File it3 = file4 = file3;
                            boolean bl3 = false;
                            object2 = org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment$Companion.registerApplicationExtensionPointsAndExtensionsFrom$hasConfigFile(it3, configFilePath) ? file4 : null;
                        } else {
                            object2 = object = null;
                        }
                        if (object2 == null) {
                            ClassLoader classLoader;
                            UrlClassLoader urlClassLoader;
                            throw new IllegalStateException("Unable to find extension point configuration " + configFilePath + " (cp:\n  " + ((urlClassLoader = (classLoader = Thread.currentThread().getContextClassLoader()) instanceof UrlClassLoader ? (UrlClassLoader)classLoader : null) != null && (urlClassLoader = urlClassLoader.getUrls()) != null ? CollectionsKt.joinToString$default((Iterable)((Iterable)urlClassLoader), (CharSequence)"\n  ", null, null, (int)0, null, Companion::registerApplicationExtensionPointsAndExtensionsFrom$lambda$10, (int)30, null) : null) + ')');
                        }
                    }
                }
            }
            File pluginRoot = object;
            CoreApplicationEnvironment.registerExtensionPointAndExtensions((Path)FileSystems.getDefault().getPath(pluginRoot.getPath(), new String[0]), (String)configFilePath, (ExtensionsArea)ApplicationManager.getApplication().getExtensionArea());
        }

        @JvmStatic
        public final void registerPluginExtensionPoints(@NotNull MockProject project) {
            Intrinsics.checkNotNullParameter((Object)project, (String)"project");
            ExpressionCodegenExtension.Companion.registerExtensionPoint((Project)project);
            SyntheticResolveExtension.Companion.registerExtensionPoint((Project)project);
            SyntheticJavaResolveExtension.Companion.registerExtensionPoint((Project)project);
            ClassBuilderInterceptorExtension.Companion.registerExtensionPoint((Project)project);
            ClassGeneratorExtension.Companion.registerExtensionPoint((Project)project);
            ClassFileFactoryFinalizerExtension.Companion.registerExtensionPoint((Project)project);
            AnalysisHandlerExtension.Companion.registerExtensionPoint((Project)project);
            PackageFragmentProviderExtension.Companion.registerExtensionPoint((Project)project);
            StorageComponentContainerContributor.Companion.registerExtensionPoint((Project)project);
            DeclarationAttributeAltererExtension.Companion.registerExtensionPoint((Project)project);
            PreprocessedVirtualFileFactoryExtension.Companion.registerExtensionPoint((Project)project);
            JsSyntheticTranslateExtension.Companion.registerExtensionPoint((Project)project);
            CompilerConfigurationExtension.Companion.registerExtensionPoint((Project)project);
            CollectAdditionalSourcesExtension.Companion.registerExtensionPoint((Project)project);
            ProcessSourcesBeforeCompilingExtension.Companion.registerExtensionPoint((Project)project);
            ExtraImportsProviderExtension.Companion.registerExtensionPoint((Project)project);
            IrGenerationExtension.Companion.registerExtensionPoint((Project)project);
            ScriptEvaluationExtension.Companion.registerExtensionPoint((Project)project);
            ShellExtension.Companion.registerExtensionPoint((Project)project);
            TypeResolutionInterceptor.Companion.registerExtensionPoint((Project)project);
            CandidateInterceptor.Companion.registerExtensionPoint((Project)project);
            DescriptorSerializerPlugin.Companion.registerExtensionPoint((Project)project);
            FirExtensionRegistrarAdapter.Companion.registerExtensionPoint((Project)project);
            TypeAttributeTranslatorExtension.Companion.registerExtensionPoint((Project)project);
            AssignResolutionAltererExtension.Companion.registerExtensionPoint((Project)project);
            FirAnalysisHandlerExtension.Companion.registerExtensionPoint((Project)project);
            DiagnosticSuppressor.Companion.registerExtensionPoint((Project)project);
        }

        public final void registerExtensionsFromPlugins$cli_base(@NotNull MockProject project, @NotNull CompilerConfiguration configuration) {
            Intrinsics.checkNotNullParameter((Object)project, (String)"project");
            Intrinsics.checkNotNullParameter((Object)configuration, (String)"configuration");
            MessageCollector messageCollector = (MessageCollector)configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY);
            for (ComponentRegistrar registrar : configuration.getList(ComponentRegistrar.Companion.getPLUGIN_COMPONENT_REGISTRARS())) {
                Unit unit;
                try {
                    registrar.registerProjectComponents(project, configuration);
                    unit = Unit.INSTANCE;
                }
                catch (AbstractMethodError e) {
                    Unit unit2;
                    Intrinsics.checkNotNull((Object)registrar);
                    String message = org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment$Companion.registerExtensionsFromPlugins$createErrorMessage(registrar);
                    if (Intrinsics.areEqual((Object)registrar.getClass().getSimpleName(), (Object)"ScriptingCompilerConfigurationComponentRegistrar")) {
                        MessageCollector messageCollector2 = messageCollector;
                        if (messageCollector2 != null) {
                            MessageCollector.report$default((MessageCollector)messageCollector2, (CompilerMessageSeverity)CompilerMessageSeverity.STRONG_WARNING, (String)("Default scripting plugin is disabled: " + message), null, (int)4, null);
                            unit2 = Unit.INSTANCE;
                        } else {
                            unit2 = null;
                        }
                    } else {
                        String errorMessageWithStackTrace = message + ".\n" + CollectionsKt.joinToString$default((Iterable)((Iterable)CollectionsKt.take((Iterable)((Iterable)StringsKt.lines((CharSequence)ExceptionsKt.stackTraceToString((Throwable)e))), (int)6)), (CharSequence)"\n", null, null, (int)0, null, null, (int)62, null);
                        MessageCollector messageCollector3 = messageCollector;
                        if (messageCollector3 != null) {
                            MessageCollector.report$default((MessageCollector)messageCollector3, (CompilerMessageSeverity)CompilerMessageSeverity.ERROR, (String)errorMessageWithStackTrace, null, (int)4, null);
                            unit2 = Unit.INSTANCE;
                        } else {
                            unit2 = null;
                        }
                    }
                    unit = unit2;
                }
            }
            CompilerPluginRegistrar.ExtensionStorage extensionStorage = new CompilerPluginRegistrar.ExtensionStorage();
            Iterator iterator = configuration.getList(CompilerPluginRegistrar.Companion.getCOMPILER_PLUGIN_REGISTRARS()).iterator();
            while (iterator.hasNext()) {
                CompilerPluginRegistrar registrar;
                CompilerPluginRegistrar $this$registerExtensionsFromPlugins_u24lambda_u2411 = registrar = (CompilerPluginRegistrar)iterator.next();
                boolean bl = false;
                $this$registerExtensionsFromPlugins_u24lambda_u2411.registerExtensions(extensionStorage, configuration);
            }
            CompilerPluginRegistrarKt.registerInProject((CompilerPluginRegistrar.ExtensionStorage)extensionStorage, (Project)((Project)project), Companion::registerExtensionsFromPlugins$lambda$12);
        }

        private final void registerApplicationServicesForCLI(KotlinCoreApplicationEnvironment applicationEnvironment) {
            applicationEnvironment.registerFileType((FileType)PlainTextFileType.INSTANCE, "xml");
            applicationEnvironment.registerParserDefinition((ParserDefinition)new JavaParserDefinition());
        }

        @JvmStatic
        public final void registerApplicationServices(@NotNull KotlinCoreApplicationEnvironment applicationEnvironment) {
            Intrinsics.checkNotNullParameter((Object)((Object)applicationEnvironment), (String)"applicationEnvironment");
            KotlinCoreApplicationEnvironment $this$registerApplicationServices_u24lambda_u2413 = applicationEnvironment;
            boolean bl = false;
            $this$registerApplicationServices_u24lambda_u2413.registerFileType((FileType)KotlinFileType.INSTANCE, "kt");
            $this$registerApplicationServices_u24lambda_u2413.registerFileType((FileType)KotlinFileType.INSTANCE, KotlinParserDefinition.STD_SCRIPT_SUFFIX);
            $this$registerApplicationServices_u24lambda_u2413.registerParserDefinition((ParserDefinition)new KotlinParserDefinition());
            $this$registerApplicationServices_u24lambda_u2413.getApplication().registerService(KotlinBinaryClassCache.class, (Object)new KotlinBinaryClassCache());
            $this$registerApplicationServices_u24lambda_u2413.getApplication().registerService(JavaClassSupers.class, JavaClassSupersImpl.class);
            $this$registerApplicationServices_u24lambda_u2413.getApplication().registerService(TransactionGuard.class, TransactionGuardImpl.class);
        }

        @JvmStatic
        public final void registerProjectExtensionPoints(@NotNull ExtensionsArea area) {
            Intrinsics.checkNotNullParameter((Object)area, (String)"area");
            CoreApplicationEnvironment.registerExtensionPoint((ExtensionsArea)area, (String)PsiTreeChangePreprocessor.EP.getName(), PsiTreeChangePreprocessor.class);
            CoreApplicationEnvironment.registerExtensionPoint((ExtensionsArea)area, (String)PsiElementFinder.EP.getName(), PsiElementFinder.class);
            IdeaExtensionPoints.INSTANCE.registerVersionSpecificProjectExtensionPoints(area);
        }

        @JvmStatic
        @Deprecated(message="Use registerProjectServices(project) instead.", replaceWith=@ReplaceWith(expression="registerProjectServices(projectEnvironment.project)", imports={}))
        public final void registerProjectServices(@NotNull JavaCoreProjectEnvironment projectEnvironment, @Nullable MessageCollector messageCollector) {
            Intrinsics.checkNotNullParameter((Object)projectEnvironment, (String)"projectEnvironment");
            MockProject mockProject = projectEnvironment.getProject();
            Intrinsics.checkNotNullExpressionValue((Object)mockProject, (String)"getProject(...)");
            this.registerProjectServices(mockProject);
        }

        @JvmStatic
        public final void registerProjectServices(@NotNull MockProject project) {
            Intrinsics.checkNotNullParameter((Object)project, (String)"project");
            MockProject $this$registerProjectServices_u24lambda_u2414 = project;
            boolean bl = false;
            $this$registerProjectServices_u24lambda_u2414.registerService(JavaElementSourceFactory.class, JavaFixedElementSourceFactory.class);
            $this$registerProjectServices_u24lambda_u2414.registerService(KotlinJavaPsiFacade.class, (Object)new KotlinJavaPsiFacade((Project)$this$registerProjectServices_u24lambda_u2414));
            $this$registerProjectServices_u24lambda_u2414.registerService(ModuleAnnotationsResolver.class, (Object)new CliModuleAnnotationsResolver());
        }

        public final void registerProjectServicesForCLI(@NotNull JavaCoreProjectEnvironment projectEnvironment) {
            Intrinsics.checkNotNullParameter((Object)projectEnvironment, (String)"projectEnvironment");
        }

        @JvmStatic
        public final void registerKotlinLightClassSupport(@NotNull MockProject project) {
            Intrinsics.checkNotNullParameter((Object)project, (String)"project");
            MockProject $this$registerKotlinLightClassSupport_u24lambda_u2415 = project;
            boolean bl = false;
            CliTraceHolder traceHolder = new CliTraceHolder((Project)project);
            CliLightClassGenerationSupport cliLightClassGenerationSupport = new CliLightClassGenerationSupport(traceHolder, (Project)project);
            CliKotlinAsJavaSupport kotlinAsJavaSupport = new CliKotlinAsJavaSupport((Project)project, traceHolder);
            $this$registerKotlinLightClassSupport_u24lambda_u2415.registerService(LightClassGenerationSupport.class, (Object)cliLightClassGenerationSupport);
            $this$registerKotlinLightClassSupport_u24lambda_u2415.registerService(CliLightClassGenerationSupport.class, (Object)cliLightClassGenerationSupport);
            $this$registerKotlinLightClassSupport_u24lambda_u2415.registerService(KotlinAsJavaSupport.class, (Object)kotlinAsJavaSupport);
            $this$registerKotlinLightClassSupport_u24lambda_u2415.registerService(CodeAnalyzerInitializer.class, (Object)traceHolder);
            PsiElementFinder.EP.getPoint((AreaInstance)project).registerExtension((Object)new JavaElementFinder((Project)$this$registerKotlinLightClassSupport_u24lambda_u2415));
            PsiElementFinder.EP.getPoint((AreaInstance)project).registerExtension((Object)new PsiElementFinderImpl((Project)$this$registerKotlinLightClassSupport_u24lambda_u2415));
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        private static final void getOrCreateApplicationEnvironment$lambda$3$lambda$2() {
            Object object = Companion.getAPPLICATION_LOCK();
            synchronized (object) {
                boolean bl = false;
                ourApplicationEnvironment = null;
                Unit unit = Unit.INSTANCE;
            }
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        private static final boolean registerApplicationExtensionPointsAndExtensionsFrom$hasConfigFile(File $this$registerApplicationExtensionPointsAndExtensionsFrom_u24hasConfigFile, String configFile) {
            boolean bl;
            if ($this$registerApplicationExtensionPointsAndExtensionsFrom_u24hasConfigFile.isDirectory()) {
                bl = new File($this$registerApplicationExtensionPointsAndExtensionsFrom_u24hasConfigFile, "META-INF" + File.separator + configFile).exists();
            } else {
                boolean bl2;
                try {
                    boolean bl3;
                    Closeable closeable = (Closeable)new ZipFile($this$registerApplicationExtensionPointsAndExtensionsFrom_u24hasConfigFile);
                    Throwable throwable = null;
                    try {
                        ZipFile it = (ZipFile)closeable;
                        boolean bl4 = false;
                        bl3 = it.getEntry("META-INF/" + configFile) != null;
                    }
                    catch (Throwable throwable2) {
                        throwable = throwable2;
                        throw throwable2;
                    }
                    finally {
                        CloseableKt.closeFinally((Closeable)closeable, (Throwable)throwable);
                    }
                    bl2 = bl3;
                }
                catch (Throwable e) {
                    bl2 = false;
                }
                bl = bl2;
            }
            return bl;
        }

        private static final CharSequence registerApplicationExtensionPointsAndExtensionsFrom$lambda$10(URL it) {
            String string = it.getFile();
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getFile(...)");
            return string;
        }

        private static final String registerExtensionsFromPlugins$createErrorMessage(Object extension) {
            return "The provided plugin " + extension.getClass().getName() + " is not compatible with this version of compiler";
        }

        private static final String registerExtensionsFromPlugins$lambda$12(Object it) {
            Intrinsics.checkNotNullParameter((Object)it, (String)"it");
            return org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment$Companion.registerExtensionsFromPlugins$createErrorMessage(it);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }

    @Metadata(mv={2, 0, 0}, k=1, xi=48, d1={" 4\n\n\n \n\n \n\n \n\n\b\n\n\b\n\n \n\n\b 20B000¢\b\b\tJ\b0HJ020J\b0HR\n0¢\b\n \b\f\rR0X¢\n ¨"}, d2={"Lorg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment$ProjectEnvironment;", "Lorg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreProjectEnvironment;", "disposable", "Lcom/intellij/openapi/Disposable;", "applicationEnvironment", "Lorg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreApplicationEnvironment;", "configuration", "Lorg/jetbrains/kotlin/config/CompilerConfiguration;", "<init>", "(Lcom/intellij/openapi/Disposable;Lorg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreApplicationEnvironment;Lorg/jetbrains/kotlin/config/CompilerConfiguration;)V", "jarFileSystem", "Lcom/intellij/openapi/vfs/VirtualFileSystem;", "getJarFileSystem", "()Lcom/intellij/openapi/vfs/VirtualFileSystem;", "extensionRegistered", "", "preregisterServices", "", "registerExtensionsFromPlugins", "registerJavaPsiFacade", "cli-base"})
    @SourceDebugExtension(value={"SMAP\nKotlinCoreEnvironment.kt\nKotlin\n*S Kotlin\n*F\n+ 1 KotlinCoreEnvironment.kt\norg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment$ProjectEnvironment\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,846:1\n1755#2,3:847\n*S KotlinDebug\n*F\n+ 1 KotlinCoreEnvironment.kt\norg/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment$ProjectEnvironment\n*L\n155#1:847,3\n*E\n"})
    public static final class ProjectEnvironment
    extends KotlinCoreProjectEnvironment {
        @NotNull
        private final VirtualFileSystem jarFileSystem;
        private boolean extensionRegistered;

        /*
         * WARNING - void declaration
         */
        public ProjectEnvironment(@NotNull Disposable disposable, @NotNull KotlinCoreApplicationEnvironment applicationEnvironment, @NotNull CompilerConfiguration configuration) {
            VirtualFileSystem virtualFileSystem;
            Intrinsics.checkNotNullParameter((Object)disposable, (String)"disposable");
            Intrinsics.checkNotNullParameter((Object)((Object)applicationEnvironment), (String)"applicationEnvironment");
            Intrinsics.checkNotNullParameter((Object)configuration, (String)"configuration");
            super(disposable, applicationEnvironment);
            MessageCollector messageCollector = (MessageCollector)configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY);
            UtilKt.setIdeaIoUseFallback();
            if (configuration.getBoolean(JVMConfigurationKeys.USE_FAST_JAR_FILE_SYSTEM)) {
                MessageCollector messageCollector2 = messageCollector;
                if (messageCollector2 != null) {
                    MessageCollector.report$default((MessageCollector)messageCollector2, (CompilerMessageSeverity)CompilerMessageSeverity.STRONG_WARNING, (String)"Using new faster version of JAR FS: it should make your build faster, but the new implementation is experimental", null, (int)4, null);
                }
            }
            ProjectEnvironment projectEnvironment = this;
            if (configuration.getBoolean(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING)) {
                virtualFileSystem = applicationEnvironment.getJarFileSystem();
            } else if (configuration.getBoolean(JVMConfigurationKeys.USE_FAST_JAR_FILE_SYSTEM) || configuration.getBoolean(CommonConfigurationKeys.USE_FIR)) {
                FastJarFileSystem fastJarFs = applicationEnvironment.getFastJarFileSystem();
                if (fastJarFs == null) {
                    MessageCollector messageCollector3 = messageCollector;
                    if (messageCollector3 != null) {
                        MessageCollector.report$default((MessageCollector)messageCollector3, (CompilerMessageSeverity)CompilerMessageSeverity.STRONG_WARNING, (String)"Your JDK doesn't seem to support mapped buffer unmapping, so the slower (old) version of JAR FS will be used", null, (int)4, null);
                    }
                    virtualFileSystem = applicationEnvironment.getJarFileSystem();
                } else {
                    File outputJar = (File)configuration.get(JVMConfigurationKeys.OUTPUT_JAR);
                    if (outputJar == null) {
                        virtualFileSystem = (VirtualFileSystem)fastJarFs;
                    } else {
                        boolean bl;
                        List contentRoots;
                        List list = contentRoots = (List)configuration.get(CLIConfigurationKeys.CONTENT_ROOTS);
                        if (list != null) {
                            boolean bl2;
                            ProjectEnvironment projectEnvironment2;
                            block19: {
                                void $this$any$iv;
                                Iterable iterable = (Iterable)list;
                                projectEnvironment2 = projectEnvironment;
                                boolean $i$f$any = false;
                                if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                                    bl2 = false;
                                } else {
                                    for (Object element$iv : $this$any$iv) {
                                        ContentRoot it = (ContentRoot)element$iv;
                                        boolean bl3 = false;
                                        if (!(it instanceof JvmClasspathRoot && Intrinsics.areEqual((Object)((JvmClasspathRoot)it).getFile().getPath(), (Object)outputJar.getPath()))) continue;
                                        bl2 = true;
                                        break block19;
                                    }
                                    bl2 = false;
                                }
                            }
                            boolean bl4 = bl2;
                            projectEnvironment = projectEnvironment2;
                            bl = bl4;
                        } else {
                            bl = false;
                        }
                        if (bl) {
                            MessageCollector messageCollector4 = messageCollector;
                            if (messageCollector4 != null) {
                                MessageCollector.report$default((MessageCollector)messageCollector4, (CompilerMessageSeverity)CompilerMessageSeverity.STRONG_WARNING, (String)("JAR from the classpath " + outputJar.getPath() + " is reused as output JAR, so the slower (old) version of JAR FS will be used"), null, (int)4, null);
                            }
                            virtualFileSystem = applicationEnvironment.getJarFileSystem();
                        } else {
                            virtualFileSystem = (VirtualFileSystem)fastJarFs;
                        }
                    }
                }
            } else {
                virtualFileSystem = applicationEnvironment.getJarFileSystem();
            }
            projectEnvironment.jarFileSystem = virtualFileSystem;
        }

        @NotNull
        public final VirtualFileSystem getJarFileSystem() {
            return this.jarFileSystem;
        }

        protected void preregisterServices() {
            ExtensionsAreaImpl extensionsAreaImpl = this.getProject().getExtensionArea();
            Intrinsics.checkNotNullExpressionValue((Object)extensionsAreaImpl, (String)"getExtensionArea(...)");
            Companion.registerProjectExtensionPoints((ExtensionsArea)extensionsAreaImpl);
        }

        public final void registerExtensionsFromPlugins(@NotNull CompilerConfiguration configuration) {
            Intrinsics.checkNotNullParameter((Object)configuration, (String)"configuration");
            if (!this.extensionRegistered) {
                MockProject mockProject = this.getProject();
                Intrinsics.checkNotNullExpressionValue((Object)mockProject, (String)"getProject(...)");
                Companion.registerPluginExtensionPoints(mockProject);
                MockProject mockProject2 = this.getProject();
                Intrinsics.checkNotNullExpressionValue((Object)mockProject2, (String)"getProject(...)");
                Companion.registerExtensionsFromPlugins$cli_base(mockProject2, configuration);
                this.extensionRegistered = true;
            }
        }

        protected void registerJavaPsiFacade() {
            MockProject $this$registerJavaPsiFacade_u24lambda_u241 = this.getProject();
            boolean bl = false;
            Object object = $this$registerJavaPsiFacade_u24lambda_u241.getService(JavaFileManager.class);
            Intrinsics.checkNotNull((Object)object, (String)"null cannot be cast to non-null type com.intellij.core.CoreJavaFileManager");
            $this$registerJavaPsiFacade_u24lambda_u241.registerService(CoreJavaFileManager.class, (Object)((CoreJavaFileManager)object));
            MockProject mockProject = this.getProject();
            Intrinsics.checkNotNullExpressionValue((Object)mockProject, (String)"getProject(...)");
            Companion.registerKotlinLightClassSupport(mockProject);
            $this$registerJavaPsiFacade_u24lambda_u241.registerService(ExternalAnnotationsManager.class, (Object)new MockExternalAnnotationsManager());
            $this$registerJavaPsiFacade_u24lambda_u241.registerService(InferredAnnotationsManager.class, (Object)new MockInferredAnnotationsManager());
            super.registerJavaPsiFacade();
        }
    }
}

