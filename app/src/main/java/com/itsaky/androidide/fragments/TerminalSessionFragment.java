package com.termux.app;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;

import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;
import android.app.AlertDialog;  
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.autofill.AutofillManager;
import android.widget.EditText;
import android.app.Activity;
import androidx.appcompat.app.AppCompatActivity;

import android.widget.RelativeLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;
//import com.itsaky.androidide.app.BaseIDEActivity;
import com.termux.R;
import com.termux.app.*;
import com.termux.app.activities.HelpActivity;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import com.itsaky.androidide.fragments.*;
/**
 * A terminal emulator activity.
 * <p/>
 * See
 * <ul>
 * <li>http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android</li>
 * <li>https://code.google.com/p/android/issues/detail?id=6426</li>
 * </ul>
 * about memory leaks.
 */


public class TerminalSessionFragment extends BaseIDEFragment implements ServiceConnection {



    View bindid;
    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    protected TermuxService mTermuxService;

    /**
     * The {@link TerminalView} shown in  {@link TerminalSessionFragment} that displays the terminal.
     */
    protected TerminalView mTerminalView;

    /**
     *  The {@link TerminalViewClient} interface implementation to allow for communication between
     *  {@link TerminalView} and {@link TerminalSessionFragment}.
     */
    protected TermuxTerminalViewClient mTermuxTerminalViewClient;

    /**
     *  The {@link TerminalSessionClient} interface implementation to allow for communication between
     *  {@link TerminalSession} and {@link TerminalSessionFragment}.
     */
    protected TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /**
     * Termux app shared preferences manager.
     */
    protected TermuxAppSharedPreferences mPreferences;

    /**
     * Termux app SharedProperties loaded from termux.properties
     */
    protected TermuxAppSharedProperties mProperties;

    /**
     * The root view of the {@link TerminalSessionFragment}.
     */
    protected TermuxActivityRootView mTermuxActivityRootView;

    /**
     * The space at the bottom of {@link @mTermuxActivityRootView} of the {@link TerminalSessionFragment}.
     */
    protected View mTermuxActivityBottomSpaceView;

    /**
     * The terminal extra keys view.
     */
    protected ExtraKeysView mExtraKeysView;

    /**
     * The client for the {@link #mExtraKeysView}.
     */
    protected TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;

    /**
     * The termux sessions list controller.
     */
    protected TermuxSessionsListViewController mTermuxSessionListViewController;

    /**
     * The last toast shown, used cancel current toast before showing new in {@link #showToast(String, boolean)}.
     */
    protected Toast mLastToast;

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should probably be treated as background.
     */
    protected boolean mIsVisible;

    /**
     * If onResume() was called after onCreate().
     */
    protected boolean mIsOnResumeAfterOnCreate = false;

    /**
     * If activity was restarted like due to call to {@link #recreate()} after receiving
     * {@link TERMUX_ACTIVITY#ACTION_RELOAD_STYLE}, system dark night mode was changed or activity
     * was killed by android.
     */
    protected boolean mIsActivityRecreated = false;

    /**
     * The {@link TerminalSessionFragment} is in an invalid state and must not be run.
     */
    protected boolean mIsInvalidState;

    protected int mNavBarHeight;

    protected float mTerminalToolbarDefaultHeight;


    protected static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    protected static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    protected static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    protected static final int CONTEXT_MENU_AUTOFILL_ID = 2;
    protected static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    protected static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    protected static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;
    protected static final int CONTEXT_MENU_HELP_ID = 7;
    protected static final int CONTEXT_MENU_REPORT_ID = 9;

    protected static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    protected static final String ARG_ACTIVITY_RECREATED = "activity_recreated";

    protected static final String LOG_TAG = "TerminalSessionFragment";

    // @SuppressLint("InflateParams")
    // @NonNull
    // @Override
    // protected View bindLayout() {
        // return getLayoutInflater().inflate(R.layout.activity_termux, null, false);
    // }

    @SuppressLint("InflateParams")
    @NonNull
    @Override
   public View bindLayout(LayoutInflater inflater, ViewGroup container) {
    return inflater.inflate(R.layout.activity_termux, container, false);
}






    @Override
    public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        Logger.logDebug(LOG_TAG, "onCreate");
        mIsOnResumeAfterOnCreate = true;

        if (savedInstanceState != null)
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false);

        
        ReportActivity.deleteReportInfoFilesOlderThanXDays(requireContext(), 14, false);
        reloadProperties();
        // Load Termux app SharedProperties from disk
        mProperties = TermuxAppSharedProperties.getProperties();
        mPreferences = TermuxAppSharedPreferences.build(requireContext(), true);
        if (mPreferences == null) {
            // An AlertDialog should have shown to kill the app, so we don't continue running activity code
            mIsInvalidState = true;
            return;
        }

        setActivityTheme();

        setMargins();

        // 使用 getView() 来查找视图
        if (getView() != null) {
            mTermuxActivityRootView = getView().findViewById(R.id.activity_termux_root_view);
            // 这里可能需要调整，因为 setActivity 可能需要的是 Activity 实例，需确认是否有替代方法
            mTermuxActivityRootView.setActivity(null);
            mTermuxActivityBottomSpaceView = getView().findViewById(R.id.activity_termux_bottom_space_view);
            mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener());

            View content = getView().findViewById(android.R.id.content);
            content.setOnApplyWindowInsetsListener((v, insets) -> {
                mNavBarHeight = insets.getSystemWindowInsetBottom();
                return insets;
            });
        }

        if (mProperties.isUsingFullScreen()) {
            
            requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setTermuxTerminalViewAndClients();

        setTerminalToolbarView(savedInstanceState);

        setNewSessionButtonView();

        setToggleKeyboardView();

        registerForContextMenu(mTerminalView);

        
        FileReceiverActivity.updateFileReceiverActivityComponentsState(requireContext());

        try {
            

             Intent serviceIntent = new Intent(requireContext(), TermuxService.class);
        requireContext().startService(serviceIntent);
        if (!requireContext().bindService(serviceIntent, this, 0)) {
            throw new RuntimeException("bindService() failed");
        }
        
            if (!requireContext().bindService(serviceIntent, this, 0))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG,"TerminalSessionFragment failed to start TermuxService", e);
            
            Logger.showToast(requireContext(),
                getString(e.getMessage() != null && e.getMessage().contains("app is in background") ?
                    R.string.error_termux_service_start_failed_bg : R.string.error_termux_service_start_failed_general),
                true);
            mIsInvalidState = true;
            return;
        }

        
        TermuxUtils.sendTermuxOpenedBroadcast(requireContext());
    }

    @Override
    public void onStart() {
        super.onStart();

        Logger.logDebug(LOG_TAG, "onStart");

        if (mIsInvalidState) return;

        mIsVisible = true;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStart();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();

        if (mPreferences.isTerminalMarginAdjustmentEnabled())
            addTermuxActivityRootViewGlobalLayoutListener();
    }

    @Override
    public void onResume() {
        super.onResume();

        Logger.logVerbose(LOG_TAG, "onResume");

        if (mIsInvalidState) return;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();

        
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(requireContext(), LOG_TAG);

        mIsOnResumeAfterOnCreate = false;
    }

    @Override
    public void onStop() {
        super.onStop();

        Logger.logDebug(LOG_TAG, "onStop");

        if (mIsInvalidState) return;

        mIsVisible = false;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStop();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStop();

        removeTermuxActivityRootViewGlobalLayoutListener();

        getDrawer().closeDrawers();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Logger.logDebug(LOG_TAG, "onDestroy");

          if (mLastToast != null) {
            mLastToast.cancel();
            mLastToast = null;
        }

        if (mIsInvalidState) return;

        if (mTermuxService != null) {
            // Do not leave service and session clients with references to activity.
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }

        try {
            // unbindService(this);
                 requireActivity().unbindService(this);
        } catch (Exception e) {
            // ignore.
        }
    }











    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState");

        super.onSaveInstanceState(savedInstanceState);
        saveTerminalToolbarTextInput(savedInstanceState);
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true);
    }





    /**
     * Part of the {@link ServiceConnection} interface. The service is bound with
     * {@link #bindService(Intent, ServiceConnection, int)} in {@link #onCreate(Bundle)} which will cause a call to this
     * callback method.
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        Logger.logDebug(LOG_TAG, "onServiceConnected");

        mTermuxService = ((TermuxService.LocalBinder) service).service;

        setTermuxSessionsListView();

        final Intent intent = requireActivity().getIntent();
        requireActivity().setIntent(null);

        final String workingDir;
        final String sessionName;
        if (intent != null && intent.getExtras() != null) {
            workingDir = intent.getExtras().getString(TERMUX_ACTIVITY.EXTRA_SESSION_WORKING_DIR, null);
            sessionName = intent.getExtras().getString(TERMUX_ACTIVITY.EXTRA_SESSION_NAME, null);
        } else {
            workingDir = null;
            sessionName = null;
        }

        boolean launchFailsafe =
            intent != null && intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION,
                false);

        if (mTermuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                TermuxInstaller.setupBootstrapIfNeeded(requireActivity(), () -> {
                    if (mTermuxService == null) return; // Activity might have been destroyed.
                    try {
                        setupTermuxSessionOnServiceConnected(
                            intent,
                            workingDir,
                            sessionName,
                            null,
                            launchFailsafe
                        );
                    } catch (WindowManager.BadTokenException e) {
                        // Activity finished - ignore.
                    }
                });
            } else {
                // The service connected while not in foreground - just bail out.
                finishActivityIfNotFinishing();
            }
        } else {

            final Optional<TermuxSession> existingSession = workingDir == null ? Optional.empty() :
                mTermuxService.getTermuxSessions().stream().filter(session -> Objects.equals(
                    session.getTerminalSession().getCwd(), workingDir)).findFirst();

            setupTermuxSessionOnServiceConnected(
                intent,
                workingDir,
                sessionName,
                existingSession.orElse(null),
                launchFailsafe
            );
        }

        // Update the {@link TerminalSession} and {@link TerminalEmulator} clients.
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);
    }

    protected void setupTermuxSessionOnServiceConnected(
        Intent intent,
        String workingDir,
        String sessionName,
        TermuxSession existingSession,
        boolean launchFailsafe
    ) {
        if (mTermuxService.isTermuxSessionsEmpty()) {
            onCreateNewSession(launchFailsafe, sessionName, workingDir);
            return;
        }

        if (existingSession != null) {
            // requested to open a session with a specific working directory
            // a session is already opened with the provided working directory

            if (existingSession.getExecutionCommand().isFailsafe != launchFailsafe) {
                // the existing session's failsafe status does not match with the requested
                // failsafe status, create a new session
                onCreateNewSession(launchFailsafe, sessionName,
                    workingDir);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(
                    existingSession.getTerminalSession());
            }
        } else if (workingDir != null) {
            // working directory is provided, but no session has that specific CWD
            onCreateNewSession(launchFailsafe, sessionName,
                workingDir);
        } else {
            // no working directory provided
            mTermuxTerminalSessionActivityClient.setCurrentSession(
                mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected");

        // Respect being stopped from the {@link TermuxService} notification action.
        finishActivityIfNotFinishing();
    }






    private void reloadProperties() {
        mProperties.loadTermuxPropertiesFromDisk();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadProperties();
    }

private void setActivityTheme() {
// TermuxActivity termuxActivity = (TermuxActivity) requireActivity(); // 强制转换

        // Update NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
         // Set activity night mode. If NightMode.SYSTEM is set, then android will automatically
        // trigger recreation of activity when uiMode/dark mode configuration is changed so that
        // day or night theme takes affect.
        AppCompatActivityUtils.setNightMode(termuxActivity, NightMode.getAppNightMode().getName(), true);
    }

    private void setMargins() {
        RelativeLayout relativeLayout = requireView().findViewById(R.id.activity_termux_root_relative_layout);
        int marginHorizontal = mProperties.getTerminalMarginHorizontal();
        int marginVertical = mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }



    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView().getViewTreeObserver().addOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView().getViewTreeObserver().removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }



private void setTermuxTerminalViewAndClients() {
// TermuxActivity termuxActivity = (TermuxActivity) requireActivity(); // 强制转换

        mTermuxTerminalSessionActivityClient = onCreateTerminalSessionClient();
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(termuxActivity, mTermuxTerminalSessionActivityClient);

        mTerminalView = requireView().findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);
        mTermuxTerminalViewClient.onCreate();
        mTermuxTerminalSessionActivityClient.onCreate();
    }

    // private void setTermuxTerminalViewAndClients() {
        // // Set termux terminal view and session clients
        // mTermuxTerminalSessionActivityClient = onCreateTerminalSessionClient();
        // mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);

        // // Set termux terminal view
        // mTerminalView = getView().findViewById(R.id.terminal_view);
        // mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);

        // if (mTermuxTerminalViewClient != null)
            // mTermuxTerminalViewClient.onCreate();

        // if (mTermuxTerminalSessionActivityClient != null)
            // mTermuxTerminalSessionActivityClient.onCreate();
    // }
TermuxActivity termuxActivity = (TermuxActivity) requireActivity(); // 强制转换

    @NonNull
    public TermuxTerminalSessionActivityClient onCreateTerminalSessionClient() {
        return new TermuxTerminalSessionActivityClient(termuxActivity);
    }

    // private void setTermuxSessionsListView() {
        // ListView termuxSessionsListView = requireView().findViewById(R.id.terminal_sessions_list);
        // mTermuxSessionListViewController = new TermuxSessionsListViewController(this, mTermuxService.getTermuxSessions());
        // termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        // termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        // termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);
    // }
private void setTermuxSessionsListView() {
        ListView list = requireView().findViewById(R.id.terminal_sessions_list);
        mTermuxSessionListViewController = new TermuxSessionsListViewController(termuxActivity, mTermuxService.getTermuxSessions());
        list.setAdapter(mTermuxSessionListViewController);
        list.setOnItemClickListener(mTermuxSessionListViewController);
        list.setOnItemLongClickListener(mTermuxSessionListViewController);
    }



private void setTerminalToolbarView(Bundle savedInstanceState) {

        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(termuxActivity, mTerminalView,
                mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);
        final ViewPager pager = getTerminalToolbarViewPager();
        if (mPreferences.shouldShowTerminalToolbar()) pager.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams layoutParams = pager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;
        pager.setAdapter(new TerminalToolbarViewPager.PageAdapter(termuxActivity, null));
        pager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(termuxActivity, pager));
    }
    // private void setTerminalToolbarView(Bundle savedInstanceState) {
        // mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView,
            // mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);

        // final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        // if (mPreferences.shouldShowTerminalToolbar()) terminalToolbarViewPager.setVisibility(View.VISIBLE);

        // ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        // mTerminalToolbarDefaultHeight = layoutParams.height;

        // setTerminalToolbarHeight();

        // String savedTextInput = null;
        // if (savedInstanceState != null)
            // savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);

        // terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        // terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
    // }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = Math.round(mTerminalToolbarDefaultHeight *
            (mTermuxTerminalExtraKeys.getExtraKeysInfo() == null ? 0 : mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length) *
            mProperties.getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }


    public void toggleTerminalToolbar() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        final boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(requireContext(), getString(showNow ? R.string.msg_enabling_terminal_toolbar : R.string.msg_disabling_terminal_toolbar), true);
        getTerminalToolbarViewPager().setVisibility(showNow ? View.VISIBLE : View.GONE); // 注意是 showNow
        if (showNow && isTerminalToolbarTextInputViewSelected()) {
            // Focus the text input view if just revealed.
            requireView().findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
    }

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        final EditText textInputView = requireView().findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty()) savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }

    private void setNewSessionButtonView() {
        View newSessionButton = requireView().findViewById(R.id.new_session_button);
        newSessionButton.setOnClickListener(v -> onCreateNewSession(false, null, null));
        newSessionButton.setOnLongClickListener(v -> {
            TextInputDialogUtils.textInput(requireActivity(), R.string.title_create_named_session, null,
                R.string.action_create_named_session_confirm, text -> onCreateNewSession(false, text, null),
                R.string.action_new_session_failsafe, text -> onCreateNewSession(true, text, null),
                -1, null, null);
            return true;
        });
    }

    protected void onCreateNewSession(boolean isFailsafe, String sessionName, String workingDirectory) {
        mTermuxTerminalSessionActivityClient.addNewSession(isFailsafe, sessionName, workingDirectory);
    }

    private void setToggleKeyboardView() {
        requireView().findViewById(R.id.toggle_keyboard_button).setOnClickListener(v -> {
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
        });

        requireView().findViewById(R.id.toggle_keyboard_button).setOnLongClickListener(v -> {
            toggleTerminalToolbar();
            return true;
        });
    }





    @SuppressLint("RtlHardcoded")
  //  @Override
    public void onBackPressed() {
        if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
            getDrawer().closeDrawers();
        } else {
            finishActivityIfNotFinishing();
        }
    }

    private void finishActivityIfNotFinishing() {
        if (!requireActivity().isFinishing()) {
            requireActivity().finish();
        }
    }

    /** Show a toast and dismiss the last one if still visible. */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(requireContext(), text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        mLastToast.show();
    }



    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;

        boolean addAutoFillMenu = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AutofillManager autofillManager = (AutofillManager) requireContext().getSystemService(/**Context.AUTOFILL_SERVICE*/"autofill");

            if (autofillManager != null && autofillManager.isEnabled()) {
                addAutoFillMenu = true;
            }
        }

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (!DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText()))
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        if (addAutoFillMenu)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_ID, Menu.NONE, R.string.action_autofill_password);
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, getResources().getString(R.string.action_kill_process, getCurrentSession().getPid())).setEnabled(currentSession.isRunning());
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on).setCheckable(true).setChecked(mPreferences.shouldKeepScreenOn());
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue);
    }

    /** Hook system menu to show context menu instead. */
  //  @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mTerminalView.showContextMenu();
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        TerminalSession session = getCurrentSession();

        switch (item.getItemId()) {
            case CONTEXT_MENU_SELECT_URL_ID:
                mTermuxTerminalViewClient.showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                mTermuxTerminalViewClient.shareSessionTranscript();
                return true;
            case CONTEXT_MENU_SHARE_SELECTED_TEXT:
                mTermuxTerminalViewClient.shareSelectedText();
                return true;
            case CONTEXT_MENU_AUTOFILL_ID:
                requestAutoFill();
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                onResetTerminalSession(session);
                return true;
            case CONTEXT_MENU_KILL_PROCESS_ID:
                showKillSessionDialog(session);
                return true;
            case CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON:
                toggleKeepScreenOn();
                return true;
            case CONTEXT_MENU_HELP_ID:
                ActivityUtils.startActivity(requireContext(), new Intent(requireContext(), HelpActivity.class));
                return true;
            case CONTEXT_MENU_REPORT_ID:
                mTermuxTerminalViewClient.reportIssueFromTranscript();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

//    @Override
    public void onContextMenuClosed(Menu menu) {
       // super.onContextMenuClosed(menu);
        // onContextMenuClosed() is triggered twice if back button is pressed to dismiss instead of tap for some reason
        mTerminalView.onContextMenuClosed(menu);
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;

        final AlertDialog.Builder b = new AlertDialog.Builder(requireContext());
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);

            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onResetTerminalSession();
        }
    }


    private void toggleKeepScreenOn() {
        if (mTerminalView.getKeepScreenOn()) {
            mTerminalView.setKeepScreenOn(false);
            mPreferences.setKeepScreenOn(false);
        } else {
            mTerminalView.setKeepScreenOn(true);
            mPreferences.setKeepScreenOn(true);
        }
    }

private void requestAutoFill() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
           
           AutofillManager autofillManager = (AutofillManager) requireContext().getSystemService(/**Context.AUTOFILL_SERVICE*/"autofill");

            if (autofillManager != null && autofillManager.isEnabled()) {
                autofillManager.requestAutofill(mTerminalView);
            }
        }
    }


    /**
     * For processes to access primary external storage (/sdcard, /storage/emulated/0, ~/storage/shared),
     * termux needs to be granted legacy WRITE_EXTERNAL_STORAGE or MANAGE_EXTERNAL_STORAGE permissions
     * if targeting targetSdkVersion 30 (android 11) and running on sdk 30 (android 11) and higher.
     */
    public void requestStoragePermission(boolean isPermissionCallback) {
        new Thread() {
            @Override
            public void run() {
                // Do not ask for permission again
                int requestCode = isPermissionCallback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;

                // If permission is granted, then also setup storage symlinks.
                if(PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                    requireActivity(), requestCode, !isPermissionCallback)) {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(requireActivity(), LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request));

                    TermuxInstaller.setupStorageSymlinks(requireActivity());
                } else {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(requireActivity(), LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request));
                }
            }
        }.start();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: "  + resultCode + ", data: "  + IntentUtils.getIntentString(data));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.logVerbose(LOG_TAG, "onRequestPermissionsResult: requestCode: " + requestCode + ", permissions: "  + Arrays.toString(permissions) + ", grantResults: "  + Arrays.toString(grantResults));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }



    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() {
        return mTermuxTerminalExtraKeys;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
    }

    public DrawerLayout getDrawer() {
        return (DrawerLayout) requireView().findViewById(R.id.drawer_layout);
    }


    public ViewPager getTerminalToolbarViewPager() {
        return (ViewPager) requireView().findViewById(R.id.terminal_toolbar_view_pager);
    }

    public float getTerminalToolbarDefaultHeight() {
        return mTerminalToolbarDefaultHeight;
    }

    public boolean isTerminalViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 0;
    }

    public boolean isTerminalToolbarTextInputViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 1;
    }


    public void termuxSessionListNotifyUpdated() {
        mTermuxSessionListViewController.notifyDataSetChanged();
    }
    
    
    public boolean isFragmentVisible() {
    return isAdded() && !isHidden() && getUserVisibleHint() && mIsVisible;
}


    public boolean isOnResumeAfterOnCreate() {
        return mIsOnResumeAfterOnCreate;
    }

    public boolean isActivityRecreated() {
        return mIsActivityRecreated;
    }



    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        if (mTerminalView != null)
            return mTerminalView.getCurrentSession();
        else
            return null;
    }

    public TermuxAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxAppSharedProperties getProperties() {
        return mProperties;
    }




    public static void updateTermuxActivityStyling(Context context, boolean recreateActivity) {
        // Make sure that terminal styling is always applied.
        Intent stylingIntent = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        stylingIntent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreateActivity);
        context.sendBroadcast(stylingIntent);
    }

    private void fixTermuxActivityBroadcastReceiverIntent(Intent intent) {
        if (intent == null) return;

        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    private void reloadActivityStyling(boolean recreateActivity) {
        if (mProperties != null) {
            reloadProperties();

            if (mExtraKeysView != null) {
                mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
                mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
            }

            // Update NightMode.APP_NIGHT_MODE
            TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        }

        setMargins();
        setTerminalToolbarHeight();

        FileReceiverActivity.updateFileReceiverActivityComponentsState(requireContext());

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onReloadActivityStyling();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadActivityStyling();

        // To change the activity and drawer theme, activity needs to be recreated.
        // It will destroy the activity, including all stored variables and views, and onCreate()
        // will be called again. Extra keys input text, terminal sessions and transcripts will be preserved.
        if (recreateActivity) {
            Logger.logDebug(LOG_TAG, "Recreating activity");
            requireActivity().recreate();
        }
    }



    public static void startTermuxActivity(@NonNull final Context context) {
        ActivityUtils.startActivity(context, newInstance(context));
    }

    public static Intent newInstance(@NonNull final Context context) {
        Intent intent = new Intent(context, com.itsaky.androidide.activities.TerminalActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

}