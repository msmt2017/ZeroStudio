
package com.itsaky.androidide.templates.base.modules.android

import com.itsaky.androidide.templates.ModuleTemplate
import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.AndroidModuleTemplateConfigurator
import com.itsaky.androidide.templates.base.ProjectTemplateBuilder
import com.itsaky.androidide.templates.base.baseAndroidXDependencies
import com.itsaky.androidide.templates.base.util.AndroidManifestBuilder.ConfigurationType.APPLICATION_ATTR


/**
 * Configure the default template for the project.
 *
 * @param name The name of the module (gradle format, e.g. ':app').
 * @param copyDefAssets Whether to copy the default Android assets (except `values` directory) to this module.
 * @param block The module configurator.
 */
public inline  fun ProjectTemplateBuilder.defaultAppModule(name: String = ":app",
                                            addAndroidX: Boolean = true,
                                            copyDefAssets: Boolean = true,
                                            crossinline block: AndroidModuleTemplateConfigurator
) {
  check(
    defModuleTemplate == null) { "Default module has been already configured" }

  val module = AndroidModuleTemplateBuilder().apply {
    _name = name
    templateName = 0
    thumb = 0

    preRecipe = commonPreRecipe {
      return@commonPreRecipe defModule
    }

    postRecipe = commonPostRecipe {
      if (copyDefAssets) {
        copyDefaultRes()

        // add manifest attributes for data extraction rules
        // and backup rules
        manifest {
          configure(APPLICATION_ATTR) {
            androidAttribute("dataExtractionRules",
              "@xml/data_extraction_rules")

            androidAttribute("fullBackupContent", "@xml/backup_rules")
          }
        }
      }
    }

    if (addAndroidX) {
      baseAndroidXDependencies()
    }

    block()
  }.build() as ModuleTemplate

  modules.add(module)
}