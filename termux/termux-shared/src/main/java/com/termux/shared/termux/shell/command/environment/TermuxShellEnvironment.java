package com.termux.shared.termux.shell.command.environment;

import static com.itsaky.androidide.utils.Environment.ANDROID_HOME;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.environment.AndroidShellEnvironment;
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils;
import com.termux.shared.shell.command.environment.ShellCommandShellEnvironment;
import com.termux.shared.termux.TermuxBootstrap;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.TermuxShellUtils;

import android.os.Build;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.Charset;
import java.util.HashMap;

/**
 * Environment for Termux.
 */
public class TermuxShellEnvironment extends AndroidShellEnvironment {

    private static final String LOG_TAG = "TermuxShellEnvironment";

    /** Environment variable for the termux {@link TermuxConstants#TERMUX_PREFIX_DIR_PATH}. */
    public static final String ENV_PREFIX = "PREFIX";


    public static boolean ProotMod;
  // public static final boolean ProotMod = !"com.termux".equals(TermuxConstants.TERMUX_PACKAGE_NAME);
    //proot路径
	public static String PROOT_PATH;
	///data/data/包名 路径
	public static String PACKAGE_NAME_PATH;
	// /linkerconfig/ld.config.txt路径
	public static String PROOT_TMP_DIR;

    public TermuxShellEnvironment() {
        super();
        shellCommandShellEnvironment = new TermuxShellCommandShellEnvironment();
    }


    /** Init {@link TermuxShellEnvironment} constants and caches. */
    public synchronized static void init(@NonNull Context currentPackageContext) {
        TermuxAppShellEnvironment.setTermuxAppEnvironment(currentPackageContext);
        initProotEnv(currentPackageContext);
    }


    /** Init {@link TermuxShellEnvironment} constants and caches. */
    public synchronized static void writeEnvironmentToFile(@NonNull Context currentPackageContext) {
        HashMap<String, String> environmentMap = new TermuxShellEnvironment().getEnvironment(currentPackageContext, false);
        String environmentString = ShellEnvironmentUtils.convertEnvironmentToDotEnvFile(environmentMap);

        // Write environment string to temp file and then move to final location since otherwise
        // writing may happen while file is being sourced/read
        Error error = FileUtils.writeTextToFile("termux.env.tmp", TermuxConstants.TERMUX_ENV_TEMP_FILE_PATH,
            Charset.defaultCharset(), environmentString, false);
        if (error != null) {
            Logger.logErrorExtended(LOG_TAG, error.toString());
            return;
        }

        error = FileUtils.moveRegularFile("termux.env.tmp", TermuxConstants.TERMUX_ENV_TEMP_FILE_PATH, TermuxConstants.TERMUX_ENV_FILE_PATH, true);
        if (error != null) {
            Logger.logErrorExtended(LOG_TAG, error.toString());
        }
    }

    /** Get shell environment for Termux. */
    @NonNull
    @Override
    public HashMap<String, String> getEnvironment(@NonNull Context currentPackageContext, boolean isFailSafe) {

        // Termux environment builds upon the Android environment
        HashMap<String, String> environment = super.getEnvironment(currentPackageContext, isFailSafe);

        HashMap<String, String> termuxAppEnvironment = TermuxAppShellEnvironment.getEnvironment(currentPackageContext);
        if (termuxAppEnvironment != null)
            environment.putAll(termuxAppEnvironment);

        environment.put(ENV_HOME, TermuxConstants.TERMUX_HOME_DIR_PATH);
        environment.put(ENV_PREFIX, TermuxConstants.TERMUX_PREFIX_DIR_PATH);

        // If failsafe is not enabled, then we keep default PATH and TMPDIR so that system binaries can be used
        if (!isFailSafe) {
            environment.put(ENV_TMPDIR, TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH);
            environment.put(ENV_PATH, TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + String.format(":%s/cmdline-tools/latest/bin", ANDROID_HOME.getAbsolutePath()));
            environment.remove(ENV_LD_LIBRARY_PATH);
        }

        putCustomizeEnv(environment);
        
        return environment;
    }

    private static void initProotEnv(Context currentPackageContext) {

		if (TermuxShellEnvironment.PROOT_PATH != null) {
			return;
		}

		try {
			TermuxShellEnvironment.ProotMod = currentPackageContext
					.getApplicationInfo().targetSdkVersion == Build.VERSION_CODES.P;
		} catch (Throwable e) {
			TermuxShellEnvironment.ProotMod = true;
		}

		if (TermuxShellEnvironment.PROOT_PATH == null) {
			TermuxShellEnvironment.PROOT_PATH = currentPackageContext.getApplicationInfo().nativeLibraryDir
					+ "/libproot.so";
		}

		if (TermuxShellEnvironment.PACKAGE_NAME_PATH == null) {
			TermuxShellEnvironment.PACKAGE_NAME_PATH = currentPackageContext.getDataDir().getAbsolutePath();
		}

		TermuxShellEnvironment.PROOT_TMP_DIR = new File(TermuxShellEnvironment.PROOT_PATH).getParent();

		File cacheDirFile = new File(PACKAGE_NAME_PATH, "cache");
		if (!cacheDirFile.exists()) {
			cacheDirFile.mkdir();
		}
		File ld_config_txt_file = new File(cacheDirFile, "ld.config.txt");
		if (!ld_config_txt_file.exists() || ld_config_txt_file.length() == 0) {
			try {
				Files.copy(Paths.get("/linkerconfig/ld.config.txt"), ld_config_txt_file.toPath(),
						StandardCopyOption.REPLACE_EXISTING);
				ld_config_txt_file.setReadable(true, false);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
    }


    @NonNull
    @Override
    public String getDefaultWorkingDirectoryPath() {
        return TermuxConstants.TERMUX_HOME_DIR_PATH;
    }

    @NonNull
    @Override
    public String getDefaultBinPath() {
        return TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH;
    }

    @NonNull
    @Override
    public String[] setupShellCommandArguments(@NonNull String executable, String[] arguments) {
        return TermuxShellUtils.setupShellCommandArguments(executable, arguments);
    }
    
    private void putCustomizeEnv(HashMap<String, String> environment) {
        environment.put("PROOT_TMP_DIR", PROOT_TMP_DIR);
        environment.put("ANDROID_HOME", String.valueOf(TermuxConstants.TERMUX_HOME_DIR_PATH) + "/android-sdk");
        environment.put("GRADLE_HOME", String.valueOf(TermuxConstants.TERMUX_HOME_DIR_PATH) + "/.gradle");
        environment.put("GRADLE", "bash ./gradlew -Pandroid.aapt2FromMavenOverride=" + TermuxConstants.TERMUX_HOME_DIR_PATH + "/.androidide/aapt2");
        environment.put("JAVA_TOOL_OPTIONS", "-Duser.language=zh -Duser.region=CN");
    }
    
}
