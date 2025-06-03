/*
* Dex-Editor-Android an Advanced Dex Editor for Android
* Copyright 2024-25, developer-krushna
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted permitted that the following conditions are
* met:
*
* * Redistributions of source code must retain the above copyright
* notice, this list of conditions and the following disclaimer.
* * Redistributions in binary form must reproduce the above
* copyright notice, this list of conditions and the following disclaimer
* in the documentation and/or other materials provided with the
* distribution.
* * Neither the name of developer-krushna nor the names of its
* contributors may be used to endorse or promote products derived from
* this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
* "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
* LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
* A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
* OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
* SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
* LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
* DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
* THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
* (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
* OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.


* Please contact Krushna by email mt.modder.hub@gmail.com if you need
* additional information or have any questions
*/


package modder.hub.dexeditor.activity;

import android.Manifest;
import android.animation.*;
import android.app.*;
import android.app.Activity;
import android.content.*;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.*;
import android.os.*;
import android.text.*;
import android.text.method.LinkMovementMethod;
import android.text.style.*;
import android.util.*;
import android.view.*;
import android.view.View.*;
import android.view.animation.*;
import android.webkit.*;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.android.tools.smali.baksmali.Adaptors.ClassDefinition;
import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.smali.SmaliOptions;
import com.android.tools.smali.smali2.Smali;

import com.blogspot.atifsoftwares.animatoolib.Animatoo;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.textfield.TextInputLayout;

import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.*;
import io.github.rosemoe.sora.event.Event;
import io.github.rosemoe.sora.event.EventReceiver;
import io.github.rosemoe.sora.event.Unsubscribe;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow;
import io.github.rosemoe.sora.widget.component.EditorBuiltinComponent;
import io.github.rosemoe.sora.text.Content;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import modder.hub.dexeditor.fragment.*;
import modder.hub.dexeditor.smali.Smali2Java;
import modder.hub.dexeditor.views.*;
import modder.hub.dexeditor.utils.*;
import modder.hub.dexeditor.R;


import org.eclipse.tm4e.core.registry.IGrammarSourceMT;
import org.eclipse.tm4e.core.registry.IThemeSourceMT;

import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import org.eclipse.tm4e.core.registry.IThemeSourceMT;
import androidx.appcompat.app.AppCompatActivity;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.SymbolInputView;

import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;
import io.github.rosemoe.sora.lang.completion.CompletionItem;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.text.CharPosition;
import modder.hub.dexeditor.adapter.*;

/*
Author @developer-krushna
Code fixed comments by ChatGPT
Thanks to @AndroidPrimwe
*/

/**
 * 中文注释: TextEditorActivity 类是 Dex-Editor-Android 应用中的核心文本编辑器活动，
 * 主要用于显示、编辑 Smali 代码。它集成了代码高亮、自动完成、语法检查、文件保存、
 * 跳转到行、方法和字段导航、Smali 到 Java 转换以及文本操作（如注释、删除行、缩进）等功能。
 * 这个活动是用户与 Smali 代码进行交互的主要界面。
 * English annotation: The TextEditorActivity class is the core text editor activity in the Dex-Editor-Android application,
 * mainly used for displaying and editing Smali code. It integrates features such as code highlighting,
 * auto-completion, syntax checking, file saving, jumping to lines, method and field navigation,
 * Smali to Java conversion, and text operations (like commenting, deleting lines, indentation).
 * This activity serves as the primary interface for user interaction with Smali code.
 *
 * @implements SmaliMethodListFragment.DialogLineNumberListener 中文注释: 实现了 SmaliMethodListFragment.DialogLineNumberListener 接口，用于从方法列表中获取行号并跳转到编辑器中的相应位置。
 * English annotation: Implements the SmaliMethodListFragment.DialogLineNumberListener interface, used to get line numbers from the method list and jump to the corresponding position in the editor.
 */
public class TextEditorActivity extends AppCompatActivity implements SmaliMethodListFragment.DialogLineNumberListener {
	/**
	 * 中文注释: 用于存储类结构信息的静态变量。
	 * English annotation: Static variable used to store class tree information.
	 */
	public static ClassTree classTree;
	/**
	 * 中文注释: 当前文件的标题，通常是文件名。
	 * English annotation: The current file's title, usually the file name.
	 */
	private String currentTitle;
	/**
	 * 中文注释: 应用栏布局，通常包含工具栏。
	 * English annotation: The app bar layout, typically containing the toolbar.
	 */
	private AppBarLayout appBarLayout;
	/**
	 * 中文注释: 协调器布局，用于管理浮动操作按钮和应用栏行为。
	 * English annotation: Coordinator layout, used to manage floating action buttons and app bar behavior.
	 */
	private CoordinatorLayout coordinatorLayout;
	/**
	 * 中文注释: 应用的工具栏。
	 * English annotation: The application's toolbar.
	 */
	private Toolbar toolbar;
	/**
	 * 中文注释: 显示操作进度的对话框。
	 * English annotation: Dialog to show operation progress.
	 */
	private ProgressDialog progressDialog;
	/**
	 * 中文注释: 代码编辑器中的光标对象，用于获取和设置光标位置。
	 * English annotation: Cursor object in the code editor, used to get and set cursor position.
	 */
	private Cursor cursor;
	/**
	 * 中文注释: 当前打开的 Dex 文件的版本。
	 * English annotation: The version of the currently opened Dex file.
	 */
	private int dexVersion;
	/**
	 * 中文注释: 活动的菜单对象。
	 * English annotation: The activity's menu object.
	 */
	private Menu menu;
	/**
	 * 中文注释: 包管理器，用于查询应用程序信息。
	 * English annotation: Package manager, used to query application information.
	 */
	private PackageManager packageManager;
	/**
	 * 中文注释: SharedPreferences 编辑器，用于修改偏好设置。
	 * English annotation: SharedPreferences editor, used to modify preferences.
	 */
	private SharedPreferences.Editor preferencesEditor;
	/**
	 * 中文注释: 菜单项，用于执行“重做”操作。
	 * English annotation: Menu item for performing "redo" action.
	 */
	private MenuItem redoMenuItem;
	/**
	 * 中文注释: SharedPreferences 对象，用于存储和检索偏好设置。
	 * English annotation: SharedPreferences object, used to store and retrieve preferences.
	 */
	private SharedPreferences sharedPreferences;

	/**
	 * 中文注释: 用于显示和编辑 Smali 代码的核心组件。
	 * English annotation: The core component for displaying and editing Smali code.
	 */
	private CodeEditor smaliEditor;
	/**
	 * 中文注释: 头部线性布局。
	 * English annotation: Header linear layout.
	 */
	private LinearLayout linearHeader;
	/**
	 * 中文注释: 左侧线性布局。
	 * English annotation: Left linear layout.
	 */
	private LinearLayout linearLeft;
	/**
	 * 中文注释: 左侧文本视图，可能显示当前类名或文件路径。
	 * English annotation: Left text view, possibly displaying current class name or file path.
	 */
	private TextView textviewLeft;
	/**
	 * 中文注释: 右侧线性布局。
	 * English annotation: Right linear layout.
	 */
	private LinearLayout linearRight;
	/**
	 * 中文注释: 行号文本视图，显示当前光标所在的行号和列号。
	 * English annotation: Line number text view, displaying the current line and column number of the cursor.
	 */
	private TextView textviewLineNo;
	/**
	 * 中文注释: 方法名文本视图，显示光标所在的方法或字段名称。
	 * English annotation: Method name text view, displaying the method or field name at the cursor.
	 */
	private TextView methodName;
	/**
	 * 中文注释: 符号输入视图，提供Smali常用符号的快速输入。
	 * English annotation: Symbol input view, providing quick input for common Smali symbols.
	 */
	private SymbolInputView symbol_input;

	/**
	 * 中文注释: 定时任务，可能用于定期保存或更新UI。
	 * English annotation: Timer task, possibly for periodic saving or UI updates.
	 */
	private TimerTask timerTask;
	/**
	 * 中文注释: 菜单项，用于执行“撤消”操作。
	 * English annotation: Menu item for performing "undo" action.
	 */
	private MenuItem undoMenuItem;
	/**
	 * 中文注释: 定时器对象。
	 * English annotation: Timer object.
	 */
	private Timer timer = new Timer();
	/**
	 * 中文注释: 指示文件是否处于编辑模式（已修改但未保存）。
	 * English annotation: Indicates if the file is in edit mode (modified but not saved).
	 */
	private boolean isEditMode = false;
	/**
	 * 中文注释: 指示临时 Smali 文件是否已创建。
	 * English annotation: Indicates if the temporary Smali file has been created.
	 */
	private String isFileCreated = "";
	/**
	 * 中文注释: 保存事件的状态字符串。
	 * English annotation: String representing the state of the save event.
	 */
	private String saveEvent = "";
	/**
	 * 中文注释: 指示是否在启动时保存文件。
	 * English annotation: Indicates whether to save the file on startup.
	 */
	private String saveOnStart = "";
	/**
	 * 中文注释: 临时 Smali 文件的路径。
	 * English annotation: Path to the temporary Smali file.
	 */
	private String tempSmaliPath = "";
	/**
	 * 中文注释: 通过 Intent 传递的类名。
	 * English annotation: Class name passed via Intent.
	 */
	private String intentClassName = "";
	/**
	 * 中文注释: 保存编译错误信息。
	 * English annotation: Stores compilation error messages.
	 */
	private String saveCompileError = "";
	/**
	 * 中文注释: Intent 对象，用于启动其他活动或传递数据。
	 * English annotation: Intent object, used to start other activities or pass data.
	 */
	private Intent intent = new Intent();


	/**
	 * 中文注释: Smali 方法列表片段的静态实例。
	 * English annotation: Static instance of Smali method list fragment.
	 */
	public static SmaliMethodListFragment smaliMethodsStringsFragment = null;
	/**
	 * 中文注释: 用于保存方法列表 RecyclerView 的状态。
	 * English annotation: Used to save the state of the method list RecyclerView.
	 */
	public static Parcelable methodRecyclerViewState = null;
	/**
	 * 中文注释: 用于保存字符串列表 RecyclerView 的状态。
	 * English annotation: Used to save the state of the strings list RecyclerView.
	 */
	public static Parcelable stringsRecyclerViewState = null;
	/**
	 * 中文注释: 指示字符串列表是否可见。
	 * English annotation: Indicates if the strings list was visible.
	 */
	public static boolean wasStringsVisible = false;
	/**
	 * 中文注释: 最后一次打开的 Smali 文件的路径。
	 * English annotation: Path of the last opened Smali file.
	 */
	public static String lastSmaliFilePath = "";
	/**
	 * 中文注释: 最后一次修改时间戳。
	 * English annotation: Last modification timestamp.
	 */
	public static long lastModifiedTime = -1;


	/**
	 * 中文注释: Dex 偏好设置。
	 * English annotation: Dex preferences.
	 */
	private SharedPreferences dexPref;
	/**
	 * 中文注释: 编辑器偏好设置。
	 * English annotation: Editor preferences.
	 */
	private SharedPreferences editorPrefs;


	/**
	 * 中文注释: 保存的字体类型。
	 * English annotation: Saved font type.
	 */
	private String savedFont = "normal";
	/**
	 * 中文注释: 指示是否需要重新加载编辑器内容。
	 * English annotation: Indicates if the editor content needs to be reloaded.
	 */
	private boolean isReload = false;

	/**
	 * 中文注释: Smali 指令助手，提供 Smali 指令相关功能。
	 * English annotation: Smali instruction helper, provides Smali instruction related functionalities.
	 */
	public SmaliInstructionHelper smaliInstructionHelper;

	/**
	 * 中文注释: Smali 语言中常用的符号数组。
	 * English annotation: Array of common symbols in Smali language.
	 */
	public static final String[] SYMBOLS = new String[] {
		"->", "{", "}", "(", ")",
		",", ".", ";", "\"", "?",
		"+", "-", "*", "/", "<",
		">", "[", "]", ":"
	};

	/**
	 * 中文注释: 与上述符号对应的要提交到编辑器的文本。
	 * English annotation: Texts to be committed to editor for symbols above.
	 */
	public static final String[] SYMBOL_INSERT_TEXT = new String[] {
		"\t", "{}", "}", "(", ")",
		",", ".", ";", "\"", "?",
		"+", "-", "*", "/", "<",
		">", "[", "]", ":"
	};


	/**
	 * 中文注释: 文件保存回调接口，用于在文件保存完成后通知。
	 * English annotation: File save callback interface, used to notify when a file save is complete.
	 */
	public interface FileSaveCallback {
		/**
		 * 中文注释: 文件保存完成时调用的方法。
		 * English annotation: Method called when the file has been saved.
		 * @param filePath 中文注释: 保存的文件路径。 English annotation: The path of the saved file.
		 */
		void onFileSaved(String filePath);
	}

	/**
	 * 中文注释: Activity 生命周期方法，在活动首次创建时调用。
	 * English annotation: Activity lifecycle method, called when the activity is first created.
	 * @param savedInstanceState 中文注释: 包含上次保存的活动状态的 Bundle。 English annotation: Bundle containing the activity's previously saved state.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.text_editor);

		// Reset state if activity is recreated
		// 中文注释: 如果活动被重新创建，则重置状态
		// English annotation: Reset state if activity is recreated
		if (savedInstanceState == null) {
			smaliMethodsStringsFragment = null;
			methodRecyclerViewState = null;
			stringsRecyclerViewState = null;
			wasStringsVisible = false;
			lastSmaliFilePath = "";
			lastModifiedTime = -1;
		}

		initialize(savedInstanceState);
		initializeLogic();
	}

	/**
	 * 中文注释: 显示 Smali 标签的自动补全建议。
	 * English annotation: Displays auto-completion suggestions for Smali labels.
	 * @param query 中文注释: 用户输入的查询字符串。 English annotation: The query string entered by the user.
	 */
	private void showLabelsCompletion(String query) {
		// 1. Fetch labels
		// 中文注释: 1. 获取标签
		// English annotation: 1. Fetch labels
		int editorLineNumber = smaliEditor.getCursor().getLeftLine();
		List<String> labelList = SmaliCursorUtils.extractAllLabelLines(
		smaliEditor.getText().toString(),
		editorLineNumber
		);

		// 2. Show dialog
		// 中文注释: 2. 显示对话框
		// English annotation: 2. Show dialog
		final ListDialog dialog = new ListDialog(
		smaliEditor.getContext(),
		labelList,
		query,
		editorLineNumber
		);

		/**
		 * 中文注释: 设置标签点击监听器。
		 * English annotation: Sets the label click listener.
		 */
		dialog.setOnLabelClickListener(new ListDialog.OnLabelClickListener() {
			/**
			 * 中文注释: 当标签被点击时调用。
			 * English annotation: Called when a label is clicked.
			 * @param selectedLabel 中文注释: 被选中的标签文本。 English annotation: The text of the selected label.
			 */
			@Override
			public void onLabelClick(String selectedLabel) {
				String selectedItem = selectedLabel;

				int lineNumber = Integer.parseInt(selectedItem.substring(1, selectedItem.indexOf(']'))) - 1;
				String lineContent = smaliEditor.getText().getLineString(lineNumber);
				int columnPos = lineContent.indexOf(query);

				if (columnPos >= 0) {
					smaliEditor.setSelection(lineNumber, columnPos);
					smaliEditor.ensurePositionVisible(lineNumber, columnPos);
				}
				dialog.dismiss();
			}
		});

		dialog.show();
	}

	/**
	 * 中文注释: 初始化视图组件和设置。
	 * English annotation: Initializes view components and settings.
	 * @param savedInstanceState 中文注释: 包含上次保存的活动状态的 Bundle。 English annotation: Bundle containing the activity's previously saved state.
	 */
	private void initialize(Bundle savedInstanceState) {
		DexEditorActivity.classTree = classTree;
		appBarLayout = findViewById(R.id._app_bar);
		coordinatorLayout = findViewById(R.id._coordinator);
		toolbar = findViewById(R.id._toolbar);

		linearHeader = findViewById(R.id.linear_header);
		linearLeft = findViewById(R.id.linear_left);
		textviewLeft = findViewById(R.id.textview_left);
		linearRight = findViewById(R.id.linear_right);
		textviewLineNo = findViewById(R.id.textview_lineNo);
		methodName = findViewById(R.id.methodName);
		symbol_input = findViewById(R.id.symbol_input);


		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		/**
		 * 中文注释: 设置工具栏导航按钮的点击监听器，点击后返回上一级。
		 * English annotation: Sets the click listener for the toolbar navigation button, which navigates back on click.
		 */
		toolbar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				onBackPressed();
			}
		});
		smaliEditor = findViewById(R.id.smali_editor);
		sharedPreferences = getSharedPreferences("SelectedTranslationPackageName", 0);
		preferencesEditor = sharedPreferences.edit();
		packageManager = getPackageManager();

		dexPref = getSharedPreferences("dexPref", Activity.MODE_PRIVATE);
		editorPrefs = getSharedPreferences("editor_prefs", Context.MODE_PRIVATE);

		/**
		 * 中文注释: 设置左侧线性布局的点击监听器，点击后显示一个弹出菜单。
		 * English annotation: Sets the click listener for the left linear layout, which displays a popup menu on click.
		 */
		linearLeft.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				PopupMenu popupMenu = new PopupMenu(TextEditorActivity.this, view);
				Menu menu = popupMenu.getMenu();
				menu.add(1, 1, 1, currentTitle);
				menu.add(2, 2, 2, Smali2JavaName2(intentClassName));
				menu.add(3, 3, 3, intentClassName);
				menu.add(4, 4, 4, "L" + intentClassName + ";");

				/**
				 * 中文注释: 设置弹出菜单项的点击监听器，点击后将选中的文本复制到剪贴板。
				 * English annotation: Sets the click listener for popup menu items, which copies the selected text to the clipboard on click.
				 */
				popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
					@Override
					public boolean onMenuItemClick(MenuItem menuItem) {
						copiedToClipboard(menuItem.getTitle().toString());
						return true;
					}
				});

				popupMenu.show();
			}
		});

		/**
		 * 中文注释: 设置右侧线性布局的点击监听器，点击后运行方法列表的 Runnable。
		 * English annotation: Sets the click listener for the right linear layout, which runs the method list Runnable on click.
		 */
		linearRight.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				runOnUiThread(new MethodListRunnable());
			}
		});
	}

	/**
	 * 中文注释: 初始化逻辑，设置编辑器行为和事件监听器。
	 * English annotation: Initializes logic, setting editor behavior and event listeners.
	 */
	private void initializeLogic() {
		setTitle("");
		tempSmaliPath = getFilesDir() + "/tmp.smali";
		savedFont  = SettingsFragment.getFontType(this);
		cursor = smaliEditor.getCursor();

		smaliInstructionHelper.init(getApplicationContext());
		loadEditorSettings(true);
		symbol_input.bindEditor(smaliEditor);
		symbol_input.addSymbols(SYMBOLS, SYMBOL_INSERT_TEXT);
		loadTMThemes(this);
		smaliEditor.setEditorLanguage(new CustomAutoComplete(smaliEditor, smaliInstructionHelper.getAllSmaliInstructions()));
		try{
			TextMateColorScheme scheme = TextMateColorScheme.create(ThemeRegistry.getInstance());
			smaliEditor.setColorScheme(scheme);
		}catch (Exception e){}
		handleSmaliIntent();

		/**
		 * 中文注释: 订阅内容改变事件，用于处理撤消/重做状态和保存逻辑。
		 * English annotation: Subscribes to content change events, used to handle undo/redo state and save logic.
		 */
		smaliEditor.subscribeEvent(ContentChangeEvent.class, new EventReceiver<ContentChangeEvent>() {
			@Override
			public void onReceive(ContentChangeEvent event, Unsubscribe unsubscribe) {
				handleUndoRedo();
				if(!isReload){
					Cursor cursor = smaliEditor.getCursor();
					int position = cursor.getLeftLine();
					editorPrefs.edit().putInt("lineNo", position).commit();
				}
				if (!saveEvent.isEmpty()) {
					isEditMode = true;
					isFileCreated = "";
				}
				saveEvent = "Saved";
				isReload = false;
			}
		});

		/**
		 * 中文注释: 订阅选择改变事件，用于更新行号、列号和当前方法/字段名称。
		 * English annotation: Subscribes to selection change events, used to update line number, column number, and current method/field name.
		 */
		smaliEditor.subscribeEvent(SelectionChangeEvent.class, new EventReceiver<SelectionChangeEvent>() {
			@Override
			public void onReceive(SelectionChangeEvent event, Unsubscribe unsubscribe) {
				Cursor cursor = smaliEditor.getCursor();
				CharSequence text = smaliEditor.getText();

				int line = cursor.getLeftLine() + 1;
				int column = cursor.getLeftColumn() + 1;

				StringBuilder positionText = new StringBuilder();
				positionText.append(String.format("%d:%d", line, column));

				if (cursor.isSelected()) {
					String selectedText = text.subSequence(
					cursor.getLeft(),
					cursor.getRight()).toString();
					positionText.append(" (").append(selectedText.length()).append(")");
				}

				textviewLineNo.setText(positionText.toString());

				String currentElement = SmaliCursorUtils.getCurrentMethodOrFieldName(text, cursor.getLeftLine());
				methodName.setText(currentElement != null ? currentElement : "...");
			}
		});
	}


	/**
	 * 中文注释: 显示 Smali 导航（方法和字符串列表）。
	 * English annotation: Displays Smali navigation (method and string lists).
	 * @param tempSmaliPath 中文注释: 临时 Smali 文件的路径。 English annotation: The path of the temporary Smali file.
	 * @param currentTitle 中文注释: 当前文件的标题。 English annotation: The title of the current file.
	 * @param lineNo 中文注释: 当前行号。 English annotation: The current line number.
	 */
	private void showSmaliNavigation(String tempSmaliPath, String currentTitle, int lineNo) {
		File smaliFile = new File(tempSmaliPath);
		boolean fileChanged = !tempSmaliPath.equals(lastSmaliFilePath) ||
		(smaliFile.exists() && smaliFile.lastModified() != lastModifiedTime);

		if (smaliMethodsStringsFragment == null || fileChanged) {
			// Create a new instance if none exists or file has changed
			// 中文注释: 如果不存在或文件已更改，则创建新实例
			// English annotation: Create a new instance if none exists or file has changed
			smaliMethodsStringsFragment = new SmaliMethodListFragment();
			smaliMethodsStringsFragment.show(getSupportFragmentManager(), " ");
			smaliMethodsStringsFragment.updateUi(tempSmaliPath, currentTitle.replace(".smali", ""), lineNo, dexVersion);

			// Update tracking variables
			// 中文注释: 更新跟踪变量
			// English annotation: Update tracking variables
			lastSmaliFilePath = tempSmaliPath;
			lastModifiedTime = smaliFile.exists() ? smaliFile.lastModified() : -1;

			// Reset visibility and scroll states since it's a fresh load
			// 中文注释: 由于是新加载，重置可见性和滚动状态
			// English annotation: Reset visibility and scroll states since it's a fresh load
			wasStringsVisible = false;
			methodRecyclerViewState = null;
			stringsRecyclerViewState = null;
		} else {
			// Reuse the existing fragment instance
			// 中文注释: 重用现有片段实例
			// English annotation: Reuse the existing fragment instance
			smaliMethodsStringsFragment.show(getSupportFragmentManager(), " ");
			smaliMethodsStringsFragment.restorePreviousState(methodRecyclerViewState, stringsRecyclerViewState, wasStringsVisible);
		}
	}


	/**
	 * 中文注释: Activity 生命周期方法，在活动销毁时调用。
	 * English annotation: Activity lifecycle method, called when the activity is destroyed.
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
		// Clear the fragment instance on activity destruction
		// 中文注释: 在活动销毁时清除片段实例
		// English annotation: Clear the fragment instance on activity destruction
		smaliMethodsStringsFragment = null;
	}

	/**
	 * 中文注释: Activity 生命周期方法，在活动恢复时调用。
	 * English annotation: Activity lifecycle method, called when the activity resumes.
	 */
	@Override
	public void onResume() {
		super.onResume();
		loadEditorSettings(false);
	}

	/**
	 * 中文注释: 初始化活动选项菜单。
	 * English annotation: Initializes the activity's options menu.
	 * @param menu 中文注释: 要填充的菜单。 English annotation: The menu to be populated.
	 * @return boolean 中文注释: 如果成功初始化菜单，则返回 true。 English annotation: True if the menu was successfully initialized.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		this.menu = menu;
		getMenuInflater().inflate(R.menu.editor_menu, menu);
		undoMenuItem = menu.findItem(R.id.undo);
		redoMenuItem = menu.findItem(R.id.redo);
		menu.findItem(R.id.wrap_text).setCheckable(true).setChecked(editorPrefs.getBoolean("wrap_text", false));
		menu.findItem(R.id.edit_menu);
		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				View saveView = findViewById(R.id.save);
				if (saveView != null) {
					saveView.setOnLongClickListener(new View.OnLongClickListener() {
						@Override
						public boolean onLongClick(View view) {
							if (saveCompileError.isEmpty()) {
								return true;
							}
							showPreviousErrorDlg(saveCompileError);
							return true;
						}
					});
				} else {
				}
			}
		}, 100); // 100ms delay

		return super.onCreateOptionsMenu(menu);
	}

	/**
	 * 中文注释: 处理用户选择菜单项的事件。
	 * English annotation: Handles events when a user selects a menu item.
	 * @param item 中文注释: 被选中的菜单项。 English annotation: The selected menu item.
	 * @return boolean 中文注释: 如果事件被处理，则返回 true。 English annotation: True if the event was handled.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Cursor cursor = smaliEditor.getCursor();
		switch (item.getItemId()) {
			case R.id.redo:
			smaliEditor.redo();
			handleUndoRedo();
			dismissEditorWindow();
			break;
			case R.id.undo:
			smaliEditor.undo();
			handleUndoRedo();
			dismissEditorWindow();
			break;
			case R.id.save:
			if (isEditMode) {
				saveFile(false);
			}
			break;
			case R.id.navigation:
			runOnUiThread(new MethodListRunnable());
			break;
			case R.id.smali2java:
			runOnUiThread(new SmaliToJavaRunnable());
			break;
			case R.id.preference:
			startActivity(new Intent(TextEditorActivity.this, SettingsActivity.class));
			break;
			case R.id.jumpToLine:
			jumpToLine();
			break;
			case R.id.close:
			onBackPressed();
			break;
			case R.id.wrap_text:
			if (item.isChecked()) {
				item.setChecked(false);
				editorPrefs.edit().putBoolean("wrap_text", false).commit();
				smaliEditor.setWordwrap(false);
			}
			else {
				item.setChecked(true);
				editorPrefs.edit().putBoolean("wrap_text", true).commit();
				smaliEditor.setWordwrap(true);
			}
			return true;
			case R.id.read_only:
			if (item.isChecked()) {
				item.setChecked(false);
				smaliEditor.setEditable(true);
				menu.findItem(R.id.edit_menu).setEnabled(true);
			}
			else {
				item.setChecked(true);
				smaliEditor.setEditable(false);
				menu.findItem(R.id.edit_menu).setEnabled(false);
			}
			return true;

			case R.id.delete_line:
			deleteLine();
			dismissEditorWindow();
			return true;

			case R.id.empty_line:
			emptyLine();
			dismissEditorWindow();
			return true;

			case R.id.replace_line:
			replaceLine();
			dismissEditorWindow();
			return true;

			case R.id.duplicate_line:
			duplicateLine();
			dismissEditorWindow();
			return true;

			case R.id.toggle_comment:
			toggleComment();
			return true;

			case R.id.copy_line:
			_copyLine();
			dismissEditorWindow();
			return true;

			case R.id.cut_line:
			smaliEditor.cutLine();
			dismissEditorWindow();
			return true;

			case R.id.convert_uppercase:
			convertUpperLowerCase(true);
			dismissEditorWindow();
			return true;

			case R.id.convert_lowercase:
			convertUpperLowerCase(false);
			dismissEditorWindow();
			return true;

			case R.id.increase_indent:
			increaseIndent();
			dismissEditorWindow();
			return true;

			case R.id.decrease_indent:
			decreaseIndent();
			dismissEditorWindow();
			return true;

			case R.id.smali_instruction:
			String instruction = getCurrentLineInstruction();
			if (instruction != null) {
				SmaliInstructionsDialog dialog = new SmaliInstructionsDialog(this, "smali_instructions.txt", instruction);
				dialog.show();
			} else {
				SmaliInstructionsDialog dialog = new SmaliInstructionsDialog(this, "smali_instructions.txt");
				dialog.show();
			}

			return true;

			default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	}



	/**
	 * 中文注释: 加载编辑器设置，如字体大小、行号显示和文本换行。
	 * English annotation: Loads editor settings such as font size, line number display, and word wrap.
	 * @param loadTypeface 中文注释: 是否加载字体。 English annotation: Whether to load the typeface.
	 */
	public void loadEditorSettings(boolean loadTypeface){
		smaliEditor.setTextSize(SettingsFragment.getFontSize(this));
		smaliEditor.setLineNumberEnabled(SettingsFragment.showLineNumbers(this));
		smaliEditor.setLineSpacing(2.0f, 1.1f);
		smaliEditor.setLineNumberMarginLeft(2f);
		smaliEditor.setWordwrap(editorPrefs.getBoolean("wrap_text", false));
		if(loadTypeface){
			Typeface typeface = savedFont.equals("normal")
			? Typeface.DEFAULT : Typeface.MONOSPACE;
			smaliEditor.setTypefaceText(typeface);
			smaliEditor.setTypefaceLineNumber(typeface);
		} else {
			if(!savedFont.equals(SettingsFragment.getFontType(this))){
				Typeface typeface = savedFont.equals("normal")
				? Typeface.MONOSPACE : Typeface.DEFAULT;
				smaliEditor.setTypefaceText(typeface);
				smaliEditor.setTypefaceLineNumber(typeface);

				savedFont  = SettingsFragment.getFontType(this);
				isReload = true;
				reloadText();
			}
		}

		smaliEditor.replaceComponent(EditorTextActionWindow.class, new TextActionWindow(smaliEditor, new TextActionCallback(Smali2JavaName2(intentClassName))));
	}


	/**
	 * 中文注释: 处理文本动作窗口的回调，例如“跳转到”和“翻译”。
	 * English annotation: Handles callbacks from the text action window, such as "Go To" and "Translate".
	 */
	private class TextActionCallback implements TextActionWindow.ItemClickCallBack {
		/**
		 * 中文注释: 当前类名。
		 * English annotation: The current class name.
		 */
		private final String currentClassName;

		/**
		 * 中文注释: 构造函数。
		 * English annotation: Constructor.
		 * @param className 中文注释: 类名。 English annotation: The class name.
		 */
		TextActionCallback(String className) {
			this.currentClassName = className;
		}

		/**
		 * 中文注释: 点击“跳转到”时的回调。
		 * English annotation: Callback when "Go To" is clicked.
		 * @param view 中文注释: 被点击的视图。 English annotation: The clicked view.
		 * @param text 中文注释: 要跳转到的文本。 English annotation: The text to navigate to.
		 */
		@Override
		public void onClickGoTo(View view, final String text) {
			if(text.startsWith(":")){
				showLabelsCompletion(text.replace("}", ""));
			} else {
				runOnUiThread(new GoToRunnable(text, currentClassName));
			}
		}

		/**
		 * 中文注释: 点击“翻译”时的回调。
		 * English annotation: Callback when "Translate" is clicked.
		 * @param view 中文注释: 被点击的视图。 English annotation: The clicked view.
		 * @param text 中文注释: 要翻译的文本。 English annotation: The text to translate.
		 */
		@Override
		public void onClickTranslate(View view, final String text) {
			runOnUiThread(new Runnable() {
				/**
				 * 中文注释: 要翻译的文本。
				 * English annotation: The text to be translated.
				 */
				private final String mText = text;

				@Override
				public void run() {
					if (!sharedPreferences.contains("selectedPackage")) {
						SketchwareUtil.showMessage(getApplicationContext(), "Select a translation app first");
						showAvailableTranslationDlg();
						return;
					}
					try {
						String packageName = sharedPreferences.getString("selectedPackage", "");
						packageManager.getPackageInfo(packageName, 0);
						Intent intent = new Intent("android.intent.action.PROCESS_TEXT");
						intent.setType("text/plain");
						intent.putExtra("android.intent.extra.PROCESS_TEXT", mText);
						intent.putExtra("android.intent.extra.PROCESS_TEXT_READONLY", true);
						intent.setPackage(packageName);
						startActivity(intent);
					} catch (PackageManager.NameNotFoundException e) {
						preferencesEditor.remove("selectedPackage");
						preferencesEditor.apply();
						showAvailableTranslationDlg();
					}
				}
			});
		}

		/**
		 * 中文注释: 长按“翻译”时的回调。
		 * English annotation: Callback when "Translate" is long-clicked.
		 * @param view 中文注释: 被长按的视图。 English annotation: The long-clicked view.
		 */
		@Override
		public void onLongClickTranslate(View view) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					showAvailableTranslationDlg();
				}
			});

		}
	}


	/**
	 * 中文注释: 处理“跳转到”操作的 Runnable。
	 * English annotation: Runnable for handling "Go To" operations.
	 */
	private class GoToRunnable implements Runnable {
		/**
		 * 中文注释: 要跳转到的文本。
		 * English annotation: The text to go to.
		 */
		private final String text;
		/**
		 * 中文注释: 当前类名。
		 * English annotation: The current class name.
		 */
		private final String currentClassName;

		/**
		 * 中文注释: 构造函数。
		 * English annotation: Constructor.
		 * @param text 中文注释: 要跳转到的文本。 English annotation: The text to go to.
		 * @param currentClassName 中文注释: 当前类名。 English annotation: The current class name.
		 */
		GoToRunnable(String text, String currentClassName) {
			this.text = text;
			this.currentClassName = currentClassName;
		}

		@Override
		public void run() {
			if (!text.contains(";->")) {
				if (Smali2JavaName(text).equals(currentClassName)) {
					SketchwareUtil.showMessage(getApplicationContext(), "You are already in this class");
					return;
				} else if (classTree.classMap.get(smali2OnlySlash(text)) == null) {
					ErrorDlg("Class not found: " + Smali2JavaName(text));
					return;
				} else {
					Intent intent = new Intent(TextEditorActivity.this, TextEditorActivity.class);
					intent.putExtra("ClassName", smali2OnlySlash(text));
					startActivity(intent);
					Animatoo.animateSlideLeft(TextEditorActivity.this);
					return;
				}
			}
			String[] splitText = splitText(text);
			if (splitText.length == 2) {
				String className = splitText[0];
				final String methodName = splitText[1];
				String javaClassName = Smali2JavaName(className);
				if (javaClassName.equals(currentClassName)) {
					try {
						if (isFileCreated.isEmpty()) {
							saveSmaliCodeToFile(smaliEditor.getText().toString(), tempSmaliPath, new FileSaveCallback() {
								@Override
								public void onFileSaved(String filePath) {
									try {
										isFileCreated = "true";
										extractInfo(filePath, methodName);
									} catch (Exception e) {
										ErrorDlg(e.toString());
									}
								}
							});
						} else if (!FileUtil.isExistFile(tempSmaliPath)) {
							saveSmaliCodeToFile(smaliEditor.getText().toString(), tempSmaliPath, new FileSaveCallback() {
								@Override
								public void onFileSaved(String filePath) {
									try {
										isFileCreated = "true";
										extractInfo(filePath, methodName);
									} catch (Exception e) {
										ErrorDlg(e.toString());
									}
								}
							});
						} else {
							extractInfo(tempSmaliPath, methodName);
						}
					} catch (Exception e) {
						ErrorDlg(e.toString());
					}
				} else if (classTree.classMap.get(smali2OnlySlash(className)) == null) {
					ErrorDlg("Class not found : " + javaClassName);
				} else {
					Intent intent = new Intent(TextEditorActivity.this, TextEditorActivity.class);
					intent.putExtra("ClassName", smali2OnlySlash(className));
					intent.putExtra("MethodName", methodName);
					startActivity(intent);
					Animatoo.animateSlideLeft(TextEditorActivity.this);
				}
			}
		}
	}



	/**
	 * 中文注释: 处理返回按钮按下事件。如果文件处于编辑模式，则提示用户保存。
	 * English annotation: Handles back button press event. If the file is in edit mode, prompts the user to save.
	 */
	@Override
	public void onBackPressed() {
		FileUtil.deleteFile(tempSmaliPath);
		if (isEditMode) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Info");
			builder.setMessage("Do you want to save the file ?");
			builder.setPositiveButton("ok", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					saveFile(true);
				}
			});
			builder.setNegativeButton("cancel ", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					finish();
					Animatoo.animateSlideRight(TextEditorActivity.this);
				}
			});
			Notify_MT.Dlg_Style(builder);
			return;
		}
		finish();
		Animatoo.animateSlideRight(this);
	}


	/**
	 * 中文注释: 处理 Smali Intent，根据 Intent 中的类名加载 Smali 代码。
	 * English annotation: Handles Smali Intent, loading Smali code based on the class name in the Intent.
	 */
	private void handleSmaliIntent() {
		showProgressDialog(true);
		if (getIntent().hasExtra("ClassName")) {
			intentClassName = getIntent().getStringExtra("ClassName");
			currentTitle = extractSubstringAfterLastSlash(intentClassName);

			try {
				dexVersion = classTree.getOpenedDexVersion();
				SharedPreferences.Editor editor = dexPref.edit();
				editor.putInt("dexVer", dexVersion);
				editor.apply();
			} catch (Exception e) {
				dexVersion = dexPref.getInt("dexVer", 35);
			}

			final Handler handler = new Handler(Looper.getMainLooper()) {
				@Override
				public void handleMessage(Message msg) {
					showProgressDialog(false);
					try {
						if (getIntent().hasExtra("MethodName")) {
							saveSmaliCodeToFile(smaliEditor.getText().toString(), tempSmaliPath, new FileSaveCallback() {
								@Override
								public void onFileSaved(String filePath) {
									try {
										isFileCreated = "true";
										extractInfo(filePath, getIntent().getStringExtra("MethodName"));
									} catch (Exception e) {
										ErrorDlg(customException(e));
									}
								}
							});
						}
					} catch (Exception e) {
						ErrorDlg(customException(e));
					}
				}
			};

			new Thread() {
				@Override
				public void run() {
					try {
						if (classTree == null) {
							finish();
						}
						String smaliCode = new String(classTree.getSmaliByType(classTree.classMap.get(intentClassName)).getBytes(), "UTF-8");

						// Update UI on the main thread
						// 中文注释: 在主线程上更新UI
						// English annotation: Update UI on the main thread
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								textviewLeft.setText(currentTitle);
								smaliEditor.setText(smaliCode);
								showProgressDialog(false);
							}
						});

					} catch (Exception e) {
						// Handle exceptions on the main thread
						// 中文注释: 在主线程上处理异常
						// English annotation: Handle exceptions on the main thread
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								showProgressDialog(false);
								ErrorDlg(customException(e));
							}
						});
					}
					handler.sendEmptyMessage(0); // Notify completion
				}
			}.start();
		}
	}

	/**
	 * 中文注释: 重新加载编辑器中的文本内容。
	 * English annotation: Reloads the text content in the editor.
	 */
	private void reloadText(){
		showProgressDialog(true);
		final Handler handler = new Handler(Looper.getMainLooper()) {
			@Override
			public void handleMessage(Message msg) {
				showProgressDialog(false);
				try {
					reloadEditorPosition(editorPrefs.getInt("lineNo", 0));
				} catch (Exception e) {
					ErrorDlg(customException(e));
				}
			}
		};

		new Thread() {
			@Override
			public void run() {
				try {
					String smaliCode = smaliEditor.getText().toString();
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							smaliEditor.setText(smaliCode);
						}
					});

				} catch (Exception e) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							showProgressDialog(false);
							ErrorDlg(customException(e));
						}
					});
				}
				handler.sendEmptyMessage(0);
			}
		}.start();
	}

	/**
	 * 中文注释: 处理撤消和重做菜单项的启用状态。
	 * English annotation: Handles the enabled state of undo and redo menu items.
	 */
	private void handleUndoRedo() {
		if (menu != null) {
			if (smaliEditor.canUndo()) {
				undoMenuItem.setIcon(R.drawable.ic_undo_mt);
				undoMenuItem.setEnabled(true);
			} else {
				undoMenuItem.setIcon(R.drawable.ic_undo_grey);
				isEditMode = false;
				undoMenuItem.setEnabled(false);
			}
			if (smaliEditor.canRedo()) {
				redoMenuItem.setIcon(R.drawable.ic_redo_mt);
				redoMenuItem.setEnabled(true);
			} else {
				redoMenuItem.setIcon(R.drawable.ic_redo_grey);
				redoMenuItem.setEnabled(false);
			}
		}
	}

	/**
	 * 中文注释: 从 Smali 方法列表片段接收更新的行号并跳转到编辑器中的相应位置。
	 * English annotation: Receives updated line numbers from the Smali method list fragment and jumps to the corresponding position in the editor.
	 * @param lineNumber 中文注释: 要跳转到的行号字符串。 English annotation: The line number string to jump to.
	 */
	@Override
	public void _updateEditorLineNumber(String lineNumber) {
		try {
			int LineNum = (int) Math.floor(Double.parseDouble(lineNumber));
			smaliEditor.jumpToLine(LineNum);
			String getLineText = smaliEditor.getText().getLine(LineNum).toString();
			if(getLineText.contains("const-string")){
				int[] positions = getOuterQuotePositions(getLineText);
				smaliEditor.setSelectionRegion(LineNum, (positions[0] + 1), LineNum, positions[1]);
				dismissEditorWindow();
			}
		} catch (Exception e) {
			SketchwareUtil.showMessage(getApplicationContext(), "Value is out of range." + lineNumber);
		}
	}

	/**
	 * 中文注释: 显示可用的翻译应用对话框，允许用户选择默认翻译应用。
	 * English annotation: Displays a dialog of available translation applications, allowing the user to select a default.
	 */
	private void showAvailableTranslationDlg() {
		Intent intent = new Intent("android.intent.action.PROCESS_TEXT");
		intent.addCategory("android.intent.category.DEFAULT");
		intent.setType("text/plain");
		final List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(intent, 0);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Available system translations");
		String[] appNames = new String[resolveInfoList.size()];
		final String[] selectedPackage = {""};
		int selectedIndex = -1;
		for (int i = 0; i < resolveInfoList.size(); i++) {
			ResolveInfo resolveInfo = resolveInfoList.get(i);
			String appName = resolveInfo.activityInfo.applicationInfo.loadLabel(packageManager).toString();
			String packageName = resolveInfo.activityInfo.packageName;
			String activityName = resolveInfo.loadLabel(packageManager).toString();
			appNames[i] = appName + " - " + activityName;
			if (packageName.equals(sharedPreferences.getString("selectedPackage", ""))) {
				selectedPackage[0] = packageName;
				selectedIndex = i;
			}
		}
		builder.setSingleChoiceItems(appNames, selectedIndex, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				selectedPackage[0] = resolveInfoList.get(which).activityInfo.packageName;
			}
		});
		builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if (selectedPackage[0].isEmpty()) {
					Toast.makeText(getApplication(), "No app selected", Toast.LENGTH_SHORT).show();
					return;
				}
				preferencesEditor.putString("selectedPackage", selectedPackage[0]);
				preferencesEditor.apply();
				Toast.makeText(getApplication(), "You can further change your translation app by pressing long in the translation menu item", Toast.LENGTH_SHORT).show();
			}
		});
		AlertDialog dialog = builder.create();
		dialog.getWindow().setBackgroundDrawable(Notify_MT.createDrawable(20, Color.WHITE));
		dialog.show();
		dialog.getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				String packageName = resolveInfoList.get(position).activityInfo.packageName;
				if (packageName.isEmpty()) {
					return true;
				}
				Intent intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
				intent.setData(Uri.parse("package:" + packageName));
				startActivity(intent);
				return true;
			}
		});
		if (selectedPackage[0].isEmpty()) {
			return;
		}
		try {
			packageManager.getPackageInfo(selectedPackage[0], 0);
		} catch (PackageManager.NameNotFoundException e) {
			preferencesEditor.remove("selectedPackage");
			preferencesEditor.apply();
		}
	}

	/**
	 * 中文注释: 在 Smali 文件中查找指定方法的行号和列号。
	 * English annotation: Finds the line and column numbers of a specified method in a Smali file.
	 * @param filePath 中文注释: Smali 文件的路径。 English annotation: The path to the Smali file.
	 * @param methodName 中文注释: 要查找的方法名。 English annotation: The name of the method to find.
	 * @return TextLocation 中文注释: 包含行号和列号的 TextLocation 对象，如果未找到则返回 null。 English annotation: A TextLocation object containing the line and column numbers, or null if not found.
	 * @throws Exception 中文注释: 读取文件时可能抛出的异常。 English annotation: Exception that may be thrown during file reading.
	 */
	private TextLocation findMethodLocation(String filePath, String methodName) throws Exception {
		BufferedReader reader = new BufferedReader(new FileReader(filePath));
		int lineNumber = 0;
		while (true) {
			String line = reader.readLine();
			if (line != null) {
				lineNumber++;
				String trimmedLine = line.trim();
				if (!trimmedLine.isEmpty()) {
					String[] parts = trimmedLine.split(" ");
					if (".method".equals(parts[0]) && parts[parts.length - 1].equals(methodName)) {
						int startIndex = trimmedLine.indexOf(methodName);
						TextLocation location = new TextLocation(lineNumber, startIndex, _getTextBefore(methodName, "(").length() + startIndex);
						reader.close();
						return location;
					}
				}
			} else {
				reader.close();
				return null;
			}
		}
	}

	/**
	 * 中文注释: 在 Smali 文件中查找指定字段的行号和列号。
	 * English annotation: Finds the line and column numbers of a specified field in a Smali file.
	 * @param filePath 中文注释: Smali 文件的路径。 English annotation: The path to the Smali file.
	 * @param fieldName 中文注释: 要查找的字段名。 English annotation: The name of the field to find.
	 * @return TextLocation 中文注释: 包含行号和列号的 TextLocation 对象，如果未找到则返回 null。 English annotation: A TextLocation object containing the line and column numbers, or null if not found.
	 * @throws Exception 中文注释: 读取文件时可能抛出的异常。 English annotation: Exception that may be thrown during file reading.
	 */
	private TextLocation findFieldLocation(String filePath, String fieldName) throws Exception {
		BufferedReader reader = new BufferedReader(new FileReader(filePath));
		int lineNumber = 0;
		while (true) {
			String line = reader.readLine();
			if (line != null) {
				lineNumber++;
				if (line.trim().startsWith(".field") && line.contains(fieldName)) {
					int startIndex = line.indexOf(fieldName);
					TextLocation location = new TextLocation(lineNumber, startIndex, _getTextBefore(fieldName, ":").length() + startIndex);
					reader.close();
					return location;
				}
			} else {
				reader.close();
				return null;
			}
		}
	}

	/**
	 * 中文注释: 将 Smali 代码内容保存到指定文件。
	 * English annotation: Saves Smali code content to the specified file.
	 * @param content 中文注释: 要保存的 Smali 代码字符串。 English annotation: The Smali code string to save.
	 * @param filePath 中文注释: 目标文件路径。 English annotation: The target file path.
	 * @param callback 中文注释: 文件保存完成后的回调。 English annotation: Callback after file saving is complete.
	 * @throws Exception 中文注释: 写入文件时可能抛出的异常。 English annotation: Exception that may be thrown during file writing.
	 */
	private void saveSmaliCodeToFile(String content, String filePath, FileSaveCallback callback) throws Exception {
		BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
		writer.write(content);
		if (callback != null) {
			callback.onFileSaved(filePath);
		}
		writer.close();
	}

	/**
	 * 中文注释: 从指定文件中提取方法或字段信息，并跳转到编辑器中的相应位置。
	 * English annotation: Extracts method or field information from the specified file and jumps to the corresponding position in the editor.
	 * @param filePath 中文注释: Smali 文件的路径。 English annotation: The path to the Smali file.
	 * @param target 中文注释: 要查找的方法名或字段名。 English annotation: The name of the method or field to find.
	 */
	private void extractInfo(final String filePath, final String target) {
		new AsyncTask<Void, Void, TextLocation>() {
			@Override
			protected TextLocation doInBackground(Void... voids) {
				try {
					if (target.contains(":")) {
						return findFieldLocation(filePath, target);
					} else {
						return findMethodLocation(filePath, target);
					}
				} catch (Exception e) {
					ErrorDlg(e.toString());
					return null;
				}
			}

			@Override
			protected void onPostExecute(TextLocation location) {
				if (location != null) {
					int lineNumber = location.lineNumber - 1;
					try {
						smaliEditor.jumpToLine(lineNumber);
						new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
							@Override
							public void run() {
								try {
									smaliEditor.setSelectionRegion(lineNumber, location.startColumn, lineNumber, location.endColumn);
									dismissEditorWindow();
								} catch (Exception e) {
									ErrorDlg(e.toString());
								}
							}
						}, 100);
					} catch (Exception e) {
						ErrorDlg(e.toString());
					}
				} else {
					smaliEditor.jumpToLine(0);
					System.out.println("Method or field not found.");
				}
			}
		}.execute();
	}

	/**
	 * 中文注释: 显示错误对话框。
	 * English annotation: Displays an error dialog.
	 * @param errorMessage 中文注释: 要显示的错误信息。 English annotation: The error message to display.
	 */
	public void ErrorDlg(String errorMessage) {
		Notify_MT.Notify(this, getResources().getString(R.string.error_title), errorMessage, getResources().getString(R.string.close_btn));
	}

	/**
	 * 中文注释: 表示文本在 Smali 文件中的位置（行号、开始列和结束列）。
	 * English annotation: Represents the position of text in a Smali file (line number, start column, and end column).
	 */
	private class TextLocation {
		/**
		 * 中文注释: 文本所在的行号。
		 * English annotation: The line number where the text is located.
		 */
		int lineNumber;
		/**
		 * 中文注释: 文本的起始列号。
		 * English annotation: The starting column number of the text.
		 */
		int startColumn;
		/**
		 * 中文注释: 文本的结束列号。
		 * English annotation: The ending column number of the text.
		 */
		int endColumn;

		/**
		 * 中文注释: 构造函数。
		 * English annotation: Constructor.
		 * @param lineNumber 中文注释: 行号。 English annotation: Line number.
		 * @param startColumn 中文注释: 起始列号。 English annotation: Start column number.
		 * @param endColumn 中文注释: 结束列号。 English annotation: End column number.
		 */
		public TextLocation(int lineNumber, int startColumn, int endColumn) {
			this.lineNumber = lineNumber;
			this.startColumn = startColumn;
			this.endColumn = endColumn;
		}
	}

	/**
	 * 中文注释: 处理方法列表导航的 Runnable。
	 * English annotation: Runnable for handling method list navigation.
	 */
	private class MethodListRunnable implements Runnable {
		/**
		 * 中文注释: 编辑器光标对象。
		 * English annotation: Editor cursor object.
		 */
		Cursor cursor = smaliEditor.getCursor();
		@Override
		public void run() {
			try {
				if (isFileCreated.isEmpty()) {
					saveSmaliCodeToFile(smaliEditor.getText().toString(), tempSmaliPath, new FileSaveCallback() {
						@Override
						public void onFileSaved(String filePath) {
							try {
								isFileCreated = "true";
								showSmaliNavigation(tempSmaliPath, currentTitle.replace(".smali", ""), cursor.getLeftLine());

							} catch (Exception e) {
								ErrorDlg(e.toString());
							}
						}
					});
				} else if (!FileUtil.isExistFile(tempSmaliPath)) {
					saveSmaliCodeToFile(smaliEditor.getText().toString(), tempSmaliPath, new FileSaveCallback() {
						@Override
						public void onFileSaved(String filePath) {
							try {
								isFileCreated = "true";
								showSmaliNavigation(tempSmaliPath, currentTitle.replace(".smali", ""), cursor.getLeftLine());

							} catch (Exception e) {
								ErrorDlg(e.toString());
							}
						}
					});
				} else {
					SmaliMethodListFragment fragment = new SmaliMethodListFragment();
					fragment.show(getSupportFragmentManager(), " ");
					fragment.updateUi(tempSmaliPath, currentTitle.replace(".smali", ""), cursor.getLeftLine(), dexVersion);
				}
			} catch (Exception e) {
				ErrorDlg(e.toString());
			}
		}
	}

	/**
	 * 中文注释: 处理 Smali 到 Java 转换的 Runnable。
	 * English annotation: Runnable for handling Smali to Java conversion.
	 */
	private class SmaliToJavaRunnable implements Runnable {
		@Override
		public void run() {
			showProgressDialog(true);
			final Handler handler = new Handler() {
				@Override
				public void handleMessage(Message msg) {
					showProgressDialog(false);
				}
			};
			new Thread() {
				@Override
				public void run() {
					Looper.prepare();
					try {
						if (smaliEditor.getText().toString().isEmpty()) {
							SketchwareUtil.showMessage(getApplicationContext(), "Editor is empty!");
						} else {
							String javaFilePath = getFilesDir() + "/Smali2Java.java";
							FileUtil.writeFile(javaFilePath, Smali2Java.translate(smaliEditor.getText().toString(), dexVersion));
							intent.setClass(getApplicationContext(), JavaViewActivity.class);
							intent.putExtra("Smali2Java", javaFilePath);
							intent.putExtra("Smali2JavaName", currentTitle.replace(".smali", "").concat(".java"));
							startActivity(intent);
							SketchwareUtil.showMessage(getApplicationContext(), "Success");
						}
					} catch (Exception e) {
						try {
							Matcher matcher = Pattern.compile("\\[(\\d+),(\\d+)\\]").matcher(e.getMessage());
							if (matcher.find()) {
								smaliEditor.setSelection(Integer.parseInt(matcher.group(1)) - 1, Integer.parseInt(matcher.group(2)));
							}
						} catch (Exception ignored) {
						}
						ErrorDlg(e.getMessage());
					}
					handler.sendEmptyMessage(0);
					Looper.loop();
				}
			}.start();
		}
	}


	/**
	 * 中文注释: 保存文件。
	 * English annotation: Saves the file.
	 * @param isOnBackPressed 中文注释: 是否在按下返回键时调用。 English annotation: Whether called when back button is pressed.
	 */
	private void saveFile(boolean isOnBackPressed) {
		showProgressDialog(true);
		new SaveFileTask(isOnBackPressed).start();
	}

	/**
	 * 中文注释: 文件保存任务，在后台线程执行文件保存操作。
	 * English annotation: File save task, performs file saving operations in a background thread.
	 */
	private class SaveFileTask extends Thread {
		/**
		 * 中文注释: 指示是否在按下返回键时保存文件。
		 * English annotation: Indicates whether the file is being saved on back press.
		 */
		private final boolean isOnBackPressed;

		/**
		 * 中文注释: 构造函数。
		 * English annotation: Constructor.
		 * @param isOnBackPressed 中文注释: 是否在按下返回键时保存文件。 English annotation: Whether the file is being saved on back press.
		 */
		SaveFileTask(boolean isOnBackPressed) {
			this.isOnBackPressed = isOnBackPressed;
		}

		@Override
		public void run() {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					showProgressDialog(true);
					final Handler handler = new Handler() {
						@Override
						public void handleMessage(Message msg) {
							showProgressDialog(false);
						}
					};
					new Thread() {
						@Override
						public void run() {
							Looper.prepare();
							try {
								classTree.saveClassDef(Smali.assemble(smaliEditor.getText().toString(), new SmaliOptions(), dexVersion));
								showProgressDialog(false);
								isEditMode = false;
								SketchwareUtil.showMessage(getApplicationContext(), "Saved successfully");
								if (isOnBackPressed) {
									finish();
									Animatoo.animateSlideLeft(TextEditorActivity.this);
								}
							} catch (Exception e) {
								showProgressDialog(false);
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										showProgressDialog(false);
										runOnUiThread(new ErrorRunnable(e));
									}
								});
							}
							handler.sendEmptyMessage(0);
							Looper.loop();
						}
					}.start();
				}
			});
		}
	}

	/**
	 * 中文注释: 处理错误信息的 Runnable，包括解析错误位置和显示错误对话框。
	 * English annotation: Runnable for handling error messages, including parsing error locations and displaying error dialogs.
	 */
	private class ErrorRunnable implements Runnable {
		/**
		 * 中文注释: 异常对象。
		 * English annotation: The exception object.
		 */
		private final Exception exception;

		/**
		 * 中文注释: 构造函数。
		 * English annotation: Constructor.
		 * @param exception 中文注释: 异常对象。 English annotation: The exception object.
		 */
		ErrorRunnable(Exception exception) {
			this.exception = exception;
		}

		@Override
		public void run() {
			try {
				saveCompileError = exception.getMessage();
				Matcher matcher = Pattern.compile("\\[(\\d+),(\\d+)\\]").matcher(saveCompileError);
				if (!matcher.find()) {
					String javaClassName = Smali2JavaName2(intentClassName);
					String[] splitText = splitText(saveCompileError);
					if (splitText.length == 2) {
						String className = splitText[0];
						final String methodName = splitText[1];
						if (Smali2JavaName(className).equals(javaClassName)) {
							try {
								if (isFileCreated.isEmpty()) {
									saveSmaliCodeToFile(smaliEditor.getText().toString(), tempSmaliPath, new FileSaveCallback() {
										@Override
										public void onFileSaved(String filePath) {
											try {
												isFileCreated = "true";
												extractInfo(filePath, methodName);
											} catch (Exception e) {
												ErrorDlg(e.toString());
											}
										}
									});
								} else if (!FileUtil.isExistFile(tempSmaliPath)) {
									saveSmaliCodeToFile(smaliEditor.getText().toString(), tempSmaliPath, new FileSaveCallback() {
										@Override
										public void onFileSaved(String filePath) {
											try {
												isFileCreated = "true";
												extractInfo(filePath, methodName);
											} catch (Exception e) {
												ErrorDlg(e.toString());
											}
										}
									});
								} else {
									extractInfo(tempSmaliPath, methodName);
								}
							} catch (Exception e) {
								ErrorDlg(e.toString());
							}
						}
					}
				} else {
					int lineNumber = Integer.parseInt(matcher.group(1));
					smaliEditor.setSelection(lineNumber - 1, Integer.parseInt(matcher.group(2)));
				}
			} catch (Exception e) {
			}
			ErrorDlg(exception.getMessage());
		}
	}

	/**
	 * 中文注释: 从 ClassDef 对象获取 Smali 代码字符串。
	 * English annotation: Gets the Smali code string from a ClassDef object.
	 * @param classDef 中文注释: ClassDef 对象。 English annotation: The ClassDef object.
	 * @return String 中文注释: Smali 代码字符串。 English annotation: The Smali code string.
	 * @throws Exception 中文注释: 反编译 Smali 时可能抛出的异常。 English annotation: Exception that may be thrown during Smali decompilation.
	 */
	public static String getSmaliByType(ClassDef classDef) throws Exception {
		StringWriter stringWriter = new StringWriter();
		BaksmaliWriter baksmaliWriter = new BaksmaliWriter(stringWriter);
		new ClassDefinition(new BaksmaliOptions(), classDef).writeTo(baksmaliWriter);
		baksmaliWriter.close();
		return stringWriter.toString();
	}

	/**
	 * 中文注释: 显示编译错误对话框，支持点击错误位置跳转到编辑器。
	 * English annotation: Displays a compilation error dialog, supporting clickable error locations to jump to the editor.
	 * @param errorMessage 中文注释: 编译错误信息。 English annotation: The compilation error message.
	 */
	public void showPreviousErrorDlg(String errorMessage) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Error");
		builder.setMessage("");
		builder.setPositiveButton("Cancel", (DialogInterface.OnClickListener) null);

		final AlertDialog dialog = builder.create();
		dialog.getWindow().setBackgroundDrawable(Notify_MT.createDrawable(20, -1));
		dialog.show();

		TextView textView = (TextView) dialog.findViewById(android.R.id.message);
		textView.setClickable(true); // Make the text clickable
		// 中文注释: 使文本可点击
		// English annotation: Make the text clickable

		SpannableString spannableString = new SpannableString(errorMessage);

		Matcher matcher = Pattern.compile("\\[(.*?)\\]").matcher(errorMessage);
		while (matcher.find()) {
			final String match = matcher.group(1);
			spannableString.setSpan(new ClickableSpan() {
				@Override
				public void onClick(View view) {
					try {
						Pattern pattern = Pattern.compile("\\[(\\d+),(\\d+)\\]");
						Matcher lineColumnMatcher = pattern.matcher("[" + match + "]");
						if (lineColumnMatcher.find()) {
							int lineNumber = Integer.parseInt(lineColumnMatcher.group(1));
							int columnNumber = Integer.parseInt(lineColumnMatcher.group(2));

							smaliEditor.setSelection(lineNumber - 1, columnNumber);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
					dialog.dismiss();
				}

				@Override
				public void updateDrawState(TextPaint textPaint) {
					super.updateDrawState(textPaint);
					textPaint.setUnderlineText(false);
					textPaint.setColor(textPaint.linkColor);
				}
			}, matcher.start(), matcher.end(), 33);
		}

		// Set the SpannableString to the TextView
		// 中文注释: 将 SpannableString 设置到 TextView
		// English annotation: Set the SpannableString to the TextView
		textView.setText(spannableString);
		textView.setMovementMethod(LinkMovementMethod.getInstance()); // Enable clickable links
		// 中文注释: 启用可点击链接
		// English annotation: Enable clickable links
		textView.setHighlightColor(0); // Disable text highlighting
		// 中文注释: 禁用文本高亮
		// English annotation: Disable text highlighting
	}

	/**
	 * 中文注释: 将异常转换为字符串，包括堆栈跟踪信息。
	 * English annotation: Converts an exception to a string, including stack trace information.
	 * @param e 中文注释: 异常对象。 English annotation: The exception object.
	 * @return String 中文注释: 异常的字符串表示。 English annotation: The string representation of the exception.
	 */
	private String customException(Exception e) {
		StringWriter stringWriter = new StringWriter();
		e.printStackTrace(new PrintWriter(stringWriter));
		return stringWriter.toString();
	}


	/**
	 * 中文注释: 加载编辑器主题。
	 * English annotation: Loads the editor theme.
	 */
	private void loadTheme() {
		smaliEditor.setColorScheme(getColorScheme("light.json"));
		smaliEditor.setEditorLanguage(getSmaliLanguage("light.json"));
		try {
			TextMateColorScheme colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance());
			smaliEditor.setColorScheme(colorScheme);
			colorScheme.setColor(1, Color.parseColor("#E0E0E0")); // Background color
			// 中文注释: 背景颜色
			// English annotation: Background color
			colorScheme.setColor(3, Color.parseColor("#F0F0F0")); // Line number background
			// 中文注释: 行号背景
			// English annotation: Line number background
			colorScheme.setColor(17, Color.parseColor("#B0B0B0")); // Line number text
			// 中文注释: 行号文本
			// English annotation: Line number text
		} catch (Exception ignored) {
		}
	}

	/**
	 * 中文注释: 根据主题名称获取 TextMateColorScheme。
	 * English annotation: Gets a TextMateColorScheme based on the theme name.
	 * @param themeName 中文注释: 主题文件名。 English annotation: The theme file name.
	 * @return TextMateColorScheme 中文注释: TextMateColorScheme 对象，如果加载失败则返回 null。 English annotation: A TextMateColorScheme object, or null if loading fails.
	 */
	private TextMateColorScheme getColorScheme(String themeName) {
		try {
			AssetManager assets = getAssets();
			return TextMateColorScheme.create(IThemeSourceMT.fromInputStream(assets.open("themes/" + themeName), themeName, null));
		} catch (Exception ignored) {
			return null;
		}
	}

	/**
	 * 中文注释: 根据主题名称获取 Smali 语言配置。
	 * English annotation: Gets the Smali language configuration based on the theme name.
	 * @param themeName 中文注释: 主题文件名。 English annotation: The theme file name.
	 * @return Language 中文注释: Language 对象，如果加载失败则返回 EmptyLanguage。 English annotation: A Language object, or an EmptyLanguage if loading fails.
	 */
	private Language getSmaliLanguage(String themeName) {
		try {
			return TextMateLanguage.create(
			IGrammarSourceMT.fromInputStream(getAssets().open("smali/syntaxes/smali.tmLanguage.json"), "smali.tmLanguage.json", null),
			new InputStreamReader(getAssets().open("smali/language-configuration.json")),
			getColorScheme(themeName).getThemeSource()
			);
		} catch (IOException ignored) {
			return new EmptyLanguage();
		}
	}

	/**
	 * 中文注释: 复制当前行或选中的文本。
	 * English annotation: Copies the current line or selected text.
	 */
	public void _copyLine() {
		Cursor cursor = smaliEditor.getCursor();
		if (cursor.isSelected()) {
			smaliEditor.copyText();
			return;
		}
		int i = cursor.left().line;
		smaliEditor.setSelectionRegion(i, 0, i, smaliEditor.getText().getColumnCount(i));
		smaliEditor.copyText(false);
	}


	/**
	 * 中文注释: 切换当前行或选中区域的注释状态（添加或移除 Smali 注释符号 `#`）。
	 * English annotation: Toggles the comment state (add or remove Smali comment symbol `#`) for the current line or selected region.
	 */
	public void toggleComment() {
		Cursor cursor = smaliEditor.getCursor();
		Content text = smaliEditor.getText();

		if (cursor.isSelected()) {
			int startLine = cursor.getLeftLine();
			int endLine = cursor.getRightLine();

			boolean allCommented = true;
			for (int line = startLine; line <= endLine; line++) {
				String lineStr = text.getLineString(line);
				int firstCharPos = getFirstNonWhitespace(lineStr);

				if (firstCharPos < lineStr.length() && lineStr.charAt(firstCharPos) != '#') {
					allCommented = false;
					break;
				}
			}

			for (int line = startLine; line <= endLine; line++) {
				String lineStr = text.getLineString(line);
				int firstCharPos = getFirstNonWhitespace(lineStr);

				if (firstCharPos >= lineStr.length()) {
					continue;
				}

				if (allCommented) {
					if (lineStr.charAt(firstCharPos) == '#') {
						int endPos = firstCharPos + 1;
						if (endPos < lineStr.length() && lineStr.charAt(endPos) == ' ') {
							endPos++;
						}
						smaliEditor.setSelectionRegion(line, firstCharPos, line, endPos);
						smaliEditor.deleteText();
					}
				} else {
					if (lineStr.charAt(firstCharPos) != '#') {
						smaliEditor.setSelection(line, firstCharPos);
						smaliEditor.commitText("# ");
					}
				}
			}

			smaliEditor.setSelectionRegion(startLine, 0, endLine, text.getColumnCount(endLine));
		} else {
			int line = cursor.getLeftLine();
			String lineStr = text.getLineString(line);
			int firstCharPos = getFirstNonWhitespace(lineStr);

			if (firstCharPos < lineStr.length()) {
				if (lineStr.charAt(firstCharPos) == '#') {
					int endPos = firstCharPos + 1;
					if (endPos < lineStr.length() && lineStr.charAt(endPos) == ' ') {
						endPos++;
					}
					smaliEditor.setSelectionRegion(line, firstCharPos, line, endPos);
					smaliEditor.deleteText();
				} else {
					smaliEditor.setSelection(line, firstCharPos);
					smaliEditor.commitText("# ");
				}
			}
		}
	}

	/**
	 * 中文注释: 获取给定行中第一个非空白字符的索引。
	 * English annotation: Gets the index of the first non-whitespace character in a given line.
	 * @param line 中文注释: 要检查的行字符串。 English annotation: The line string to check.
	 * @return int 中文注释: 第一个非空白字符的索引，如果行全是空白则返回行长度。 English annotation: The index of the first non-whitespace character, or the line length if the line is all whitespace.
	 */
	private int getFirstNonWhitespace(String line) {
		for (int i = 0; i < line.length(); i++) {
			if (!Character.isWhitespace(line.charAt(i))) {
				return i;
			}
		}
		return line.length();
	}

	/**
	 * 中文注释: 删除当前行或选中区域的行。
	 * English annotation: Deletes the current line or lines within the selected region.
	 */
	public void deleteLine() {
		Cursor cursor = smaliEditor.getCursor();
		if (cursor.isSelected()) {
			smaliEditor.deleteText();
			return;
		}

		io.github.rosemoe.sora.text.CharPosition pos = cursor.left();
		int currentLine = pos.getLine();
		int nextLine = currentLine + 1;

		if (nextLine == smaliEditor.getLineCount()) {
			smaliEditor.setSelectionRegion(
			currentLine, 0,
			currentLine, smaliEditor.getText().getColumnCount(currentLine)
			);
		} else {
			smaliEditor.setSelectionRegion(
			currentLine, 0,
			nextLine, 0
			);
		}
		smaliEditor.deleteText();
	}

	/**
	 * 中文注释: 清空当前行或选中区域的行内容。
	 * English annotation: Empties the content of the current line or lines within the selected region.
	 */
	public void emptyLine() {
		final CodeEditor editor = smaliEditor;
		final Content content = editor.getText();
		final Cursor cur = editor.getCursor();

		final int startLine = cur.getLeftLine();
		final int endLine = cur.isSelected() ? cur.getRightLine() : startLine;

		content.beginBatchEdit();
		try {
			for (int line = endLine; line >= startLine; line--) {
				final int lineEnd = content.getColumnCount(line);
				if (lineEnd > 0) {
					content.delete(line, 0, line, lineEnd);
				}
			}
		} finally {
			content.endBatchEdit();
		}

		editor.setSelection(startLine, 0);
	}

	/**
	 * 中文注释: 用剪贴板内容替换当前行或选中区域的行内容。
	 * English annotation: Replaces the content of the current line or lines within the selected region with the clipboard content.
	 */
	private void replaceLine() {
		final CodeEditor editor = smaliEditor;
		final Content content = editor.getText();
		final Cursor cursor = editor.getCursor();

		content.beginBatchEdit();
		try {
			if (cursor.isSelected()) {
				int startLine = cursor.getLeftLine();
				int endLine = cursor.getRightLine();
				int endCol = content.getColumnCount(endLine);

				content.delete(startLine, 0, endLine, endCol);
				editor.pasteText();
				editor.setSelection(startLine, 0);
			} else {
				int line = cursor.getLeftLine();
				int lineLength = content.getColumnCount(line);

				content.delete(line, 0, line, lineLength);
				editor.pasteText();

				editor.setSelection(line, 0);
			}
		} finally {
			content.endBatchEdit();
		}
	}

	/**
	 * 中文注释: 复制当前行或选中区域的行，并粘贴到下一行。
	 * English annotation: Duplicates the current line or lines within the selected region and pastes them on the next line.
	 */
	private void duplicateLine() {
		final CodeEditor editor = smaliEditor;
		final Content content = editor.getText();
		final Cursor cursor = editor.getCursor();

		content.beginBatchEdit();
		try {
			if (cursor.isSelected()) {
				int startLine = cursor.getLeftLine();
				int endLine = cursor.getRightLine();

				StringBuilder duplicateContent = new StringBuilder("\n");
				for (int i = startLine; i <= endLine; i++) {
					duplicateContent.append(content.getLineString(i)).append(i < endLine ? "\n" : "");
				}

				content.insert(
				endLine, content.getColumnCount(endLine),
				duplicateContent.toString()
				);

				int newStartLine = endLine + 1;
				int newEndLine = endLine + 1 + (endLine - startLine);
				editor.setSelectionRegion(
				newStartLine, 0,
				newEndLine, content.getColumnCount(newEndLine)
				);
			} else {
				int line = cursor.getLeftLine();
				int column = cursor.getLeftColumn();
				String lineContent = content.getLineString(line);

				content.insert(
				line, content.getColumnCount(line), "\n" + lineContent);

				editor.setSelection(line, column);
			}
		} finally {

			content.endBatchEdit();
		}
	}

	/**
	 * 中文注释: 将选中区域的文本转换为大写或小写。
	 * English annotation: Converts the selected text to uppercase or lowercase.
	 * @param toUpper 中文注释: 如果为 true 则转换为大写，否则转换为小写。 English annotation: True to convert to uppercase, false to convert to lowercase.
	 */
	public void convertUpperLowerCase(boolean toUpper) {
		final CodeEditor editor = smaliEditor;
		final Content content = editor.getText();
		final Cursor cursor = editor.getCursor();

		if (!cursor.isSelected()) {
			SketchwareUtil.showMessage(this, "Please select the text to be converted first");
			return;
		}

		content.beginBatchEdit();
		try {
			int startLine = cursor.getLeftLine();
			int startCol = cursor.getLeftColumn();
			int endLine = cursor.getRightLine();
			int endCol = cursor.getRightColumn();

			String selectedText = content.subContent(startLine, startCol, endLine, endCol).toString();
			String convertedText = toUpper ? selectedText.toUpperCase() : selectedText.toLowerCase();
			content.replace(startLine, startCol, endLine, endCol, convertedText);
			editor.setSelectionRegion(startLine, startCol, endLine, startCol + convertedText.length());
		} finally {
			content.endBatchEdit();
		}
	}

	/**
	 * 中文注释: 增加当前行或选中区域的缩进。
	 * English annotation: Increases the indentation of the current line or selected region.
	 */
	public void increaseIndent() {
		final CodeEditor editor = smaliEditor;
		final Content content = editor.getText();
		final Cursor cursor = editor.getCursor();
		final int tabWidth = editor.getTabWidth();
		final String tabSpaces = String.format("%" + tabWidth + "s", "");

		content.beginBatchEdit();
		try {
			if (cursor.isSelected()) {
				int startLine = cursor.getLeftLine();
				int endLine = cursor.getRightLine();

				for (int line = startLine; line <= endLine; line++) {
					content.insert(line, 0, tabSpaces);
				}
			} else {
				int line = cursor.getLeftLine();
				int column = cursor.getLeftColumn();
				content.insert(line, column, tabSpaces);
				editor.setSelection(line, column + tabWidth);
			}
		} finally {
			content.endBatchEdit();
		}
	}

	/**
	 * 中文注释: 减少当前行或选中区域的缩进。
	 * English annotation: Decreases the indentation of the current line or selected region.
	 */
	public void decreaseIndent() {
		final CodeEditor editor = smaliEditor;
		final Content content = editor.getText();
		final Cursor cursor = editor.getCursor();
		final int tabWidth = editor.getTabWidth();

		content.beginBatchEdit();
		try {
			int startLine = cursor.isSelected() ? cursor.getLeftLine() : cursor.getLeftLine();
			int endLine = cursor.isSelected() ? cursor.getRightLine() : cursor.getLeftLine();

			for (int line = startLine; line <= endLine; line++) {
				String lineText = content.getLineString(line);
				int spacesToRemove = 0;

				while (spacesToRemove < lineText.length() &&
				lineText.charAt(spacesToRemove) == ' ') {
					spacesToRemove++;
				}

				if (spacesToRemove > 0) {
					int removeCount = spacesToRemove >= tabWidth ? tabWidth : spacesToRemove;
					content.delete(line, 0, line, removeCount);
				}
			}
		} finally {
			content.endBatchEdit();
		}
	}


	/**
	 * 中文注释: 重新加载编辑器并跳转到指定位置。
	 * English annotation: Reloads the editor and jumps to the specified position.
	 * @param position 中文注释: 要跳转到的行号（0-indexed）。 English annotation: The line number to jump to (0-indexed).
	 */
	private void reloadEditorPosition(int position) {
		new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
			@Override
			public void run() {
				try {
					smaliEditor.post(new Runnable() {
						@Override
						public void run() {
							smaliEditor.jumpToLine(position);
						}
					});
				} catch (Exception e) {
					ErrorDlg(e.toString());
				}
			}
		}, 200);
	}



	/**
	 * 中文注释: 显示“跳转到行”对话框，允许用户输入行号并跳转。
	 * English annotation: Displays the "Jump to line" dialog, allowing the user to enter a line number and jump to it.
	 */
	public void jumpToLine() {
		View view = LayoutInflater.from(this).inflate(R.layout.dialog_jump_to_line, null);
		TextInputLayout textInputLayout = view.findViewById(R.id.textInputLayout);
		EditText editText = view.findViewById(R.id.editText);

		// Set dynamic hint
		// 中文注释: 设置动态提示
		// English annotation: Set dynamic hint
		String hint = "Line number [1-" + smaliEditor.getLineCount() + "]";
		textInputLayout.setHint(hint);

		AlertDialog.Builder builder = new AlertDialog.Builder(this)
		.setTitle("Jump to line")
		.setView(view)
		.setPositiveButton("OK", null)
		.setNegativeButton("Cancel", null);

		AlertDialog dialog_mt = builder.create();


		dialog_mt.getWindow().setBackgroundDrawable(Notify_MT.createDrawable(20, Color.parseColor("#FFFFFF")));
		dialog_mt.show();
		dialog_mt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener(){

			@Override
			public void onClick(View v){

				if (editText.getText().toString().equals("")) {
					textInputLayout.setError("Enter something !");
				}
				else {
					try {
						smaliEditor.jumpToLine(Integer.parseInt(editText.getText().toString()) - 1);
						dialog_mt.dismiss();
					} catch (Exception e) {
						textInputLayout.setError("Value is out of range.");
					}
				}

			}
		});
	}

	/**
	 * 中文注释: 隐藏编辑器中所有弹出的窗口。
	 * English annotation: Hides all popped-up windows in the editor.
	 */
	public void dismissEditorWindow(){
		new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
			@Override
			public void run() {
				try {
					smaliEditor.hideEditorWindows();
				} catch (Exception e) {
					ErrorDlg(e.toString());
				}
			}
		}, 50);
	}

	/**
	 * 中文注释: 显示或隐藏进度对话框。
	 * English annotation: Shows or hides the progress dialog.
	 * @param show 中文注释: 如果为 true 则显示，否则隐藏。 English annotation: True to show, false to hide.
	 */
	private void showProgressDialog(boolean show) {
		if (show) {
			if (progressDialog == null) {
				progressDialog = new ProgressDialog(this);
				progressDialog.setCancelable(false);
				progressDialog.setCanceledOnTouchOutside(false);
				progressDialog.requestWindowFeature(1);
				progressDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			}
			progressDialog.setMessage(null);
			try {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						progressDialog.show();
					}
				});
			} catch (WindowManager.BadTokenException e) {
				// Ignore
				// 中文注释: 忽略
				// English annotation: Ignore
			}
			progressDialog.setContentView(R.layout.loading);
			LinearLayout backgroundLayout = progressDialog.findViewById(R.id.background);
			GradientDrawable gradientDrawable = new GradientDrawable();
			gradientDrawable.setColor(Color.parseColor("#E0E0E0"));
			gradientDrawable.setCornerRadius(40.0f);
			gradientDrawable.setStroke(0, Color.WHITE);
			progressDialog.findViewById(R.id.linear2).setBackground(gradientDrawable);
			((LinearLayout) progressDialog.findViewById(R.id.layout_progress)).addView(new RadialProgressView(this));
		} else if (progressDialog != null) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					progressDialog.dismiss();
				}
			});
		}
	}

	/**
	 * 中文注释: 获取当前光标所在行的 Smali 指令。
	 * English annotation: Gets the Smali instruction on the current cursor line.
	 * @return String 中文注释: Smali 指令字符串，如果当前行不是 Smali 指令则返回 null。 English annotation: The Smali instruction string, or null if the current line is not a Smali instruction.
	 */
	public String getCurrentLineInstruction() {
		CodeEditor editor = smaliEditor;
		Cursor cursor = editor.getCursor();
		Content content = editor.getText();

		int line = cursor.getLeftLine();
		String lineText = content.getLineString(line);
		String trimmed = lineText.trim();

		if (trimmed.isEmpty()) {
			return null;
		}


		int endOfFirstWord = 0;
		while (endOfFirstWord < trimmed.length()) {
			char c = trimmed.charAt(endOfFirstWord);
			if (Character.isWhitespace(c)) break;  // Fixed syntax here
			// 中文注释: 这里修正了语法
			// English annotation: Fixed syntax here
			if (c == '{' || c == '}' || c == ';') break;
			endOfFirstWord++;
		}

		String firstWord = trimmed.substring(0, endOfFirstWord);
		return SmaliInstructionHelper.isSmaliInstruction(firstWord) ? firstWord : null;
	}

	/**
	 * 中文注释: 将 Smali 风格的类名转换为 Java 风格的类名（例如：Lcom/example/MyClass; -> com.example.MyClass）。
	 * English annotation: Converts a Smali-style class name to a Java-style class name (e.g., Lcom/example/MyClass; -> com.example.MyClass).
	 * @param smaliName 中文注释: Smali 风格的类名。 English annotation: The Smali-style class name.
	 * @return String 中文注释: Java 风格的类名。 English annotation: The Java-style class name.
	 */
	public String Smali2JavaName(String smaliName) {
		return smaliName.substring(1, smaliName.length() - 1).replace('/', '.');
	}

	/**
	 * 中文注释: 将 Smali 风格的类名转换为 Java 风格的类名（例如：com/example/MyClass -> com.example.MyClass）。
	 * English annotation: Converts a Smali-style class name to a Java-style class name (e.g., com/example/MyClass -> com.example.MyClass).
	 * @param smaliName 中文注释: Smali 风格的类名。 English annotation: The Smali-style class name.
	 * @return String 中文注释: Java 风格的类名。 English annotation: The Java-style class name.
	 */
	public String Smali2JavaName2(String smaliName) {
		return smaliName.replace('/', '.');
	}

	/**
	 * 中文注释: 从 Smali 风格的类名中提取仅包含斜杠的路径部分（例如：Lcom/example/MyClass; -> com/example/MyClass）。
	 * English annotation: Extracts only the slash-separated path from a Smali-style class name (e.g., Lcom/example/MyClass; -> com/example/MyClass).
	 * @param smaliName 中文注释: Smali 风格的类名。 English annotation: The Smali-style class name.
	 * @return String 中文注释: 仅包含斜杠的路径。 English annotation: The slash-separated path only.
	 */
	public String smali2OnlySlash(String smaliName) {
		return smaliName.substring(1, smaliName.length() - 1);
	}

	/**
	 * 中文注释: 从给定的字符串中提取最后一个斜杠后的子字符串。
	 * English annotation: Extracts the substring after the last slash from the given string.
	 * @param str 中文注释: 输入字符串。 English annotation: The input string.
	 * @return String 中文注释: 最后一个斜杠后的子字符串，如果没有斜杠则返回原字符串。 English annotation: The substring after the last slash, or the original string if no slash is found.
	 */
	public static String extractSubstringAfterLastSlash(String str) {
		int lastSlashIndex = str.lastIndexOf('/');
		return lastSlashIndex != -1 ? str.substring(lastSlashIndex + 1) : str;
	}

	/**
	 * 中文注释: 根据“->”分隔符分割文本。
	 * English annotation: Splits the text by the "->" delimiter.
	 * @param text 中文注释: 要分割的文本。 English annotation: The text to split.
	 * @return String[] 中文注释: 分割后的字符串数组。 English annotation: An array of strings after splitting.
	 */
	public String[] splitText(String text) {
		return text.split("->");
	}

	/**
	 * 中文注释: 获取文本中指定分隔符之前的子字符串。
	 * English annotation: Gets the substring before the specified delimiter in the text.
	 * @param text 中文注释: 输入文本。 English annotation: The input text.
	 * @param delimiter 中文注释: 分隔符。 English annotation: The delimiter.
	 * @return String 中文注释: 分隔符之前的子字符串，如果未找到分隔符则返回空字符串。 English annotation: The substring before the delimiter, or an empty string if the delimiter is not found.
	 */
	private String _getTextBefore(String text, String delimiter) {
		int index = text.indexOf(delimiter);
		return index != -1 ? text.substring(0, index) : "";
	}

	/**
	 * 中文注释: 获取输入字符串中最外层引号的起始和结束位置。
	 * English annotation: Gets the start and end positions of the outermost quotes in the input string.
	 * @param input 中文注释: 输入字符串。 English annotation: The input string.
	 * @return int[] 中文注释: 包含起始和结束位置的整数数组，如果未找到则为 {-1, -1}。 English annotation: An integer array containing the start and end positions, or {-1, -1} if not found.
	 */
	public static int[] getOuterQuotePositions(String input) {
		int startQuote = -1;
		int endQuote = -1;
		boolean insideString = false;
		boolean escapeNext = false;

		for (int i = 0; i < input.length(); i++) {
			char currentChar = input.charAt(i);
			if (currentChar == '\\') {
				escapeNext = true;
			} else if (currentChar == '"' && !escapeNext) {
				if (!insideString) {
					startQuote = i;
					insideString = true;
				} else {
					endQuote = i;
					break;
				}
			} else {
				escapeNext = false;
			}
		}

		return new int[]{startQuote, endQuote};
	}

	/**
	 * 中文注释: 将文本复制到剪贴板。
	 * English annotation: Copies the text to the clipboard.
	 * @param text 中文注释: 要复制的文本。 English annotation: The text to copy.
	 */
	public void copiedToClipboard(String text) {
		((ClipboardManager) getSystemService(getApplicationContext().CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("clipboard", text));
		SketchwareUtil.showMessage(this, "Text has been copied to clipboard");
	}

	/**
	 * 中文注释: 加载 TextMate 主题。
	 * English annotation: Loads TextMate themes.
	 * @param context 中文注释: 上下文对象。 English annotation: The context object.
	 */
	public static void loadTMThemes(Context context) {
		FileProviderRegistry.getInstance()
		.addFileProvider(new AssetsFileResolver(context.getAssets()));
		ThemeRegistry scheme = ThemeRegistry.getInstance();
		String light = "light.json";  // Only this theme will be loaded
		// 中文注释: 只加载此主题
		// English annotation: Only this theme will be loaded

		String path = "themes/" + light;
		try {
			scheme.loadTheme(
			new ThemeModel(
			IThemeSourceMT.fromInputStream(
			FileProviderRegistry.getInstance().tryGetInputStream(path),
			path,
			null),
			light));
		} catch (Exception e) {
			e.printStackTrace();
		}

		scheme.setTheme(light);  // Set the quietlight theme as active
		// 中文注释: 将 quietlight 主题设置为活动主题
		// English annotation: Set the quietlight theme as active
	}
}