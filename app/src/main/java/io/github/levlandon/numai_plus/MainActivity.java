package io.github.levlandon.numai_plus;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ListPopupWindow;
import android.support.v7.widget.Toolbar;
import android.widget.Scroller;
import android.content.res.Resources;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONException;
import cc.nnproject.json.JSONObject;

/**
 * This program is free software. It comes without any warranty, to
 * the extent permitted by applicable law. You can redistribute it
 * and/or modify it under the terms of the Do What The Fuck You Want
 * To Public License, Version 2, as published by Sam Hocevar. See
 * http://www.wtfpl.net/ for more details.
 */
public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PICK_IMAGE = 1;
    private static final int REQUEST_CODE_SETTINGS = 2;
    private static final String TAG = "NumAiTts";

    private ApiService apiService;
    private ConfigManager config;
    private ChatRepository chatRepository;

    private ViewPager mainPager;
    private View chatsPage;
    private View chatPage;
    private ListView msgList;
    private ListView chatsList;
    private EditText chatSearch;
    private EditText input;
    private MessageAdapter adapter;
    private ImageButton sendBtn;
    private Spinner modelSelector;
    private TextView emptyState;
    private View configBanner;
    private View configBannerAction;
    private TextView configBannerText;
    private ImageButton attachBtn;
    private View attachmentPreviewStrip;
    private LinearLayout attachmentPreviewContainer;
    private boolean autoScroll = true;
    private boolean isGenerating = false;
    private boolean isThinkingState = false;
    private boolean thinkingEnabled = false;
    private InputStream currentStream;
    private volatile boolean isCancelled = false;
    private boolean modelsFetched = false;
    private boolean suppressModelSelection = false;
    private ModelSelectorAdapter modelSelectorAdapter;
    private final ArrayList<String> allChatModels = new ArrayList<String>();
    private long activeChatId = -1L;
    int UPDATE_DELAY_MS = 250;

    // Stream buffers
    private final StringBuilder thinkBuffer = new StringBuilder();
    private final StringBuilder contentBuffer = new StringBuilder();
    private final List<String> inputImages = new ArrayList<String>();
    private final List<String> pendingAttachmentNames = new ArrayList<String>();
    private final List<ComposeAction> composeActions = new ArrayList<ComposeAction>();
    private final ArrayList<ChatRecord> chatListItems = new ArrayList<ChatRecord>();
    private final Handler uiHandler = new Handler();
    private Message activeAssistantMessage;
    private Message lastErrorMessage;
    private long activeReasoningStartTime;
    private Runnable reasoningTicker;
    private Runnable responseLoaderTicker;
    private boolean removeLastUserOnStop;
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;
    private boolean ttsInitializing = false;
    private Message speakingMessage;
    private String activeTtsUtteranceId;
    private int nextTtsUtteranceId = 1;
    private Message editingMessage;
    private boolean lastRequestedThinkingEnabled;
    private boolean currentResponseUsedStreaming;
    private boolean currentResponseRetriedWithoutStreaming;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        LayoutInflater inflater = LayoutInflater.from(this);
        chatsPage = inflater.inflate(R.layout.page_chat_list, null);
        chatPage = inflater.inflate(R.layout.page_chat, null);
        mainPager = (ViewPager) findViewById(R.id.main_pager);
        mainPager.setAdapter(new MainPagerAdapter());
        mainPager.setOffscreenPageLimit(1);
        mainPager.setCurrentItem(1, false);

        Toolbar toolbar = (Toolbar) chatPage.findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        SSLDisabler.disableSSLCertificateChecking();

        config = ConfigManager.getInstance(this);
        chatRepository = ChatRepository.getInstance(this);
        UPDATE_DELAY_MS = config.getConfig().getUpdateDelay();

        apiService = new ApiService(this);
        msgList = (ListView) chatPage.findViewById(R.id.messages_list);
        chatsList = (ListView) chatsPage.findViewById(R.id.chats_list);
        chatSearch = (EditText) chatsPage.findViewById(R.id.chat_search);
        input = (EditText) chatPage.findViewById(R.id.message_input);
        sendBtn = (ImageButton) chatPage.findViewById(R.id.send_button);
        attachBtn = (ImageButton) chatPage.findViewById(R.id.attach_button);
        modelSelector = (Spinner) chatPage.findViewById(R.id.model_selector);
        emptyState = (TextView) chatPage.findViewById(R.id.empty_state);
        configBanner = chatPage.findViewById(R.id.config_banner);
        configBannerAction = chatPage.findViewById(R.id.config_banner_action);
        configBannerText = (TextView) chatPage.findViewById(R.id.config_banner_text);
        attachmentPreviewStrip = chatPage.findViewById(R.id.attachment_preview_strip);
        attachmentPreviewContainer = (LinearLayout) chatPage.findViewById(R.id.attachment_preview_container);
        restoreCachedChatModels();
        setupComposeActions();
        setupChatsPage();
        setupModelSelector();
        setupInputField();

        sendBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (isGenerating) {
                    stopGeneration();
                } else {
                    sendMessage();
                }
            }
        });

        attachBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                if (editingMessage != null) {
                    cancelEditMode();
                } else {
                    showComposeActionsMenu();
                }
            }
        });

        MessageManager.getInstance().clearMessages();
        adapter = new MessageAdapter(this, MessageManager.getInstance().getMessages(), new MessageAdapter.MessageActionListener() {
            public void onCopyMessage(Message message) {
                copyAssistantMessage(message);
            }

            public void onRegenerateMessage(Message message) {
                regenerateAssistantMessage(message);
            }

            public void onToggleSpeak(Message message) {
                toggleSpeakMessage(message);
            }

            public void onSelectText(Message message) {
                showSelectableTextDialog(message.getContent());
            }

            public void onShowUserMessageMenu(View anchor, Message message) {
                showUserMessageMenu(anchor, message);
            }

            public void onRetryError(Message message) {
                retryErrorMessage(message);
            }
        });
        msgList.setAdapter(adapter);
        initializeActiveChat();
        updateAttachmentPreview();
        refreshChatList();
        setupConfigBanner();
        updateComposerState();
        updateEmptyState();

        msgList.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(android.widget.AbsListView view, int scrollState) {
                //If the user touches the screen or flings the list
                if (scrollState == SCROLL_STATE_TOUCH_SCROLL || scrollState == SCROLL_STATE_FLING) {
                    autoScroll = false;
                }
            }
            public void onScroll(android.widget.AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        restoreCachedChatModels();
        sanitizeThinkingModelForCurrentProvider();
        applyVisibleModelsToSelector();
        refreshChatList();
        updateProfileButtonAvatar();
        updateConfigBanner();
        updateComposerState();
    }

    private void setupInputField() {
        input.setHorizontallyScrolling(false);
        input.setScroller(new Scroller(this));
        input.setVerticalScrollBarEnabled(true);
        input.setMovementMethod(new ScrollingMovementMethod());
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable editable) {
                updateComposerState();
            }
        });
    }

    private void updateComposerState() {
        boolean hasInput = input != null && input.getText() != null && input.getText().toString().trim().length() != 0;
        boolean hasImages = !inputImages.isEmpty();
        boolean hasValidConfig = hasValidChatConfig();
        boolean canSend = hasValidConfig && !isGenerating && (hasInput || hasImages);
        if (sendBtn != null) {
            sendBtn.setEnabled(canSend || isGenerating);
            sendBtn.setAlpha((canSend || isGenerating) ? 1f : 0.72f);
        }
        updateEditModeUi();
        updateConfigBanner();
    }

    private void updateEmptyState() {
        if (emptyState == null) return;
        emptyState.setVisibility(adapter != null && adapter.getCount() > 0 ? View.GONE : View.VISIBLE);
    }

    private void setupModelSelector() {
        applyVisibleModelsToSelector();
        modelSelector.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_UP && !modelsFetched) {
                    loadToolbarModels();
                    return true;
                }
                return false;
            }
        });
        modelSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                if (suppressModelSelection) {
                    suppressModelSelection = false;
                    return;
                }
                String selectedModel = (String) adapterView.getItemAtPosition(position);
                if (selectedModel != null && selectedModel.length() != 0) {
                    if (modelSelectorAdapter != null) {
                        modelSelectorAdapter.setSelectedModel(selectedModel);
                    }
                    if (!selectedModel.equals(config.getConfig().getChatModel())) {
                        config.updateChatModel(selectedModel);
                    }
                    syncActiveChatDefaults();
                }
            }

            public void onNothingSelected(AdapterView<?> adapterView) {}
        });
    }

    private void loadToolbarModels() {
        final Loading loading = new Loading(this);
        apiService.getModels(new ApiCallback<ArrayList<String>>() {
            @Override
            public void onSuccess(ArrayList<String> result) {
                config.setCachedModels(result);
                restoreCachedChatModels();
                sanitizeThinkingModelForCurrentProvider();
                applyVisibleModelsToSelector();
                modelsFetched = true;
                loading.dismiss();
                if (modelSelectorAdapter != null && modelSelectorAdapter.getCount() > 0) {
                    modelSelector.performClick();
                }
            }

            @Override
            public void onError(ApiError error) {
                loading.dismiss();
                Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void restoreCachedChatModels() {
        allChatModels.clear();
        allChatModels.addAll(config.getCachedModels());
        modelsFetched = !allChatModels.isEmpty();
    }

    private void sanitizeThinkingModelForCurrentProvider() {
        if (allChatModels.isEmpty()) {
            return;
        }
        String currentThinkingModel = config.getConfig().getThinkingModel();
        if (currentThinkingModel == null || currentThinkingModel.length() == 0 || !allChatModels.contains(currentThinkingModel)) {
            String fallbackThinkingModel = ModelSelector.selectThinkingModel(allChatModels);
            if (fallbackThinkingModel != null) {
                config.updateThinkingModel(fallbackThinkingModel);
            }
        }
    }

    private void setupComposeActions() {
        composeActions.clear();
        composeActions.add(new ComposeAction(R.id.action_attach_image, getString(R.string.select_picture), false));
        composeActions.add(new ComposeAction(R.id.action_toggle_thinking, getString(R.string.thinking_mode), thinkingEnabled));
    }

    private void setupConfigBanner() {
        if (configBannerAction != null) {
            configBannerAction.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    openSettingsScreen();
                }
            });
        }
        updateConfigBanner();
    }

    private void updateConfigBanner() {
        if (configBanner == null) {
            return;
        }
        boolean show = !hasValidChatConfig();
        configBanner.setVisibility(show ? View.VISIBLE : View.GONE);
        if (configBannerText != null) {
            configBannerText.setText(buildConfigBannerText());
        }
    }

    private boolean hasValidChatConfig() {
        Config conf = config.getConfig();
        return conf != null &&
                conf.getBaseUrl() != null && conf.getBaseUrl().trim().length() != 0 &&
                conf.getApiKey() != null && conf.getApiKey().trim().length() != 0 &&
                conf.getChatModel() != null && conf.getChatModel().trim().length() != 0;
    }

    private String buildConfigBannerText() {
        Config conf = config.getConfig();
        if (conf.getApiKey() == null || conf.getApiKey().trim().length() == 0) {
            return getString(R.string.config_missing_api_key);
        }
        if (conf.getBaseUrl() == null || conf.getBaseUrl().trim().length() == 0) {
            return getString(R.string.config_missing_provider);
        }
        if (conf.getChatModel() == null || conf.getChatModel().trim().length() == 0) {
            return getString(R.string.config_missing_model);
        }
        return getString(R.string.config_missing_provider);
    }

    private void setupChatsPage() {
        chatsList.setAdapter(new ChatListAdapter());
        chatsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ChatRecord record = chatListItems.get(position);
                openChat(record.getChatId(), true);
            }
        });
        chatsList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showChatContextMenu(view, chatListItems.get(position));
                return true;
            }
        });

        if (chatSearch != null) {
            chatSearch.setFocusable(true);
            chatSearch.setFocusableInTouchMode(true);
            chatSearch.setClickable(true);
            chatSearch.setCursorVisible(true);
            chatSearch.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                public void afterTextChanged(Editable editable) {
                    refreshChatList();
                }
            });
        }

        View profileButton = chatsPage.findViewById(R.id.profile_button);
        profileButton.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                openSettingsScreen();
            }
        });
        updateProfileButtonAvatar();

        View newChatButton = chatsPage.findViewById(R.id.new_chat_button);
        newChatButton.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                startNewDraftChat(true);
            }
        });
    }

    private void updateProfileButtonAvatar() {
        ImageButton profileButton = (ImageButton) chatsPage.findViewById(R.id.profile_button);
        if (profileButton == null) {
            return;
        }
        String avatarPath = config.getAvatarPath();
        if (avatarPath != null && avatarPath.length() != 0) {
            Bitmap bitmap = BitmapFactory.decodeFile(avatarPath);
            if (bitmap != null) {
                Bitmap circularBitmap = AvatarBitmapHelper.createCircularBitmap(bitmap);
                profileButton.setPadding(0, 0, 0, 0);
                profileButton.setScaleType(ImageView.ScaleType.CENTER_CROP);
                profileButton.setImageBitmap(circularBitmap != null ? circularBitmap : bitmap);
                return;
            }
        }
        int padding = dpToPx(10);
        profileButton.setPadding(padding, padding, padding, padding);
        profileButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        profileButton.setImageResource(R.drawable.ic_profile);
    }

    private void openSettingsScreen() {
        startActivityForResult(new Intent(MainActivity.this, SettingsActivity.class), REQUEST_CODE_SETTINGS);
    }

    private void initializeActiveChat() {
        long storedActiveChatId = config.getActiveChatId();
        if (storedActiveChatId > 0L) {
            ChatRecord storedChat = chatRepository.getChat(storedActiveChatId);
            if (storedChat != null) {
                openChat(storedActiveChatId, false);
                return;
            }
            clearCurrentChat();
            return;
        }
        clearCurrentChat();
    }

    private void clearCurrentChat() {
        activeChatId = -1L;
        MessageManager.getInstance().setCurrentChatId(-1L);
        MessageManager.getInstance().clearMessages();
        editingMessage = null;
        if (input != null) {
            input.setText("");
        }
        inputImages.clear();
        pendingAttachmentNames.clear();
        updateAttachmentPreview();
        updateComposerState();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateEmptyState();
        config.setActiveChatId(-1L);
    }

    private void refreshChatList() {
        String query = chatSearch != null && chatSearch.getText() != null ? chatSearch.getText().toString() : null;
        chatListItems.clear();
        chatListItems.addAll(chatRepository.getChats(query));
        ListAdapter adapter = chatsList.getAdapter();
        if (adapter instanceof BaseAdapter) {
            ((BaseAdapter) adapter).notifyDataSetChanged();
        }
    }

    private void startNewDraftChat(boolean openPager) {
        stopSpeakingMessage(null);
        if (editingMessage != null) {
            cancelEditMode();
        } else {
            input.setText("");
            inputImages.clear();
            pendingAttachmentNames.clear();
            updateAttachmentPreview();
            updateComposerState();
        }
        clearCurrentChat();
        if (openPager) {
            mainPager.setCurrentItem(1, true);
        }
    }

    private void openChat(long chatId, boolean openPager) {
        ChatRecord chat = chatRepository.getChat(chatId);
        if (chat == null) {
            clearCurrentChat();
            refreshChatList();
            return;
        }
        activeChatId = chatId;
        config.setActiveChatId(chatId);
        MessageManager.getInstance().setCurrentChatId(chatId);
        MessageManager.getInstance().setMessages(chatRepository.getMessages(chatId));
        applyChatDefaults(chat);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateEmptyState();
        if (openPager) {
            mainPager.setCurrentItem(1, true);
        }
        msgList.post(new Runnable() {
            public void run() {
                if (adapter != null && adapter.getCount() > 0) {
                    msgList.setSelection(adapter.getCount() - 1);
                }
            }
        });
    }

    private void applyChatDefaults(ChatRecord chat) {
        if (chat == null) {
            return;
        }
        String modelName = chat.getModelName();
        if (modelName != null && modelName.length() != 0) {
            config.updateChatModel(modelName);
        }
        thinkingEnabled = chat.isReasoningEnabledDefault();
        applyVisibleModelsToSelector();
    }

    private void syncActiveChatDefaults() {
        if (activeChatId > 0L) {
            chatRepository.updateChatDefaults(activeChatId, config.getConfig().getChatModel(), thinkingEnabled);
            refreshChatList();
        }
    }

    private void showChatContextMenu(View anchor, final ChatRecord record) {
        final ArrayList<String> actions = new ArrayList<String>();
        actions.add(getString(R.string.rename_chat_action));
        actions.add(getString(R.string.delete_chat_action));

        final ListPopupWindow popupWindow = new ListPopupWindow(this);
        popupWindow.setAnchorView(anchor);
        popupWindow.setAdapter(new ArrayAdapter<String>(this, R.layout.popup_action_item, actions));
        popupWindow.setModal(true);
        popupWindow.setBackgroundDrawable(getResources().getDrawable(R.drawable.bg_spinner_dropdown));
        popupWindow.setContentWidth(dpToPx(220));
        popupWindow.setDropDownGravity(Gravity.RIGHT);
        popupWindow.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                popupWindow.dismiss();
                if (position == 0) {
                    promptRenameChat(record);
                } else if (position == 1) {
                    confirmDeleteChat(record);
                }
            }
        });
        popupWindow.show();
        ListView listView = popupWindow.getListView();
        if (listView != null) {
            listView.setDivider(null);
            listView.setDividerHeight(0);
        }
    }

    private void promptRenameChat(final ChatRecord record) {
        final EditText editText = new EditText(this);
        editText.setText(record.getTitle() != null ? record.getTitle() : "");
        editText.setSelection(editText.getText().length());
        new AlertDialog.Builder(this)
                .setTitle(R.string.rename_chat_action)
                .setView(editText)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String title = editText.getText().toString().trim();
                        if (title.length() == 0) {
                            return;
                        }
                        chatRepository.renameChat(record.getChatId(), title);
                        refreshChatList();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDeleteChat(final ChatRecord record) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_chat_action)
                .setMessage(R.string.delete_chat_confirm)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        deleteChatRecord(record);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteChatRecord(ChatRecord record) {
        long chatId = record.getChatId();
        boolean deletingActive = chatId == activeChatId;
        chatRepository.deleteChat(chatId);
        if (deletingActive) {
            clearCurrentChat();
            mainPager.setCurrentItem(1, true);
            refreshChatList();
        } else {
            refreshChatList();
        }
    }

    private void showComposeActionsMenu() {
        composeActions.get(1).checked = thinkingEnabled;
        final ComposeActionsAdapter popupAdapter = new ComposeActionsAdapter();
        final ListPopupWindow popupWindow = new ListPopupWindow(this);
        popupWindow.setAnchorView(attachBtn);
        popupWindow.setAdapter(popupAdapter);
        popupWindow.setModal(true);
        popupWindow.setBackgroundDrawable(getResources().getDrawable(R.drawable.bg_spinner_dropdown));
        popupWindow.setContentWidth(dpToPx(220));
        popupWindow.setDropDownGravity(Gravity.RIGHT);
        popupWindow.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ComposeAction action = composeActions.get(position);
                popupWindow.dismiss();
                if (action.id == R.id.action_attach_image) {
                    openImagePicker();
                } else if (action.id == R.id.action_toggle_thinking) {
                    thinkingEnabled = !thinkingEnabled;
                    action.checked = thinkingEnabled;
                    syncActiveChatDefaults();
                }
            }
        });
        popupWindow.show();
        ListView listView = popupWindow.getListView();
        if (listView != null) {
            listView.setDivider(null);
            listView.setDividerHeight(0);
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_picture)), REQUEST_CODE_PICK_IMAGE);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private void applyVisibleModelsToSelector() {
        ArrayList<String> visibleModels;
        if (allChatModels.isEmpty()) {
            visibleModels = new ArrayList<String>();
            String currentModel = config.getConfig().getChatModel();
            if (currentModel != null && currentModel.length() != 0) {
                visibleModels.add(currentModel);
            }
        } else {
            visibleModels = config.getVisibleChatModels(allChatModels);
        }

        if (visibleModels.isEmpty()) {
            String currentModel = config.getConfig().getChatModel();
            if (currentModel != null && currentModel.length() != 0) {
                visibleModels.add(currentModel);
            }
        }

        String currentModel = config.getConfig().getChatModel();
        if (!visibleModels.isEmpty() && !visibleModels.contains(currentModel)) {
            currentModel = visibleModels.get(0);
            config.updateChatModel(currentModel);
        }

        modelSelectorAdapter = new ModelSelectorAdapter(this, visibleModels);
        modelSelectorAdapter.setSelectedModel(currentModel);
        suppressModelSelection = true;
        modelSelector.setAdapter(modelSelectorAdapter);
        if (!visibleModels.isEmpty()) {
            int selectedIndex = visibleModels.indexOf(currentModel);
            modelSelector.setSelection(selectedIndex >= 0 ? selectedIndex : 0);
        }
    }

    

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                openSettingsScreen();
                return true;
            case R.id.about:
                Toast.makeText(this, getString(R.string.app_version_toast, BuildConfig.VERSION_NAME, BuildConfig.BUILD_TYPE), Toast.LENGTH_SHORT).show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            processSelectedImage(data.getData());
        } else if (requestCode == REQUEST_CODE_SETTINGS && resultCode == RESULT_OK && data != null && data.getBooleanExtra("language_changed", false)) {
            LocaleHelper.applyAppLocale(this);
            recreate();
        }
    }

    private void processSelectedImage(Uri uri) {
        try {
            Bitmap bitmap = decodeSampledBitmap(this, uri, 1080, 1080);
            if (bitmap != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                byte[] bytes = baos.toByteArray();
                bitmap.recycle();

                inputImages.add("data:image/jpeg;base64," + Base64.encode(bytes));
                pendingAttachmentNames.add(resolveAttachmentName(uri, pendingAttachmentNames.size() + 1));
                updateAttachmentPreview();
                updateComposerState();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.image_load_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateAttachmentPreview() {
        if (attachmentPreviewStrip == null || attachmentPreviewContainer == null) return;
        attachmentPreviewContainer.removeAllViews();
        if (pendingAttachmentNames.isEmpty()) {
            attachmentPreviewStrip.setVisibility(View.GONE);
            return;
        }

        attachmentPreviewStrip.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < pendingAttachmentNames.size(); i++) {
            final int index = i;
            View chip = inflater.inflate(R.layout.attachment_preview_chip, attachmentPreviewContainer, false);
            TextView attachmentName = (TextView) chip.findViewById(R.id.attachment_name);
            View removeButton = chip.findViewById(R.id.attachment_remove);
            attachmentName.setText(pendingAttachmentNames.get(i));
            removeButton.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    removePendingAttachment(index);
                }
            });
            attachmentPreviewContainer.addView(chip);
        }
        updateEditModeUi();
    }

    private void removePendingAttachment(int index) {
        if (index < 0 || index >= inputImages.size()) return;
        inputImages.remove(index);
        if (index < pendingAttachmentNames.size()) {
            pendingAttachmentNames.remove(index);
        }
        updateAttachmentPreview();
        updateComposerState();
    }

    private String resolveAttachmentName(Uri uri, int fallbackIndex) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameColumn != -1) {
                    String displayName = cursor.getString(nameColumn);
                    if (displayName != null && displayName.length() != 0) {
                        return displayName;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        String lastSegment = uri != null ? uri.getLastPathSegment() : null;
        if (lastSegment != null && lastSegment.length() != 0) {
            return lastSegment;
        }
        return getString(R.string.attachment_name_fallback, fallbackIndex);
    }

    private void scrollToBottom() {
        if (!autoScroll) return;
        msgList.post(new Runnable() {
            public void run() {
                msgList.post(new Runnable() {
                    public void run() {
                        adapter.notifyDataSetChanged();
                        msgList.setSelection(adapter.getCount() - 1);
                    }
                });
            }
        });
    }

    private void stopGeneration() {
        isCancelled = true;
        stopReasoningTicker();
        cancelResponseLoaderTicker();

//        if (currentStream != null) {
//            try {
//                currentStream.close();
//            } catch (IOException e) {e.printStackTrace();}
//        }

        List<Message> msgs = MessageManager.getInstance().getMessages();
        if (activeAssistantMessage != null) {
            adapter.setResponseLoaderVisible(activeAssistantMessage, false);
            adapter.setResponseComplete(activeAssistantMessage, true);
            String partialContent = activeAssistantMessage.getContent();
            boolean hasPartialText = partialContent != null && partialContent.trim().length() != 0;
            boolean hasReasoning = thinkBuffer.length() != 0;
            if (!hasPartialText && !hasReasoning) {
                msgs.remove(activeAssistantMessage);
                chatRepository.deleteMessage(activeAssistantMessage.getMessageId());
            } else {
                chatRepository.updateAssistantMessage(activeAssistantMessage);
            }
        }
        resetUIState();

        // Force adapter update
        runOnUiThread(new Runnable() {
            public void run() {
                adapter.notifyDataSetChanged();
                updateEmptyState();
                updateComposerState();
            }
        });
    }

    private void sendMessage() {
        String text = input.getText().toString().trim();
        if (text.length() == 0 && inputImages.isEmpty()) return;
        if (!isChatModelReadyForSend()) {
            Toast.makeText(this, R.string.model_not_available, Toast.LENGTH_SHORT).show();
            updateComposerState();
            return;
        }
        if (thinkingEnabled && !isThinkingModelReadyForSend()) {
            Toast.makeText(this, R.string.model_not_available, Toast.LENGTH_SHORT).show();
            updateComposerState();
            return;
        }
        if (!inputImages.isEmpty() && !supportsAttachmentsForCurrentModel()) {
            Toast.makeText(this, R.string.attachments_not_supported, Toast.LENGTH_LONG).show();
            return;
        }
        autoScroll = true;
        isCancelled = false;
        currentStream = null;
        ArrayList<String> imagesToSend = new ArrayList<String>(inputImages);
        ArrayList<String> attachmentNames = new ArrayList<String>(pendingAttachmentNames);
        if (editingMessage != null) {
            trimMessagesForEdit(editingMessage);
        }
        ensureActiveChatForSend();
        Message userMessage = chatRepository.addUserMessage(activeChatId, text, imagesToSend, attachmentNames, config.getConfig().getChatModel(), thinkingEnabled);
        MessageManager.getInstance().addMessage(userMessage);
        input.setText("");
        sendBtn.setImageResource(R.drawable.ic_stop_generation);
        input.setEnabled(false);
        attachBtn.setEnabled(false);
        modelSelector.setEnabled(false);
        inputImages.clear();
        pendingAttachmentNames.clear();
        editingMessage = null;
        updateAttachmentPreview();
        adapter.notifyDataSetChanged();
        updateEmptyState();
        refreshChatList();
        requestAssistantResponse(this.thinkingEnabled, true);
    }

    private boolean supportsAttachmentsForCurrentModel() {
        Config currentConfig = config.getConfig();
        if (currentConfig == null) {
            return false;
        }
        String modelName = currentConfig.getChatModel();
        if (modelName == null) {
            return false;
        }
        String normalized = modelName.toLowerCase(Locale.US);
        return normalized.indexOf("vision") != -1
                || normalized.indexOf("vl") != -1
                || normalized.indexOf("gemini") != -1
                || normalized.indexOf("gpt-4o") != -1
                || normalized.indexOf("gpt-4.1") != -1
                || normalized.indexOf("gpt-5") != -1
                || normalized.indexOf("claude-3") != -1
                || normalized.indexOf("claude-4") != -1
                || normalized.indexOf("pixtral") != -1
                || normalized.indexOf("llava") != -1;
    }

    private boolean isChatModelReadyForSend() {
        String chatModel = config.getConfig().getChatModel();
        return chatModel != null && chatModel.length() != 0 && (allChatModels.isEmpty() || allChatModels.contains(chatModel));
    }

    private boolean isThinkingModelReadyForSend() {
        String thinkingModel = config.getConfig().getThinkingModel();
        return thinkingModel != null && thinkingModel.length() != 0 && (allChatModels.isEmpty() || allChatModels.contains(thinkingModel));
    }

    private void ensureActiveChatForSend() {
        if (activeChatId > 0L) {
            return;
        }
        ChatRecord chat = chatRepository.createChat(config.getConfig().getChatModel(), thinkingEnabled);
        activeChatId = chat.getChatId();
        config.setActiveChatId(activeChatId);
        MessageManager.getInstance().setCurrentChatId(activeChatId);
        refreshChatList();
    }

    private void startResponseStream(final InputStream stream, String model, final boolean thinkingEnabled, Message message) {
        this.currentStream = stream;
        final Message msg = message != null ? message : new Message(Role.ASSISTANT, "", model);
        msg.setLlm(model);
        msg.setReasoningUsed(thinkingEnabled);
        msg.setContentFinal("");
        chatRepository.updateAssistantMessage(msg);
        if (message == null) {
            activeAssistantMessage = msg;
            activeReasoningStartTime = thinkingEnabled ? System.currentTimeMillis() : 0L;
            MessageManager.getInstance().addMessage(msg);
            adapter.prepareAssistantMessage(msg, thinkingEnabled);
            if (thinkingEnabled) {
                startReasoningTicker(msg);
            } else {
                scheduleResponseLoaderTicker(msg);
            }
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
        new Thread(new Runnable() {
            public void run() {
                try {
                    readStream(stream, msg, thinkingEnabled);
                } catch (Exception e) {
                    if (isGenerating && !isCancelled) {
                        final String err = e.getMessage();
                        runOnUiThread(new Runnable() { public void run() { handleStreamError(err); } });
                    }
                }
            }
        }).start();
    }

    private void handleStreamError(String errorMsg) {
        if (maybeRetryWithoutStreaming(errorMsg)) {
            return;
        }
        List<Message> messages = MessageManager.getInstance().getMessages();
        String failedModel = activeAssistantMessage != null && activeAssistantMessage.getLlm() != null
                ? activeAssistantMessage.getLlm()
                : config.getConfig().getChatModel();
        if (activeAssistantMessage != null) {
            messages.remove(activeAssistantMessage);
            chatRepository.deleteMessage(activeAssistantMessage.getMessageId());
        }
        ErrorDisplayData errorData = classifyError(errorMsg, failedModel);
        Message error = chatRepository.addAssistantMessage(activeChatId, errorData.summary, failedModel, false);
        error.setAsError();
        error.setErrorTitle(errorData.title);
        error.setErrorDetails(errorData.details);
        MessageManager.getInstance().addMessage(error);
        lastErrorMessage = error;
        updateEmptyState();
        refreshChatList();
        resetUIState();
    }

    private void resetUIState() {
        isGenerating = false;
        removeLastUserOnStop = false;
        stopReasoningTicker();
        cancelResponseLoaderTicker();
        activeAssistantMessage = null;
        activeReasoningStartTime = 0L;
        currentResponseUsedStreaming = false;
        currentResponseRetriedWithoutStreaming = false;
        adapter.notifyDataSetChanged();
        autoScroll = true;
        scrollToBottom();
        sendBtn.setImageResource(R.drawable.ic_arrow_upward);
        input.setEnabled(true);
        attachBtn.setEnabled(true);
        modelSelector.setEnabled(true);
        updateComposerState();
        updateEmptyState();
    }

    private void requestAssistantResponse(final boolean thinkingEnabled, boolean rollbackUserOnStop) {
        requestAssistantResponse(thinkingEnabled, rollbackUserOnStop, null);
    }

    private void requestAssistantResponse(final boolean thinkingEnabled, boolean rollbackUserOnStop, Boolean streamOverride) {
        if (!hasValidChatConfig()) {
            updateComposerState();
            return;
        }
        if (!isChatModelReadyForSend()) {
            Toast.makeText(this, R.string.model_not_available, Toast.LENGTH_SHORT).show();
            updateComposerState();
            return;
        }
        if (thinkingEnabled && !isThinkingModelReadyForSend()) {
            Toast.makeText(this, R.string.model_not_available, Toast.LENGTH_SHORT).show();
            resetUIState();
            return;
        }
        lastRequestedThinkingEnabled = thinkingEnabled;
        removeLastUserOnStop = rollbackUserOnStop;
        isGenerating = true;
        sendBtn.setImageResource(R.drawable.ic_stop_generation);
        sendBtn.setEnabled(true);
        input.setEnabled(false);
        attachBtn.setEnabled(false);
        modelSelector.setEnabled(false);
        updateComposerState();

        final Message pendingAssistant = chatRepository.addAssistantPlaceholder(activeChatId, config.getConfig().getChatModel(), thinkingEnabled);
        activeAssistantMessage = pendingAssistant;
        activeReasoningStartTime = thinkingEnabled ? System.currentTimeMillis() : 0L;
        MessageManager.getInstance().addMessage(pendingAssistant);
        adapter.prepareAssistantMessage(pendingAssistant, thinkingEnabled);
        if (thinkingEnabled) {
            startReasoningTicker(pendingAssistant);
        } else {
            scheduleResponseLoaderTicker(pendingAssistant);
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
        scrollToBottom();

        thinkBuffer.setLength(0);
        contentBuffer.setLength(0);
        isThinkingState = false;
        currentResponseUsedStreaming = false;
        currentResponseRetriedWithoutStreaming = Boolean.FALSE.equals(streamOverride);
        apiService.chatCompletion(MessageManager.getInstance().getMessages(), thinkingEnabled, streamOverride, new ApiCallback<ApiResult>() {
            @Override
            public void onSuccess(final ApiResult apiResult) {
                if (isCancelled)return;
                runOnUiThread(new Runnable() {
                    public void run() {
                        currentResponseUsedStreaming = apiResult.isStreaming();
                        startResponseStream(apiResult.getResult(), apiResult.getModel(), thinkingEnabled, pendingAssistant);
                    }
                });
            }
            @Override
            public void onError(final ApiError error) {
                if (isCancelled)return;
                runOnUiThread(new Runnable() {
                    public void run() {
                        handleStreamError(error.getMessage());
                    }
                });
            }
        });
    }

    private void copyAssistantMessage(Message message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setText(message.getContent());
        Toast.makeText(this, R.string.text_copied, Toast.LENGTH_SHORT).show();
    }

    private void retryErrorMessage(Message message) {
        if (isGenerating || message == null) {
            return;
        }
        List<Message> messages = MessageManager.getInstance().getMessages();
        messages.remove(message);
        if (message.getMessageId() > 0L) {
            chatRepository.deleteMessage(message.getMessageId());
        }
        if (lastErrorMessage == message) {
            lastErrorMessage = null;
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
        refreshChatList();
        requestAssistantResponse(lastRequestedThinkingEnabled, false);
    }

    private void showUserMessageMenu(View anchor, final Message message) {
        final ArrayList<String> actions = new ArrayList<String>();
        actions.add(getString(R.string.copy_action));
        actions.add(getString(R.string.select_text_action));
        actions.add(getString(R.string.edit_message_action));

        final ListPopupWindow popupWindow = new ListPopupWindow(this);
        popupWindow.setAnchorView(anchor);
        popupWindow.setAdapter(new ArrayAdapter<String>(this, R.layout.popup_action_item, actions));
        popupWindow.setModal(true);
        popupWindow.setBackgroundDrawable(getResources().getDrawable(R.drawable.bg_spinner_dropdown));
        popupWindow.setContentWidth(dpToPx(220));
        popupWindow.setDropDownGravity(Gravity.RIGHT);
        popupWindow.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                popupWindow.dismiss();
                if (position == 0) {
                    copyAssistantMessage(message);
                } else if (position == 1) {
                    showSelectableTextDialog(message.getContent());
                } else if (position == 2) {
                    startEditMode(message);
                }
            }
        });
        popupWindow.show();
        ListView listView = popupWindow.getListView();
        if (listView != null) {
            listView.setDivider(null);
            listView.setDividerHeight(0);
        }
    }

    private void showSelectableTextDialog(String text) {
        final android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_text_preview);
        TextView content = (TextView) dialog.findViewById(R.id.text_preview_content);
        View back = dialog.findViewById(R.id.text_preview_back);
        content.setText(text != null ? text : "");
        back.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void startEditMode(Message message) {
        if (message == null || !message.isSent() || isGenerating) {
            return;
        }
        editingMessage = message;
        input.setText(message.getContent() != null ? message.getContent() : "");
        input.setSelection(input.getText().length());
        inputImages.clear();
        pendingAttachmentNames.clear();
        List<String> images = message.getInputImages();
        List<ChatAttachment> attachments = message.getAttachments();
        if (images != null) {
            for (int i = 0; i < images.size(); i++) {
                inputImages.add(images.get(i));
                if (attachments != null && i < attachments.size() && attachments.get(i) != null && attachments.get(i).getName() != null) {
                    pendingAttachmentNames.add(attachments.get(i).getName());
                } else {
                    pendingAttachmentNames.add(getString(R.string.attachment_name_fallback, Integer.valueOf(i + 1)));
                }
            }
        }
        updateAttachmentPreview();
        updateComposerState();
        input.requestFocus();
    }

    private void cancelEditMode() {
        editingMessage = null;
        input.setText("");
        inputImages.clear();
        pendingAttachmentNames.clear();
        updateAttachmentPreview();
        updateComposerState();
    }

    private void trimMessagesForEdit(Message targetMessage) {
        List<Message> messages = MessageManager.getInstance().getMessages();
        int targetIndex = messages.indexOf(targetMessage);
        if (targetIndex == -1) {
            return;
        }
        stopSpeakingMessage(null);
        chatRepository.deleteMessagesFrom(activeChatId, targetMessage.getMessageId());
        while (messages.size() > targetIndex) {
            messages.remove(targetIndex);
        }
        refreshChatList();
    }

    private void updateEditModeUi() {
        if (attachBtn == null) {
            return;
        }
        if (editingMessage != null) {
            attachBtn.setImageResource(R.drawable.ic_close);
            attachBtn.setContentDescription(getString(R.string.cancel_edit_action));
        } else {
            attachBtn.setImageResource(R.drawable.ic_add);
            attachBtn.setContentDescription(getString(R.string.select_picture));
        }
    }

    private void regenerateAssistantMessage(Message message) {
        if (isGenerating) {
            return;
        }
        List<Message> messages = MessageManager.getInstance().getMessages();
        int lastAssistantIndex = findLatestAssistantIndex(messages);
        int messageIndex = messages.lastIndexOf(message);
        if (messageIndex == -1 || messageIndex != lastAssistantIndex) {
            return;
        }
        stopSpeakingMessage(message);
        messages.remove(messageIndex);
        chatRepository.deleteMessage(message.getMessageId());
        adapter.notifyDataSetChanged();
        updateEmptyState();
        refreshChatList();
        requestAssistantResponse(thinkingEnabled, false);
    }

    private int findLatestAssistantIndex(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message != null && !message.isSent()) {
                return i;
            }
        }
        return -1;
    }

    private void toggleSpeakMessage(Message message) {
        if (message == speakingMessage) {
            stopSpeakingMessage(message);
            return;
        }
        if (!ensureTextToSpeechReady(message)) {
            return;
        }
        startSpeakingMessage(message);
    }

    private boolean ensureTextToSpeechReady(Message pendingMessage) {
        if (ttsReady && textToSpeech != null) {
            return true;
        }
        if (ttsInitializing) {
            return false;
        }
        resetTextToSpeechEngine();
        ttsInitializing = true;
        final Message messageToSpeak = pendingMessage;
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            public void onInit(int status) {
                ttsInitializing = false;
                if (status != TextToSpeech.SUCCESS || textToSpeech == null) {
                    ttsReady = false;
                    resetTextToSpeechEngine();
                    Toast.makeText(MainActivity.this, R.string.tts_not_available, Toast.LENGTH_SHORT).show();
                    return;
                }
                attachTextToSpeechListener();
                ttsReady = true;
                startSpeakingMessage(messageToSpeak);
            }
        });
        return false;
    }

    private void startSpeakingMessage(Message message) {
        if (message == null) {
            return;
        }
        if (textToSpeech == null || !ttsReady) {
            return;
        }
        if (message.isSent()) {
            Log.d(TAG, "Blocked TTS: message is not an assistant response");
            return;
        }
        if (message == activeAssistantMessage && isGenerating) {
            Log.d(TAG, "Blocked TTS: assistant message is still streaming messageId=" + message.getMessageId());
            return;
        }
        String finalContent = message.getContentFinal();
        String displayContent = message.getContent();
        String speakableText = TtsTextSanitizer.sanitizeForPlayback(finalContent, displayContent);
        if (speakableText.length() == 0) {
            Log.d(TAG, "Blocked TTS: sanitized assistant text is empty messageId=" + message.getMessageId()
                    + " raw=" + quoteForLog(finalContent)
                    + " display=" + quoteForLog(displayContent));
            return;
        }
        if (!applyBestAvailableTtsLocale(speakableText)) {
            Log.d(TAG, "Blocked TTS: no supported locale for messageId=" + message.getMessageId());
            resetTextToSpeechEngine();
            Toast.makeText(this, R.string.tts_not_available, Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Preparing TTS from message.contentFinal messageId=" + message.getMessageId()
                + " text=" + quoteForLog(finalContent));
        Log.d(TAG, "Calling tts.speak() messageId=" + message.getMessageId()
                + " text=" + quoteForLog(speakableText));
        if (speakingMessage != null && speakingMessage != message) {
            textToSpeech.stop();
        }
        speakingMessage = message;
        adapter.setSpeakingMessage(message);
        HashMap params = new HashMap();
        String utteranceId = "numai_tts_" + nextTtsUtteranceId++;
        activeTtsUtteranceId = utteranceId;
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        int speakStatus = textToSpeech.speak(speakableText, TextToSpeech.QUEUE_FLUSH, params);
        if (speakStatus == TextToSpeech.ERROR) {
            Log.d(TAG, "tts.speak() failed messageId=" + message.getMessageId());
            activeTtsUtteranceId = null;
            speakingMessage = null;
            adapter.setSpeakingMessage(null);
            resetTextToSpeechEngine();
        }
    }

    private void attachTextToSpeechListener() {
        if (textToSpeech == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 15) {
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                }

                @Override
                public void onDone(String utteranceId) {
                    handleTtsPlaybackFinished(utteranceId);
                }

                @Override
                public void onError(String utteranceId) {
                    handleTtsPlaybackFinished(utteranceId);
                }
            });
            return;
        }
        textToSpeech.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {
            public void onUtteranceCompleted(String utteranceId) {
                handleTtsPlaybackFinished(utteranceId);
            }
        });
    }

    private void handleTtsPlaybackFinished(final String utteranceId) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (utteranceId != null && activeTtsUtteranceId != null && !utteranceId.equals(activeTtsUtteranceId)) {
                    return;
                }
                activeTtsUtteranceId = null;
                speakingMessage = null;
                adapter.setSpeakingMessage(null);
            }
        });
    }

    private boolean applyBestAvailableTtsLocale(String sampleText) {
        if (textToSpeech == null) {
            return false;
        }
        ArrayList<Locale> candidates = new ArrayList<Locale>();
        addTtsLocaleCandidate(candidates, detectTtsLocale(sampleText));
        addTtsLocaleCandidate(candidates, resolveConfiguredAppLocale());
        addTtsLocaleCandidate(candidates, Locale.getDefault());
        addTtsLocaleCandidate(candidates, Resources.getSystem().getConfiguration().locale);
        addTtsLocaleCandidate(candidates, Locale.ENGLISH);
        addTtsLocaleCandidate(candidates, Locale.US);
        for (int i = 0; i < candidates.size(); i++) {
            Locale locale = candidates.get(i);
            int availability = textToSpeech.setLanguage(locale);
            if (availability != TextToSpeech.LANG_MISSING_DATA && availability != TextToSpeech.LANG_NOT_SUPPORTED) {
                return true;
            }
        }
        return false;
    }

    private void addTtsLocaleCandidate(ArrayList<Locale> candidates, Locale locale) {
        if (locale == null) {
            return;
        }
        for (int i = 0; i < candidates.size(); i++) {
            Locale existing = candidates.get(i);
            if (existing != null && existing.toString().equals(locale.toString())) {
                return;
            }
        }
        candidates.add(locale);
    }

    private Locale resolveConfiguredAppLocale() {
        String configuredLanguage = config != null ? config.getAppLanguage() : "";
        if (configuredLanguage == null || configuredLanguage.trim().length() == 0) {
            return null;
        }
        return new Locale(configuredLanguage);
    }

    private Locale detectTtsLocale(String text) {
        if (text == null || text.length() == 0) {
            return null;
        }
        boolean hasCyrillic = false;
        boolean hasLatin = false;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
            if (block == Character.UnicodeBlock.CYRILLIC || block == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY) {
                hasCyrillic = true;
            } else if ((codePoint >= 'A' && codePoint <= 'Z') || (codePoint >= 'a' && codePoint <= 'z')) {
                hasLatin = true;
            }
        }
        if (hasCyrillic) {
            return new Locale("ru");
        }
        if (hasLatin) {
            return Locale.ENGLISH;
        }
        return null;
    }

    private void resetTextToSpeechEngine() {
        if (textToSpeech != null) {
            try {
                textToSpeech.stop();
            } catch (Exception ignored) {
            }
            try {
                textToSpeech.shutdown();
            } catch (Exception ignored) {
            }
        }
        textToSpeech = null;
        ttsReady = false;
        ttsInitializing = false;
        activeTtsUtteranceId = null;
    }

    private String buildSpeakableTextForTts(String source) {
        String plain = extractPlainAssistantTextForTts(source);
        plain = stripThinkBlocksForTts(plain);
        plain = dropLeadingMetadataLinesForTts(plain);
        plain = stripLeadingAssistantLabelForTts(plain);
        plain = stripEmojiForTts(plain);
        plain = plain.replaceAll("[ \\t\\x0B\\f]+", " ").trim();
        if (plain.length() != 0) {
            return plain;
        }
        return stripEmojiForTts(source != null ? source : "").trim();
    }

    private String stripThinkBlocksForTts(String source) {
        if (source == null || source.length() == 0) {
            return "";
        }
        return source
                .replaceAll("(?is)<think>.*?</think>", " ")
                .replace("<think>", " ")
                .replace("</think>", " ");
    }

    private String dropLeadingMetadataLinesForTts(String source) {
        if (source == null || source.length() == 0) {
            return "";
        }
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int start = 0;
        while (start < lines.length) {
            String trimmed = lines[start] != null ? lines[start].trim() : "";
            if (trimmed.length() == 0 && start < lines.length - 1) {
                start++;
                continue;
            }
            if (!isLeadingMetadataLineForTts(trimmed) || start >= lines.length - 1) {
                break;
            }
            start++;
        }
        StringBuffer out = new StringBuffer(source.length());
        for (int i = start; i < lines.length; i++) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(lines[i]);
        }
        return out.toString().trim();
    }

    private boolean isLeadingMetadataLineForTts(String line) {
        if (line == null || line.length() == 0) {
            return false;
        }
        String lower = line.toLowerCase(Locale.US);
        return lower.startsWith("model:")
                || lower.startsWith("provider:")
                || lower.startsWith("nickname:")
                || lower.startsWith("role:")
                || lower.startsWith("assistant:")
                || lower.startsWith("assistant ");
    }

    private String stripLeadingAssistantLabelForTts(String source) {
        if (source == null || source.length() == 0) {
            return "";
        }
        String trimmed = source.trim();
        String lower = trimmed.toLowerCase(Locale.US);
        if (lower.startsWith("assistant:")) {
            return trimmed.substring("assistant:".length()).trim();
        }
        if (lower.startsWith("assistant ")) {
            return trimmed.substring("assistant ".length()).trim();
        }
        return trimmed;
    }

    private String extractPlainAssistantTextForTts(String source) {
        if (source == null) {
            return "";
        }
        String trimmed = source.trim();
        if (trimmed.length() == 0) {
            return "";
        }
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                JSONObject object = JSON.getObject(trimmed);
                String content = extractJsonSpeakableText(object);
                if (content != null && content.trim().length() != 0) {
                    return content;
                }
            } catch (Exception ignored) {
                try {
                    cc.nnproject.json.JSONArray array = JSON.getArray(trimmed);
                    String content = extractJsonArraySpeakableText(array);
                    if (content != null && content.trim().length() != 0) {
                        return content;
                    }
                } catch (Exception ignoredAgain) {
                }
            }
        }
        return source;
    }

    private String extractJsonSpeakableText(JSONObject object) {
        if (object == null) {
            return null;
        }
        String[] directKeys = new String[] {"content", "text", "message", "response", "output_text"};
        for (int i = 0; i < directKeys.length; i++) {
            String value = tryGetJsonString(object, directKeys[i]);
            if (value != null && value.trim().length() != 0 && !looksLikeJsonStructure(value)) {
                return value;
            }
        }
        try {
            cc.nnproject.json.JSONArray choices = object.getArray("choices");
            String fromChoices = extractJsonArraySpeakableText(choices);
            if (fromChoices != null && fromChoices.trim().length() != 0) {
                return fromChoices;
            }
        } catch (Exception ignored) {
        }
        try {
            JSONObject message = object.getObject("message");
            String fromMessage = extractJsonSpeakableText(message);
            if (fromMessage != null && fromMessage.trim().length() != 0) {
                return fromMessage;
            }
        } catch (Exception ignored) {
        }
        try {
            cc.nnproject.json.JSONArray contentArray = object.getArray("content");
            String fromContent = extractJsonArraySpeakableText(contentArray);
            if (fromContent != null && fromContent.trim().length() != 0) {
                return fromContent;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String extractJsonArraySpeakableText(cc.nnproject.json.JSONArray array) {
        if (array == null || array.size() == 0) {
            return null;
        }
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < array.size(); i++) {
            try {
                JSONObject item = array.getObject(i);
                String text = extractJsonSpeakableText(item);
                if (text != null && text.trim().length() != 0) {
                    if (buffer.length() > 0) {
                        buffer.append('\n');
                    }
                    buffer.append(text);
                }
                continue;
            } catch (Exception ignored) {
            }
            try {
                String text = array.getString(i);
                if (text != null && text.trim().length() != 0 && !looksLikeJsonStructure(text)) {
                    if (buffer.length() > 0) {
                        buffer.append('\n');
                    }
                    buffer.append(text);
                }
            } catch (Exception ignored) {
            }
        }
        return buffer.length() == 0 ? null : buffer.toString();
    }

    private String tryGetJsonString(JSONObject object, String key) {
        if (object == null || key == null || !object.has(key)) {
            return null;
        }
        try {
            return object.getString(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean looksLikeJsonStructure(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    private String normalizeMarkdownForTts(String source) {
        if (source == null || source.length() == 0) {
            return "";
        }
        String[] lines = source.replace('\r', '\n').split("\n", -1);
        StringBuffer out = new StringBuffer(source.length());
        boolean inCodeBlock = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock) {
                continue;
            }
            String normalizedLine = normalizeMarkdownLineForTts(line);
            if (isMetadataLikeTtsLine(normalizedLine)) {
                continue;
            }
            if (normalizedLine.length() == 0) {
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                    out.append('\n');
                }
                continue;
            }
            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                out.append('\n');
            }
            out.append(normalizedLine);
        }
        return out.toString();
    }

    private String normalizeMarkdownLineForTts(String line) {
        String trimmed = line != null ? line.trim() : "";
        if (trimmed.length() == 0) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.US);
        if (lower.startsWith("assistant:")) {
            trimmed = trimmed.substring("assistant:".length()).trim();
        } else if (lower.startsWith("assistant ")) {
            trimmed = trimmed.substring("assistant ".length()).trim();
        }
        int headingLevel = 0;
        while (headingLevel < trimmed.length() && headingLevel < 6 && trimmed.charAt(headingLevel) == '#') {
            headingLevel++;
        }
        if (headingLevel > 0 && headingLevel < trimmed.length() && trimmed.charAt(headingLevel) == ' ') {
            trimmed = trimmed.substring(headingLevel + 1).trim();
        }
        if (trimmed.startsWith("> ")) {
            trimmed = trimmed.substring(2).trim();
        }
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            trimmed = trimmed.substring(2).trim();
        } else {
            int orderedIndex = 0;
            while (orderedIndex < trimmed.length() && Character.isDigit(trimmed.charAt(orderedIndex))) {
                orderedIndex++;
            }
            if (orderedIndex > 0 && orderedIndex + 1 < trimmed.length()
                    && trimmed.charAt(orderedIndex) == '.'
                    && trimmed.charAt(orderedIndex + 1) == ' ') {
                trimmed = trimmed.substring(orderedIndex + 2).trim();
            }
        }
        return stripInlineMarkdownForTts(trimmed);
    }

    private String stripInlineMarkdownForTts(String text) {
        if (text == null || text.length() == 0) {
            return "";
        }
        StringBuffer out = new StringBuffer(text.length());
        for (int i = 0; i < text.length();) {
            if (text.startsWith("~~", i) || text.startsWith("**", i) || text.startsWith("__", i)) {
                i += 2;
                continue;
            }
            char ch = text.charAt(i);
            if (ch == '*' || ch == '_' || ch == '`') {
                i++;
                continue;
            }
            if (ch == '[') {
                int labelEnd = text.indexOf("](", i + 1);
                if (labelEnd != -1) {
                    int urlEnd = text.indexOf(')', labelEnd + 2);
                    if (urlEnd != -1) {
                        out.append(text.substring(i + 1, labelEnd));
                        i = urlEnd + 1;
                        continue;
                    }
                }
            }
            if (ch == '|') {
                out.append(' ');
                i++;
                continue;
            }
            int codePoint = text.codePointAt(i);
            out.append(Character.toChars(codePoint));
            i += Character.charCount(codePoint);
        }
        return out.toString()
                .replace("------------", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replaceAll("https?://\\S+", " ")
                .replaceAll("(?i)\\b(role|assistant|system|debug|metadata|layout|contentdescription)\\s*[:=]\\s*", " ")
                .trim();
    }

    private boolean isMetadataLikeTtsLine(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.trim();
        if (trimmed.length() == 0) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.US);
        if (lower.startsWith("model:")
                || lower.startsWith("provider:")
                || lower.startsWith("reasoning:")
                || lower.startsWith("thinking:")
                || lower.startsWith("markdown:")
                || lower.startsWith("content:")
                || lower.startsWith("response:")) {
            return true;
        }
        if (trimmed.length() > 96) {
            return false;
        }
        String compact = lower.replaceAll("[\\s\\-_/.:()\\[\\]{}]+", " ").trim();
        if (compact.length() == 0) {
            return true;
        }
        if (compact.indexOf('!') != -1 || compact.indexOf('?') != -1) {
            return false;
        }
        String[] tokens = compact.split("\\s+");
        if (tokens.length > 8) {
            return false;
        }
        int metadataTokens = 0;
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.length() == 0) {
                continue;
            }
            if ("gpt".equals(token) || token.startsWith("gpt-")
                    || "gemini".equals(token)
                    || "google".equals(token)
                    || "claude".equals(token)
                    || "openai".equals(token)
                    || "openrouter".equals(token)
                    || "voidai".equals(token)
                    || "ollama".equals(token)
                    || "localai".equals(token)
                    || "llama".equals(token)
                    || "studio".equals(token)
                    || "lm".equals(token)
                    || "model".equals(token)
                    || "provider".equals(token)
                    || "material".equals(token)
                    || "markdown".equals(token)
                    || "layout".equals(token)
                    || "service".equals(token)
                    || "strings".equals(token)
                    || "ui".equals(token)
                    || "ux".equals(token)
                    || token.matches("^[a-z]+\\.[a-z0-9._-]+$")) {
                metadataTokens++;
            }
        }
        return metadataTokens > 0 && metadataTokens >= tokens.length - 1;
    }

    private void stopSpeakingMessage(Message message) {
        activeTtsUtteranceId = null;
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
        if (message == null || message == speakingMessage) {
            speakingMessage = null;
            adapter.setSpeakingMessage(null);
        }
    }

    @Override
    protected void onDestroy() {
        resetTextToSpeechEngine();
        super.onDestroy();
    }

    private String stripEmojiForTts(String source) {
        if (source == null || source.length() == 0) {
            return "";
        }
        StringBuffer buffer = new StringBuffer(source.length());
        int length = source.length();
        for (int i = 0; i < length;) {
            int codePoint = source.codePointAt(i);
            i += Character.charCount(codePoint);
            if (isEmojiCodePoint(codePoint)) {
                continue;
            }
            buffer.append(Character.toChars(codePoint));
        }
        return buffer.toString();
    }

    private boolean isEmojiCodePoint(int codePoint) {
        if (codePoint == 0x200D || (codePoint >= 0xFE00 && codePoint <= 0xFE0F)) {
            return true;
        }
        if (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF) {
            return true;
        }
        if (codePoint >= 0x1F3FB && codePoint <= 0x1F3FF) {
            return true;
        }
        int type = Character.getType(codePoint);
        return type == Character.OTHER_SYMBOL;
    }

    private boolean maybeRetryWithoutStreaming(String errorMsg) {
        if (!currentResponseUsedStreaming || currentResponseRetriedWithoutStreaming) {
            return false;
        }
        String normalized = errorMsg != null ? errorMsg.toLowerCase(Locale.US) : "";
        if (normalized.length() == 0) {
            return false;
        }
        if (normalized.indexOf("socket closed") == -1
                && normalized.indexOf("unexpected") == -1
                && normalized.indexOf("malformed") == -1
                && normalized.indexOf("timeout") == -1
                && normalized.indexOf("json") == -1
                && normalized.indexOf("stream") == -1
                && normalized.indexOf("connection") == -1
                && normalized.indexOf("eof") == -1
                && normalized.indexOf("reset") == -1) {
            return false;
        }
        currentResponseRetriedWithoutStreaming = true;
        config.setProviderStreamSupport(config.getConfig().getBaseUrl(), ConfigManager.STREAM_SUPPORT_FALSE);
        List<Message> messages = MessageManager.getInstance().getMessages();
        if (activeAssistantMessage != null) {
            messages.remove(activeAssistantMessage);
            chatRepository.deleteMessage(activeAssistantMessage.getMessageId());
        }
        stopReasoningTicker();
        cancelResponseLoaderTicker();
        activeAssistantMessage = null;
        activeReasoningStartTime = 0L;
        thinkBuffer.setLength(0);
        contentBuffer.setLength(0);
        isThinkingState = false;
        currentStream = null;
        adapter.notifyDataSetChanged();
        updateEmptyState();
        refreshChatList();
        Toast.makeText(this, R.string.streaming_fallback_message, Toast.LENGTH_SHORT).show();
        requestAssistantResponse(lastRequestedThinkingEnabled, false, Boolean.FALSE);
        return true;
    }

    private void readStream(InputStream inputStream, final Message msg, final boolean thinkingEnabled) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        String line;
        long lastUpdateTime = 0;
        while (!isCancelled && (line = reader.readLine()) != null) {
            if (!line.startsWith("data: ")) continue;
            String jsonData = line.substring(6).trim();
            if ("[DONE]".equals(jsonData)) break;
            try {
                JSONObject delta = JSON.getObject(jsonData).getArray("choices").getObject(0).getObject("delta");
                String contentStr = null;
                try {
                    contentStr = delta.getString("content");
                } catch(JSONException ignored) {}
                String reasoningStr = extractJSONReasoning(delta);
                boolean hasUpdates = false;
                if (thinkingEnabled) {
                    if (reasoningStr != null && reasoningStr.length() > 0) {
                        thinkBuffer.append(reasoningStr);
                        hasUpdates = true;
                    }
                    if (contentStr != null && contentStr.length() > 0) {
                        processContentWithTags(contentStr);
                        hasUpdates = true;
                    }
                } else {
                    if (contentStr != null && contentStr.length() > 0) {
                        processContentWithTags(contentStr);
                        hasUpdates = true;
                    }
                }
                long currentTime = System.currentTimeMillis();
                if (hasUpdates && (currentTime - lastUpdateTime >= UPDATE_DELAY_MS)) {
                    lastUpdateTime = currentTime;
                    updateStreamUI(msg, thinkingEnabled, false);
                }
            } catch (Exception e) {
                Log.e("readStream", "error parsing chunk " + jsonData, e);
            }
        }
        if (isCancelled) return;
        runOnUiThread(new Runnable() {
            public void run() {
                if (currentResponseUsedStreaming) {
                    config.setProviderStreamSupport(config.getConfig().getBaseUrl(), ConfigManager.STREAM_SUPPORT_TRUE);
                }
                updateStreamUI(msg, thinkingEnabled, true);
                resetUIState();
            }
        });
    }

    private void processContentWithTags(String token) {
        int cursor = 0;
        while (cursor < token.length()) {
            if (isThinkingState) {
                int endTag = token.indexOf("</think>", cursor);
                if (endTag != -1) {
                    thinkBuffer.append(token.substring(cursor, endTag));
                    isThinkingState = false;
                    cursor = endTag + 8; // </think>
                } else {
                    thinkBuffer.append(token.substring(cursor));
                    break;
                }
            } else {
                int startTag = token.indexOf("<think>", cursor);
                if (startTag != -1) {
                    contentBuffer.append(token.substring(cursor, startTag));
                    isThinkingState = true;
                    cursor = startTag + 7; // <think>
                } else {
                    contentBuffer.append(token.substring(cursor));
                    break;
                }
            }
        }
    }

    private void updateStreamUI(final Message msg, final boolean thinkingEnabled, final boolean isFinal) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(new Runnable() {
                public void run() {
                    updateStreamUI(msg, thinkingEnabled, isFinal);
                }
            });
            return;
        }
        String displayContent = contentBuffer.toString();
        String displayThink = thinkBuffer.toString();
        msg.setContent(displayContent);
        msg.setContentFinal(isFinal ? displayContent : "");
        msg.setReasoningUsed(thinkingEnabled);
        if (isFinal) {
            Log.d(TAG, "Assistant final text ready from stream messageId=" + msg.getMessageId()
                    + " text=" + quoteForLog(msg.getContentFinal()));
        }
        if (thinkingEnabled) {
            adapter.setResponseLoaderVisible(msg, false);
            adapter.updateReasoningState(msg, displayThink, true, !isFinal, getReasoningDurationSeconds());
            adapter.setResponseComplete(msg, isFinal);
            if (isFinal) {
                stopReasoningTicker();
            }
        } else {
            if (displayContent.length() > 0 || isFinal) {
                cancelResponseLoaderTicker();
                adapter.setResponseLoaderVisible(msg, false);
            }
            adapter.updateReasoningState(msg, "", false, false, 0);
            adapter.setResponseComplete(msg, isFinal);
        }
        chatRepository.updateAssistantMessage(msg);
        refreshChatList();
        adapter.notifyDataSetChanged();
    }

    private int getReasoningDurationSeconds() {
        if (activeReasoningStartTime == 0L) {
            return 0;
        }
        return (int) ((System.currentTimeMillis() - activeReasoningStartTime) / 1000L);
    }

    private void startReasoningTicker(final Message message) {
        stopReasoningTicker();
        reasoningTicker = new Runnable() {
            public void run() {
                if (!isGenerating || message != activeAssistantMessage) {
                    return;
                }
                adapter.updateReasoningState(message, thinkBuffer.toString(), true, true, getReasoningDurationSeconds());
                adapter.notifyDataSetChanged();
                uiHandler.postDelayed(this, 1000L);
            }
        };
        uiHandler.postDelayed(reasoningTicker, 1000L);
    }

    private void stopReasoningTicker() {
        if (reasoningTicker != null) {
            uiHandler.removeCallbacks(reasoningTicker);
            reasoningTicker = null;
        }
    }

    private void scheduleResponseLoaderTicker(final Message message) {
        cancelResponseLoaderTicker();
        responseLoaderTicker = new Runnable() {
            public void run() {
                if (!isGenerating || message != activeAssistantMessage || contentBuffer.length() != 0) {
                    return;
                }
                adapter.setResponseLoaderVisible(message, true);
                adapter.notifyDataSetChanged();
            }
        };
        uiHandler.postDelayed(responseLoaderTicker, 180L);
    }

    private void cancelResponseLoaderTicker() {
        if (responseLoaderTicker != null) {
            uiHandler.removeCallbacks(responseLoaderTicker);
            responseLoaderTicker = null;
        }
    }

    private ErrorDisplayData classifyError(String rawError, String modelName) {
        String normalized = rawError != null ? rawError.toLowerCase(Locale.US) : "";
        String title = getString(R.string.error_generic_title);
        String summary = getString(R.string.error_unknown_summary);

        if (normalized.indexOf("invalid api") != -1 || normalized.indexOf("unauthorized") != -1 || normalized.indexOf("401") != -1) {
            title = getString(R.string.error_invalid_api_key_title);
            summary = getString(R.string.error_invalid_api_key_summary);
        } else if (normalized.indexOf("model") != -1 && normalized.indexOf("not found") != -1) {
            title = getString(R.string.error_model_not_found_title);
            summary = getString(R.string.error_model_not_found_summary);
        } else if (normalized.indexOf("quota") != -1 || normalized.indexOf("credit") != -1 || normalized.indexOf("429") != -1) {
            title = getString(R.string.error_quota_title);
            summary = getString(R.string.error_quota_summary);
        } else if (normalized.indexOf("timed out") != -1 || normalized.indexOf("timeout") != -1) {
            title = getString(R.string.error_timeout_title);
            summary = getString(R.string.error_timeout_summary);
        } else if (normalized.indexOf("network") != -1 || normalized.indexOf("failed to connect") != -1 || normalized.indexOf("unable to resolve host") != -1) {
            title = getString(R.string.error_network_title);
            summary = getString(R.string.error_network_summary);
        }

        String providerName = ApiManager.getNameByUrl(config.getConfig().getBaseUrl());
        if (providerName == null || providerName.length() == 0) {
            providerName = config.getConfig().getBaseUrl();
        }

        StringBuffer details = new StringBuffer();
        details.append(getString(R.string.error_details_raw, rawError != null ? rawError : "")).append('\n');
        details.append(getString(R.string.error_details_http, extractHttpStatus(rawError) != null ? extractHttpStatus(rawError) : getString(R.string.not_available_short))).append('\n');
        details.append(getString(R.string.error_details_provider, providerName != null ? providerName : "")).append('\n');
        details.append(getString(R.string.error_details_model, modelName != null ? modelName : ""));
        return new ErrorDisplayData(title, summary, details.toString());
    }

    private String extractHttpStatus(String rawError) {
        if (rawError == null) {
            return null;
        }
        for (int i = 0; i + 2 < rawError.length(); i++) {
            char a = rawError.charAt(i);
            char b = rawError.charAt(i + 1);
            char c = rawError.charAt(i + 2);
            if (Character.isDigit(a) && Character.isDigit(b) && Character.isDigit(c)) {
                return rawError.substring(i, i + 3);
            }
        }
        return null;
    }

    private String formatChatUpdatedAt(long updatedAt) {
        if (updatedAt <= 0L) {
            return "";
        }
        long diff = System.currentTimeMillis() - updatedAt;
        if (diff < 60000L) {
            return getString(R.string.time_just_now);
        }
        if (diff < 3600000L) {
            return getString(R.string.time_minutes_ago, Integer.valueOf((int) (diff / 60000L)));
        }
        if (diff < 86400000L) {
            return getString(R.string.time_hours_ago, Integer.valueOf((int) (diff / 3600000L)));
        }
        if (diff < 172800000L) {
            return getString(R.string.time_yesterday);
        }
        return DateFormat.getDateFormat(this).format(new Date(updatedAt));
    }

    private class MainPagerAdapter extends PagerAdapter {
        public int getCount() {
            return 2;
        }

        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        public Object instantiateItem(ViewGroup container, int position) {
            View page = position == 0 ? chatsPage : chatPage;
            if (page.getParent() != null) {
                ((ViewGroup) page.getParent()).removeView(page);
            }
            container.addView(page);
            return page;
        }

        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }
    }

    private class ChatListAdapter extends BaseAdapter {
        public int getCount() {
            return chatListItems.size();
        }

        public Object getItem(int position) {
            return chatListItems.get(position);
        }

        public long getItemId(int position) {
            return chatListItems.get(position).getChatId();
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.item_chat_list_stub, parent, false);
            }
            TextView title = (TextView) view.findViewById(R.id.chat_item_title);
            TextView subtitle = (TextView) view.findViewById(R.id.chat_item_subtitle);
            TextView meta = (TextView) view.findViewById(R.id.chat_item_meta);
            ChatRecord item = chatListItems.get(position);
            String titleText = item.getTitle();
            if (titleText == null || titleText.trim().length() == 0) {
                titleText = getString(R.string.untitled_chat);
            }
            title.setText(titleText);
            String preview = item.getLastMessagePreview();
            subtitle.setText(preview != null && preview.length() != 0 ? preview : getString(R.string.empty_chat_hint));
            if (meta != null) {
                meta.setText(formatChatUpdatedAt(item.getUpdatedAt()));
            }
            return view;
        }
    }

    private static class ModelSelectorAdapter extends ArrayAdapter<String> {
        private final LayoutInflater inflater;
        private String selectedModel;

        ModelSelectorAdapter(Context context, String[] models) {
            super(context, R.layout.spinner_toolbar_item, models);
            this.inflater = LayoutInflater.from(context);
        }

        ModelSelectorAdapter(Context context, List<String> models) {
            super(context, R.layout.spinner_toolbar_item, models);
            this.inflater = LayoutInflater.from(context);
        }

        void setSelectedModel(String selectedModel) {
            this.selectedModel = selectedModel;
            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            TextView title = (TextView) view.findViewById(android.R.id.text1);
            if (title != null) {
                title.setText(getItem(position));
            }
            return view;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = inflater.inflate(R.layout.spinner_toolbar_dropdown_item, parent, false);
            }
            TextView title = (TextView) view.findViewById(android.R.id.text1);
            ImageView check = (ImageView) view.findViewById(R.id.check_icon);
            String model = getItem(position);
            if (title != null) {
                title.setText(model);
            }
            if (check != null) {
                check.setVisibility(model != null && model.equals(selectedModel) ? View.VISIBLE : View.INVISIBLE);
            }
            return view;
        }
    }

    private static class ComposeAction {
        final int id;
        final String title;
        boolean checked;

        ComposeAction(int id, String title, boolean checked) {
            this.id = id;
            this.title = title;
            this.checked = checked;
        }
    }

    private static class ErrorDisplayData {
        final String title;
        final String summary;
        final String details;

        ErrorDisplayData(String title, String summary, String details) {
            this.title = title;
            this.summary = summary;
            this.details = details;
        }
    }

    private class ComposeActionsAdapter extends BaseAdapter {
        private final LayoutInflater inflater = LayoutInflater.from(MainActivity.this);

        public int getCount() {
            return composeActions.size();
        }

        public Object getItem(int position) {
            return composeActions.get(position);
        }

        public long getItemId(int position) {
            return composeActions.get(position).id;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = inflater.inflate(R.layout.spinner_toolbar_dropdown_item, parent, false);
            }
            TextView title = (TextView) view.findViewById(android.R.id.text1);
            ImageView check = (ImageView) view.findViewById(R.id.check_icon);
            ComposeAction action = composeActions.get(position);
            title.setText(action.title);
            check.setVisibility(action.checked ? View.VISIBLE : View.INVISIBLE);
            return view;
        }
    }

    public static Bitmap decodeSampledBitmap(Context ctx, Uri uri, int reqW, int reqH) throws IOException {
        InputStream is = ctx.getContentResolver().openInputStream(uri);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, options);
        is.close();

        options.inSampleSize = calculateInSampleSize(options, reqW, reqH);
        options.inJustDecodeBounds = false;

        is = ctx.getContentResolver().openInputStream(uri);
        Bitmap bmp = BitmapFactory.decodeStream(is, null, options);
        is.close();
        return bmp;
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqW, int reqH) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqH || width > reqW) {
            if (width > height) {
                while ((width / inSampleSize) > reqW) {
                    inSampleSize *= 2;
                }
            }else {
                while ((height / inSampleSize) > reqH) {
                    inSampleSize *= 2;
                }
            }
        }
        return inSampleSize;
    }

    private String quoteForLog(String value) {
        if (value == null) {
            return "\"\"";
        }
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    private String extractJSONReasoning(JSONObject delta) {
        try {
            return delta.getString("reasoning");
        } catch (Exception e) {
            try {
                return delta.getArray("reasoning_content").getObject(0).getString("thinking");
            } catch(Exception ignored) {
                return null;
            }
        }
    }
}
