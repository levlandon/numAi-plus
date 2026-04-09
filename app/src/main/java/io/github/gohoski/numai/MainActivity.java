package io.github.gohoski.numai;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.speech.tts.TextToSpeech;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    private ApiService apiService;
    private ConfigManager config;

    private ViewPager mainPager;
    private View chatsPage;
    private View chatPage;
    private ListView msgList;
    private ListView chatsList;
    private EditText input;
    private MessageAdapter adapter;
    private ImageButton sendBtn;
    private Spinner modelSelector;
    private TextView emptyState;
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
    int UPDATE_DELAY_MS = 250;

    // Stream buffers
    private final StringBuilder thinkBuffer = new StringBuilder();
    private final StringBuilder contentBuffer = new StringBuilder();
    private final List<String> inputImages = new ArrayList<String>();
    private final List<String> pendingAttachmentNames = new ArrayList<String>();
    private final List<ComposeAction> composeActions = new ArrayList<ComposeAction>();
    private final ArrayList<ChatListItem> chatListItems = new ArrayList<ChatListItem>();
    private final Handler uiHandler = new Handler();
    private Message activeAssistantMessage;
    private long activeReasoningStartTime;
    private Runnable reasoningTicker;
    private Runnable responseLoaderTicker;
    private boolean removeLastUserOnStop;
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;
    private boolean ttsInitializing = false;
    private Message speakingMessage;

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
        if (config.getConfig().getApiKey().length() == 0) {
            startActivity(new Intent(this, FirstTimeActivity.class));
            finish();
            return;
        }
        UPDATE_DELAY_MS = config.getConfig().getUpdateDelay();

        apiService = new ApiService(this);
        msgList = (ListView) chatPage.findViewById(R.id.messages_list);
        chatsList = (ListView) chatsPage.findViewById(R.id.chats_list);
        input = (EditText) chatPage.findViewById(R.id.message_input);
        sendBtn = (ImageButton) chatPage.findViewById(R.id.send_button);
        attachBtn = (ImageButton) chatPage.findViewById(R.id.attach_button);
        modelSelector = (Spinner) chatPage.findViewById(R.id.model_selector);
        emptyState = (TextView) chatPage.findViewById(R.id.empty_state);
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
                showComposeActionsMenu();
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
        });
        msgList.setAdapter(adapter);
        updateAttachmentPreview();
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
        applyVisibleModelsToSelector();
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
        boolean canSend = !isGenerating && (hasInput || hasImages);
        if (sendBtn != null) {
            sendBtn.setEnabled(canSend || isGenerating);
        }
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
                applyVisibleModelsToSelector();
                modelsFetched = true;
                loading.dismiss();
                modelSelector.performClick();
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

    private void setupComposeActions() {
        composeActions.clear();
        composeActions.add(new ComposeAction(R.id.action_attach_image, getString(R.string.select_picture), false));
        composeActions.add(new ComposeAction(R.id.action_toggle_thinking, getString(R.string.thinking_mode), thinkingEnabled));
    }

    private void setupChatsPage() {
        chatListItems.clear();
        chatListItems.add(new ChatListItem(getString(R.string.current_chat), getString(R.string.current_chat_hint)));
        chatListItems.add(new ChatListItem(getString(R.string.chat_history_soon), getString(R.string.chat_history_hint)));

        chatsList.setAdapter(new ChatListAdapter());
        chatsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mainPager.setCurrentItem(1, true);
            }
        });

        View profileButton = chatsPage.findViewById(R.id.profile_button);
        profileButton.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        View newChatButton = chatsPage.findViewById(R.id.new_chat_button);
        newChatButton.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, R.string.new_chat_soon, Toast.LENGTH_SHORT).show();
            }
        });
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
        int selectedIndex = visibleModels.indexOf(currentModel);
        modelSelector.setSelection(selectedIndex >= 0 ? selectedIndex : 0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                startActivity(new Intent(this, SettingsActivity.class));
                finish();
                return true;
            case R.id.about:
                Toast.makeText(this, "numAi " + BuildConfig.VERSION_NAME + " (" + BuildConfig.BUILD_TYPE + ") ▶\ngithub.com/gohoski/numAi", Toast.LENGTH_SHORT).show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            processSelectedImage(data.getData());
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
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
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

        //Remove the messages from the data source
        List<Message> msgs = MessageManager.getInstance().getMessages();
        int size = msgs.size();
        if (size > 0 && msgs.get(size - 1).getRole().equals(Role.ASSISTANT.toString())) {
            msgs.remove(size - 1);
            size--;
        }
        if (removeLastUserOnStop && size > 0 && msgs.get(size - 1).getRole().equals(Role.USER.toString())) {
            msgs.remove(size - 1);
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
        autoScroll = true;
        isCancelled = false;
        currentStream = null;
        MessageManager.getInstance().addMessage(new Message(Role.USER, text, new ArrayList<String>(inputImages), null));
        input.setText("");
        sendBtn.setImageResource(R.drawable.ic_stop_generation);
        input.setEnabled(false);
        attachBtn.setEnabled(false);
        modelSelector.setEnabled(false);
        inputImages.clear();
        pendingAttachmentNames.clear();
        updateAttachmentPreview();
        adapter.notifyDataSetChanged();
        updateEmptyState();
        requestAssistantResponse(this.thinkingEnabled, true);
    }

    private void startResponseStream(final InputStream stream, String model, final boolean thinkingEnabled, Message message) {
        this.currentStream = stream;
        final Message msg = message != null ? message : new Message(Role.ASSISTANT, "", model);
        msg.setLlm(model);
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
        List<Message> messages = MessageManager.getInstance().getMessages();
        if (activeAssistantMessage != null) {
            messages.remove(activeAssistantMessage);
        }
        Message error = new Message(Role.ASSISTANT, errorMsg, getString(R.string.error));
        error.setAsError();
        MessageManager.getInstance().addMessage(error);
        updateEmptyState();
        resetUIState();
    }

    private void resetUIState() {
        isGenerating = false;
        removeLastUserOnStop = false;
        stopReasoningTicker();
        cancelResponseLoaderTicker();
        activeAssistantMessage = null;
        activeReasoningStartTime = 0L;
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
        removeLastUserOnStop = rollbackUserOnStop;
        sendBtn.setImageResource(R.drawable.ic_stop_generation);
        input.setEnabled(false);
        attachBtn.setEnabled(false);
        modelSelector.setEnabled(false);
        updateComposerState();

        final Message pendingAssistant = new Message(Role.ASSISTANT, "", config.getConfig().getChatModel());
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
        isGenerating = true;
        apiService.chatCompletion(MessageManager.getInstance().getMessages(), thinkingEnabled, new ApiCallback<ApiResult>() {
            @Override
            public void onSuccess(final ApiResult apiResult) {
                if (isCancelled)return;
                runOnUiThread(new Runnable() {
                    public void run() {
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

    private void showUserMessageMenu(View anchor, final Message message) {
        final ArrayList<String> actions = new ArrayList<String>();
        actions.add(getString(R.string.copy_action));
        actions.add(getString(R.string.select_text_action));

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
        adapter.notifyDataSetChanged();
        updateEmptyState();
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
        ttsInitializing = true;
        final Message messageToSpeak = pendingMessage;
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            public void onInit(int status) {
                ttsInitializing = false;
                if (status != TextToSpeech.SUCCESS || textToSpeech == null) {
                    ttsReady = false;
                    Toast.makeText(MainActivity.this, R.string.tts_not_available, Toast.LENGTH_SHORT).show();
                    return;
                }
                int availability = textToSpeech.setLanguage(Locale.getDefault());
                if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsReady = false;
                    Toast.makeText(MainActivity.this, R.string.tts_not_available, Toast.LENGTH_SHORT).show();
                    return;
                }
                textToSpeech.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {
                    public void onUtteranceCompleted(String utteranceId) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                speakingMessage = null;
                                adapter.setSpeakingMessage(null);
                            }
                        });
                    }
                });
                ttsReady = true;
                startSpeakingMessage(messageToSpeak);
            }
        });
        return false;
    }

    private void startSpeakingMessage(Message message) {
        if (message == null || message.getContent() == null || message.getContent().trim().length() == 0) {
            return;
        }
        if (textToSpeech == null || !ttsReady) {
            return;
        }
        String speakableText = stripEmojiForTts(message.getContent()).trim();
        if (speakableText.length() == 0) {
            return;
        }
        if (speakingMessage != null && speakingMessage != message) {
            textToSpeech.stop();
        }
        speakingMessage = message;
        adapter.setSpeakingMessage(message);
        HashMap params = new HashMap();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "numai_tts");
        textToSpeech.speak(speakableText, TextToSpeech.QUEUE_FLUSH, params);
    }

    private void stopSpeakingMessage(Message message) {
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
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
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
                    if (reasoningStr != null) contentBuffer.append(reasoningStr);
                    if (contentStr != null) contentBuffer.append(contentStr);
                    hasUpdates = (reasoningStr != null || contentStr != null);
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

    private static class ChatListItem {
        final String title;
        final String subtitle;

        ChatListItem(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
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
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.item_chat_list_stub, parent, false);
            }
            TextView title = (TextView) view.findViewById(R.id.chat_item_title);
            TextView subtitle = (TextView) view.findViewById(R.id.chat_item_subtitle);
            ChatListItem item = chatListItems.get(position);
            title.setText(item.title);
            subtitle.setText(item.subtitle);
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
