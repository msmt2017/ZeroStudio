

package com.itsaky.androidide.templates.base

import com.itsaky.androidide.templates.EMPTY_RECIPE
import com.itsaky.androidide.templates.RecipeExecutor
import com.itsaky.androidide.templates.TemplateBuilder
import com.itsaky.androidide.templates.TemplateData
import com.itsaky.androidide.templates.TemplateRecipe
import com.itsaky.androidide.templates.TemplateRecipeConfigurator
import com.itsaky.androidide.templates.TemplateRecipeFinalizer
import com.itsaky.androidide.templates.TemplateRecipeResult

sealed class PrePostRecipeTemplateBuilder<R : TemplateRecipeResult> :
  TemplateBuilder<R>() {

  // @PublishedApi
  public var preRecipe: TemplateRecipeConfigurator = {}

 // @PublishedApi
  public var postRecipe: TemplateRecipeFinalizer = {}

  private var _recipe: TemplateRecipe<R>? = null

  val isRecipeSet: Boolean
    get() = _recipe != null

  override var recipe: TemplateRecipe<R>?
    get() = TemplateRecipe {
      preRecipe(it)
      val result = (_recipe ?: EMPTY_RECIPE).execute(it)
      postRecipe(it)
      result
    }
    set(value) {
      _recipe = value
    }
}

/**
 * @property executor The [RecipeExecutor] instance.
 * @property data The project template data.
 */
sealed class ExecutorDataTemplateBuilder<R : TemplateRecipeResult, D : TemplateData> :
  PrePostRecipeTemplateBuilder<R>() {

 // @PublishedApi
  public var _executor: RecipeExecutor? = null

 // @PublishedApi
  public var _data: D? = null

  val executor: RecipeExecutor
    get() = checkNotNull(_executor)

  val data: D
    get() = checkNotNull(_data)
}