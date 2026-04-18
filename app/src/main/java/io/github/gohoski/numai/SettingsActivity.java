package io.github.gohoski.numai;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.net.wifi.WifiManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ListPopupWindow;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class SettingsActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_IMPORT_KEY = 2;
    private static final int REQUEST_CODE_PICK_AVATAR = 3;
    private static final int REQUEST_CODE_CROP_AVATAR = 4;
    private static final String OPTION_SYSTEM = "__system__";
    private static final String OPTION_CUSTOM_PROVIDER = "__custom_provider__";
    private static final String PROVIDER_LM_STUDIO = "LM Studio";
    private static final String ABOUT_GITHUB_URL = "https://github.com/levlandon/numAi-plus";

    private static final String OPTION_DEFAULT = "__default__";

    private Context context;
    private ConfigManager config;
    private ApiService api;

    private EditText keyText;
    private TextView selectedModelsSummary;
    private ImageView avatarPreview;
    private EditText nickname;
    private EditText userName;
    private EditText userRole;
    private EditText usageGoal;
    private EditText customSystemPrompt;

    private View sectionProfile;
    private View sectionPersonalization;
    private View sectionProvider;
    private Button tabProfile;
    private Button tabPersonalization;
    private Button tabProvider;

    private SelectorRow providerSelector;
    private SelectorRow streamingModeSelector;
    private SelectorRow thinkingModelSelector;
    private SelectorRow responseStyleSelector;
    private SelectorRow responseDetailSelector;
    private SelectorRow responseEmotionalitySelector;
    private SelectorRow languageSelector;

    private View customProviderContainer;
    private EditText customProviderUrl;
    private Button checkProviderConnection;
    private TextView providerStatusLabel;
    private View providerDiagnosticsContainer;
    private TextView providerDiagnosticsGateway;
    private TextView providerDiagnosticsLocal;
    private TextView providerDiagnosticsInternet;
    private TextView providerDiagnosticsProvider;
    private TextView providerDiagnosticsHint;

    private final ArrayList<String> cachedThinkingModels = new ArrayList<String>();
    private final HashMap<String, String> pendingProviderUrls = new HashMap<String, String>();
    private boolean fetchedThinkingModels = false;
    private final Handler providerCheckHandler = new Handler();
    private Runnable providerCheckRunnable;
    private int providerDiagnosticsVersion;

    private String pendingAvatarPath;
    private String selectedProviderUrl;
    private String selectedProviderType;
    private String selectedStreamingMode;
    private String selectedThinkingModel;
    private String selectedResponseStyle;
    private String selectedResponseDetailLevel;
    private String selectedResponseEmotionality;
    private String selectedLanguage;
    private String providerStatus = ConfigManager.PROVIDER_STATUS_UNKNOWN;
    private long providerLastCheckTimestamp;
    private boolean suppressCustomProviderWatcher;
    private boolean providerCheckRunning;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        context = this;
        config = ConfigManager.getInstance(this);
        api = new ApiService(this);

        bindViews();
        setupTabs();
        setupSelectors();
        setupButtons();
        loadFormState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyCachedThinkingModels();
        updateSelectedModelsSummary();
        renderAvatar();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        if (requestCode == REQUEST_CODE_IMPORT_KEY) {
            importApiKey(data.getData());
        } else if (requestCode == REQUEST_CODE_PICK_AVATAR) {
            startAvatarCrop(data.getData());
        } else if (requestCode == REQUEST_CODE_CROP_AVATAR) {
            pendingAvatarPath = data.getStringExtra("avatar_path");
            renderAvatar();
        }
    }

    private void bindViews() {
        keyText = (EditText) findViewById(R.id.apiKey);
        selectedModelsSummary = (TextView) findViewById(R.id.selected_models_summary);
        avatarPreview = (ImageView) findViewById(R.id.avatar_preview);
        nickname = (EditText) findViewById(R.id.nickname);
        userName = (EditText) findViewById(R.id.user_name);
        userRole = (EditText) findViewById(R.id.user_role);
        usageGoal = (EditText) findViewById(R.id.usage_goal);
        customSystemPrompt = (EditText) findViewById(R.id.custom_system_prompt);
        customProviderContainer = findViewById(R.id.custom_provider_container);
        customProviderUrl = (EditText) findViewById(R.id.custom_provider_url);
        checkProviderConnection = (Button) findViewById(R.id.check_provider_connection);
        providerStatusLabel = (TextView) findViewById(R.id.provider_status_label);
        providerDiagnosticsContainer = findViewById(R.id.provider_diagnostics_container);
        providerDiagnosticsGateway = (TextView) findViewById(R.id.provider_diagnostics_gateway);
        providerDiagnosticsLocal = (TextView) findViewById(R.id.provider_diagnostics_local);
        providerDiagnosticsInternet = (TextView) findViewById(R.id.provider_diagnostics_internet);
        providerDiagnosticsProvider = (TextView) findViewById(R.id.provider_diagnostics_provider);
        providerDiagnosticsHint = (TextView) findViewById(R.id.provider_diagnostics_hint);

        sectionProfile = findViewById(R.id.section_profile);
        sectionPersonalization = findViewById(R.id.section_personalization);
        sectionProvider = findViewById(R.id.section_provider);

        tabProfile = (Button) findViewById(R.id.tab_profile);
        tabPersonalization = (Button) findViewById(R.id.tab_personalization);
        tabProvider = (Button) findViewById(R.id.tab_provider);

        providerSelector = new SelectorRow(findViewById(R.id.provider_selector));
        streamingModeSelector = new SelectorRow(findViewById(R.id.streaming_mode_selector));
        thinkingModelSelector = new SelectorRow(findViewById(R.id.thinking_model_selector));
        responseStyleSelector = new SelectorRow(findViewById(R.id.response_style_selector));
        responseDetailSelector = new SelectorRow(findViewById(R.id.response_detail_selector));
        responseEmotionalitySelector = new SelectorRow(findViewById(R.id.response_emotionality_selector));
        languageSelector = new SelectorRow(findViewById(R.id.language_selector));
    }

    private void setupTabs() {
        tabProfile.setOnClickListener(new SectionClickListener(0));
        tabPersonalization.setOnClickListener(new SectionClickListener(1));
        tabProvider.setOnClickListener(new SectionClickListener(2));
        findViewById(R.id.back_button).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                finish();
            }
        });
        selectSection(0);
    }

    private void setupSelectors() {
        providerSelector.setTitle(getString(R.string.provider));
        providerSelector.root.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                showProviderSelector();
            }
        });

        streamingModeSelector.setTitle(getString(R.string.streaming_mode));
        streamingModeSelector.root.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                showSingleOptionSelector(streamingModeSelector.root, buildStreamingModeOptions(), selectedStreamingMode, new OptionSelectionListener() {
                    public void onSelected(SelectionOption option) {
                        selectedStreamingMode = option.value;
                        streamingModeSelector.setValue(option.label);
                    }
                });
            }
        });

        thinkingModelSelector.setTitle(getString(R.string.thinking_model));
        thinkingModelSelector.root.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                loadThinkingModelsAndShow();
            }
        });

        responseStyleSelector.setTitle(getString(R.string.response_style_label));
        responseStyleSelector.root.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                showSingleOptionSelector(responseStyleSelector.root, buildStyleOptions(), selectedResponseStyle, new OptionSelectionListener() {
                    public void onSelected(SelectionOption option) {
                        selectedResponseStyle = option.value;
                        responseStyleSelector.setValue(option.label);
                    }
                });
            }
        });

        responseDetailSelector.setTitle(getString(R.string.response_detail_level_label));
        responseDetailSelector.root.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                showSingleOptionSelector(responseDetailSelector.root, buildDetailOptions(), selectedResponseDetailLevel, new OptionSelectionListener() {
                    public void onSelected(SelectionOption option) {
                        selectedResponseDetailLevel = option.value;
                        responseDetailSelector.setValue(option.label);
                    }
                });
            }
        });

        responseEmotionalitySelector.setTitle(getString(R.string.response_emotionality_label));
        responseEmotionalitySelector.root.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                showSingleOptionSelector(responseEmotionalitySelector.root, buildEmotionalityOptions(), selectedResponseEmotionality, new OptionSelectionListener() {
                    public void onSelected(SelectionOption option) {
                        selectedResponseEmotionality = option.value;
                        responseEmotionalitySelector.setValue(option.label);
                    }
                });
            }
        });

        languageSelector.setTitle(getString(R.string.language));
        languageSelector.root.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                showSingleOptionSelector(languageSelector.root, buildLanguageOptions(), selectedLanguage, new OptionSelectionListener() {
                    public void onSelected(SelectionOption option) {
                        selectedLanguage = option.value;
                        languageSelector.setValue(option.label);
                    }
                });
            }
        });
    }

    private void setupButtons() {
        findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                finish();
            }
        });
        findViewById(R.id.ok).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                saveSettings();
            }
        });
        findViewById(R.id.about_button).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                showAboutDialog();
            }
        });
        findViewById(R.id.from_file).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("text/plain");
                startActivityForResult(Intent.createChooser(intent, getString(R.string.select_txt)), REQUEST_CODE_IMPORT_KEY);
            }
        });
        findViewById(R.id.choose_avatar).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(Intent.createChooser(intent, getString(R.string.choose_avatar)), REQUEST_CODE_PICK_AVATAR);
            }
        });
        findViewById(R.id.selected_models_button).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent intent = new Intent(context, ModelVisibilityActivity.class);
                intent.putExtra("provider_url", resolveProviderUrlForSelection());
                intent.putExtra("provider_type", selectedProviderType);
                intent.putExtra("api_key", keyText.getText().toString().trim());
                startActivity(intent);
            }
        });
        checkProviderConnection.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                checkProviderConnection(false);
            }
        });
        customProviderUrl.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable editable) {
                if (suppressCustomProviderWatcher) {
                    return;
                }
                providerStatus = ConfigManager.PROVIDER_STATUS_UNKNOWN;
                updateProviderStatusLabel();
                scheduleProviderCheck();
            }
        });
    }

    private void showAboutDialog() {
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_about);
        View back = dialog.findViewById(R.id.about_back);
        View github = dialog.findViewById(R.id.about_github_button);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        github.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                openGithubRepository();
            }
        });
        dialog.show();
    }

    private void openGithubRepository() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(ABOUT_GITHUB_URL));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.about_open_github_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void loadFormState() {
        Config conf = config.getConfig();
        keyText.setText(conf.getApiKey());
        nickname.setText(config.getNickname());
        userName.setText(config.getUserName());
        userRole.setText(config.getUserRole());
        usageGoal.setText(config.getUsageGoal());
        customSystemPrompt.setText(config.getCustomSystemPrompt());
        pendingAvatarPath = config.getAvatarPath();
        selectedProviderUrl = conf.getBaseUrl();
        selectedProviderType = resolveProviderType(conf.getBaseUrl());
        String storedProviderType = config.getProviderType();
        if (storedProviderType != null && storedProviderType.length() != 0) {
            selectedProviderType = storedProviderType;
        }
        selectedProviderUrl = config.getProviderUrlForType(selectedProviderType, selectedProviderUrl);
        selectedStreamingMode = config.getProviderStreamingMode(selectedProviderUrl);
        selectedThinkingModel = resolveProviderThinkingModel(selectedProviderUrl, conf.getThinkingModel());
        selectedResponseStyle = emptyToDefault(config.getResponseStyle());
        selectedResponseDetailLevel = emptyToDefault(config.getResponseDetailLevel());
        selectedResponseEmotionality = emptyToDefault(config.getResponseEmotionality());
        selectedLanguage = emptyToSystem(config.getAppLanguage());
        providerStatus = config.getProviderStatus();
        providerLastCheckTimestamp = config.getProviderLastCheckTimestamp();

        providerSelector.setValue(resolveProviderSelectionLabel());
        streamingModeSelector.setValue(findOptionLabel(buildStreamingModeOptions(), selectedStreamingMode));
        thinkingModelSelector.setValue(selectedThinkingModel != null && selectedThinkingModel.length() != 0
                ? selectedThinkingModel
                : getString(R.string.select_model));
        responseStyleSelector.setValue(findOptionLabel(buildStyleOptions(), selectedResponseStyle));
        responseDetailSelector.setValue(findOptionLabel(buildDetailOptions(), selectedResponseDetailLevel));
        responseEmotionalitySelector.setValue(findOptionLabel(buildEmotionalityOptions(), selectedResponseEmotionality));
        languageSelector.setValue(findOptionLabel(buildLanguageOptions(), selectedLanguage));
        suppressCustomProviderWatcher = true;
        customProviderUrl.setText(isUrlEditableProviderSelected() ? selectedProviderUrl : config.getProviderUrlForType(selectedProviderType, selectedProviderUrl));
        suppressCustomProviderWatcher = false;
        updateCustomProviderUi();
        renderAvatar();
        applyCachedThinkingModels();
        updateSelectedModelsSummary();
    }

    private void selectSection(int section) {
        sectionProfile.setVisibility(section == 0 ? View.VISIBLE : View.GONE);
        sectionPersonalization.setVisibility(section == 1 ? View.VISIBLE : View.GONE);
        sectionProvider.setVisibility(section == 2 ? View.VISIBLE : View.GONE);

        updateTab(tabProfile, section == 0);
        updateTab(tabPersonalization, section == 1);
        updateTab(tabProvider, section == 2);
    }

    private void updateTab(Button button, boolean selected) {
        button.setSelected(selected);
        button.setBackgroundResource(selected ? R.drawable.bg_button_primary : R.drawable.bg_button_secondary);
        button.setTextColor(getResources().getColor(selected ? android.R.color.white : R.color.colorPrimary));
    }

    private void showProviderSelector() {
        ArrayList<SelectionOption> options = new ArrayList<SelectionOption>();
        List<String> apiNames = ApiManager.getAllApiNames();
        for (int i = 0; i < apiNames.size(); i++) {
            String name = apiNames.get(i);
            options.add(new SelectionOption(name, name));
        }
        options.add(new SelectionOption(getString(R.string.custom_provider), OPTION_CUSTOM_PROVIDER));
        showSingleOptionSelector(providerSelector.root, options, selectedProviderType, new OptionSelectionListener() {
            public void onSelected(SelectionOption option) {
                rememberPendingProviderUrl();
                selectedProviderType = option.value;
                if (isUrlEditableProvider(option.value)) {
                    String pendingUrl = pendingProviderUrls.get(option.value);
                    selectedProviderUrl = pendingUrl != null && pendingUrl.length() != 0
                            ? pendingUrl
                            : config.getProviderUrlForType(option.value, ApiManager.getUrlByName(option.value));
                } else {
                    selectedProviderUrl = ApiManager.getUrlByName(option.value);
                    providerStatus = ConfigManager.PROVIDER_STATUS_UNKNOWN;
                }
                selectedStreamingMode = config.getProviderStreamingMode(selectedProviderUrl);
                selectedThinkingModel = config.getProviderThinkingModel(selectedProviderUrl);
                providerSelector.setValue(option.label);
                streamingModeSelector.setValue(findOptionLabel(buildStreamingModeOptions(), selectedStreamingMode));
                updateCustomProviderUi();
                applyCachedThinkingModels();
                updateSelectedModelsSummary();
            }
        });
    }

    private void loadThinkingModelsAndShow() {
        if (fetchedThinkingModels && !cachedThinkingModels.isEmpty()) {
            showThinkingModelSelector();
            return;
        }
        final String providerUrl = resolveProviderUrlForSelection();
        final String apiKey = keyText.getText().toString().trim();
        final Loading loading = new Loading(context);
        fetchModelsForProvider(providerUrl, apiKey, new ApiCallback<ArrayList<String>>() {
            public void onSuccess(ArrayList<String> result) {
                cachedThinkingModels.clear();
                cachedThinkingModels.addAll(result);
                config.setCachedModels(providerUrl, result);
                fetchedThinkingModels = !result.isEmpty();
                loading.dismiss();
                showThinkingModelSelector();
            }

            public void onError(ApiError error) {
                loading.dismiss();
                Toast.makeText(context, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showThinkingModelSelector() {
        if (cachedThinkingModels.isEmpty()) {
            Toast.makeText(context, R.string.no_models_available, Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<SelectionOption> options = new ArrayList<SelectionOption>();
        for (int i = 0; i < cachedThinkingModels.size(); i++) {
            String model = cachedThinkingModels.get(i);
            options.add(new SelectionOption(model, model));
        }
        showSingleOptionSelector(thinkingModelSelector.root, options, selectedThinkingModel, new OptionSelectionListener() {
            public void onSelected(SelectionOption option) {
                selectedThinkingModel = option.value;
                thinkingModelSelector.setValue(option.label);
            }
        });
    }

    private void showSingleOptionSelector(View anchor, final List<SelectionOption> options, String selectedValue,
                                          final OptionSelectionListener listener) {
        if (options == null || options.isEmpty()) {
            Toast.makeText(this, R.string.no_models_available, Toast.LENGTH_SHORT).show();
            return;
        }
        final ListPopupWindow popupWindow = new ListPopupWindow(this);
        popupWindow.setAnchorView(anchor);
        popupWindow.setAdapter(new SelectorPopupAdapter(options, selectedValue));
        popupWindow.setModal(true);
        popupWindow.setBackgroundDrawable(getResources().getDrawable(R.drawable.bg_spinner_dropdown));
        popupWindow.setContentWidth(dpToPx(240));
        popupWindow.setDropDownGravity(Gravity.RIGHT);
        popupWindow.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                popupWindow.dismiss();
                if (listener != null) {
                    listener.onSelected(options.get(position));
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

    private void applyCachedThinkingModels() {
        cachedThinkingModels.clear();
        cachedThinkingModels.addAll(config.getCachedModels(resolveProviderUrlForSelection()));
        fetchedThinkingModels = !cachedThinkingModels.isEmpty();
        if ((selectedThinkingModel == null || selectedThinkingModel.length() == 0) && !cachedThinkingModels.isEmpty()) {
            selectedThinkingModel = cachedThinkingModels.get(0);
            thinkingModelSelector.setValue(selectedThinkingModel);
        } else if (selectedThinkingModel != null && selectedThinkingModel.length() != 0 && !cachedThinkingModels.contains(selectedThinkingModel) && !cachedThinkingModels.isEmpty()) {
            selectedThinkingModel = cachedThinkingModels.get(0);
            thinkingModelSelector.setValue(selectedThinkingModel);
        } else if (selectedThinkingModel == null || selectedThinkingModel.length() == 0) {
            thinkingModelSelector.setValue(getString(R.string.select_model));
        } else {
            thinkingModelSelector.setValue(selectedThinkingModel);
        }
    }

    private void updateSelectedModelsSummary() {
        String providerUrl = resolveProviderUrlForSelection();
        if (config.getShowAllModels(providerUrl)) {
            selectedModelsSummary.setText(R.string.selected_models_all_summary);
            return;
        }
        int count = config.getSelectedChatModels(providerUrl).size();
        if (count == 0) {
            selectedModelsSummary.setText(R.string.selected_models_none_summary);
        } else {
            selectedModelsSummary.setText(getString(R.string.selected_models_count_summary, Integer.valueOf(count)));
        }
    }

    private void renderAvatar() {
        String path = pendingAvatarPath != null ? pendingAvatarPath : config.getAvatarPath();
        if (path != null && path.length() != 0) {
            Bitmap bitmap = BitmapFactory.decodeFile(path);
            if (bitmap != null) {
                Bitmap circularBitmap = AvatarBitmapHelper.createCircularBitmap(bitmap);
                avatarPreview.setPadding(0, 0, 0, 0);
                avatarPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                avatarPreview.setImageBitmap(circularBitmap != null ? circularBitmap : bitmap);
                return;
            }
        }
        int padding = dpToPx(8);
        avatarPreview.setPadding(padding, padding, padding, padding);
        avatarPreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        avatarPreview.setImageResource(R.drawable.ic_profile);
    }

    private void updateCustomProviderUi() {
        boolean customSelected = isCustomProviderSelected();
        boolean urlEditableSelected = isUrlEditableProviderSelected();
        customProviderContainer.setVisibility(urlEditableSelected ? View.VISIBLE : View.GONE);
        if (!urlEditableSelected) {
            if (providerCheckRunnable != null) {
                providerCheckHandler.removeCallbacks(providerCheckRunnable);
            }
            providerDiagnosticsVersion++;
            providerCheckRunning = false;
            checkProviderConnection.setEnabled(true);
            hideDiagnostics();
        }
        if (urlEditableSelected) {
            providerSelector.setValue(resolveProviderSelectionLabel());
            suppressCustomProviderWatcher = true;
            customProviderUrl.setText(selectedProviderUrl != null ? selectedProviderUrl : "");
            suppressCustomProviderWatcher = false;
            checkProviderConnection.setEnabled(!providerCheckRunning);
            updateProviderStatusLabel();
            if (!customSelected) {
                hideDiagnostics();
            } else {
                providerDiagnosticsContainer.setVisibility(View.GONE);
            }
        }
    }

    private boolean isCustomProviderSelected() {
        return OPTION_CUSTOM_PROVIDER.equals(selectedProviderType);
    }

    private boolean isUrlEditableProviderSelected() {
        return isUrlEditableProvider(selectedProviderType);
    }

    private boolean isUrlEditableProvider(String providerType) {
        return OPTION_CUSTOM_PROVIDER.equals(providerType)
                || PROVIDER_LM_STUDIO.equals(providerType)
                || "Ollama".equals(providerType);
    }

    private void rememberPendingProviderUrl() {
        if (!isUrlEditableProviderSelected()) {
            return;
        }
        pendingProviderUrls.put(selectedProviderType, normalizeProviderUrl(customProviderUrl.getText().toString()));
    }

    private void scheduleProviderCheck() {
        if (!isUrlEditableProviderSelected()) {
            return;
        }
        if (providerCheckRunnable != null) {
            providerCheckHandler.removeCallbacks(providerCheckRunnable);
        }
        providerCheckRunnable = new Runnable() {
            public void run() {
                checkProviderConnection(true);
            }
        };
        providerCheckHandler.postDelayed(providerCheckRunnable, 1500L);
    }

    private void checkProviderConnection(final boolean silent) {
        if (!isUrlEditableProviderSelected()) {
            return;
        }
        final String customUrl = normalizeProviderUrl(customProviderUrl.getText().toString());
        if (customUrl.length() == 0) {
            if (providerCheckRunnable != null) {
                providerCheckHandler.removeCallbacks(providerCheckRunnable);
            }
            providerStatus = ConfigManager.PROVIDER_STATUS_UNKNOWN;
            updateProviderStatusLabel();
            hideDiagnostics();
            return;
        }
        selectedProviderUrl = customUrl;
        providerCheckRunning = true;
        final int diagnosticsVersion = ++providerDiagnosticsVersion;
        checkProviderConnection.setEnabled(false);
        providerStatusLabel.setText(R.string.checking_connection);
        final String candidateApiKey = keyText.getText().toString().trim();
        showDiagnosticsPending();
        new Thread(new Runnable() {
            public void run() {
                boolean gatewayOk = false;
                boolean localOk = false;
                boolean internetOk = false;
                boolean providerOk = false;
                try {
                    String gatewayHost = resolveGatewayHost();
                    gatewayOk = gatewayHost != null && checkHostReachable(gatewayHost, 1500);
                    postDiagnosticStep(diagnosticsVersion, providerDiagnosticsGateway, R.string.diagnostics_gateway_label, gatewayOk);
                    if (!gatewayOk) {
                        finishProviderDiagnostics(diagnosticsVersion, customUrl, false);
                        return;
                    }

                    Uri providerUri = Uri.parse(customUrl);
                    String providerHost = providerUri.getHost();
                    int providerPort = resolvePort(providerUri);
                    localOk = providerHost != null && providerPort > 0 && checkSocketReachable(providerHost, providerPort, 2000);
                    postDiagnosticStep(diagnosticsVersion, providerDiagnosticsLocal, R.string.diagnostics_local_label, localOk);
                    if (!localOk) {
                        finishProviderDiagnostics(diagnosticsVersion, customUrl, false);
                        return;
                    }

                    internetOk = checkSocketReachable("example.com", 80, 2500);
                    postDiagnosticStep(diagnosticsVersion, providerDiagnosticsInternet, R.string.diagnostics_internet_label, internetOk);
                    checkProviderReachability(diagnosticsVersion, customUrl, candidateApiKey, gatewayOk, internetOk);
                } catch (Exception ignored) {
                    finishProviderDiagnostics(diagnosticsVersion, customUrl, false);
                }
            }
        }).start();
    }

    private void persistProviderCheckState(String customUrl) {
        config.setProviderType(selectedProviderType);
        config.setProviderUrl(customUrl);
        config.setProviderStatus(providerStatus);
        config.setProviderLastCheckTimestamp(providerLastCheckTimestamp);
    }

    private void restoreProviderCheckConfig(String originalBaseUrl, String originalApiKey) {
        Config conf = config.getConfig();
        conf.setBaseUrl(originalBaseUrl);
        conf.setApiKey(originalApiKey);
    }

    private void updateProviderStatusLabel() {
        if (!isUrlEditableProviderSelected()) {
            providerStatusLabel.setText("");
            return;
        }
        if (ConfigManager.PROVIDER_STATUS_OK.equals(providerStatus)) {
            providerStatusLabel.setText(R.string.provider_status_ok);
        } else if (ConfigManager.PROVIDER_STATUS_FAILED.equals(providerStatus)) {
            providerStatusLabel.setText(R.string.provider_status_failed);
        } else {
            providerStatusLabel.setText(R.string.provider_status_unknown);
        }
    }

    private void showDiagnosticsPending() {
        providerDiagnosticsContainer.setVisibility(View.VISIBLE);
        providerDiagnosticsHint.setVisibility(View.GONE);
        setDiagnosticText(providerDiagnosticsGateway, R.string.diagnostics_gateway_label, R.string.diagnostics_status_pending);
        setDiagnosticText(providerDiagnosticsLocal, R.string.diagnostics_local_label, R.string.diagnostics_status_pending);
        setDiagnosticText(providerDiagnosticsInternet, R.string.diagnostics_internet_label, R.string.diagnostics_status_pending);
        setDiagnosticText(providerDiagnosticsProvider, R.string.diagnostics_provider_label, R.string.diagnostics_status_pending);
    }

    private void hideDiagnostics() {
        providerDiagnosticsContainer.setVisibility(View.GONE);
        providerDiagnosticsHint.setVisibility(View.GONE);
    }

    private void setDiagnosticText(TextView view, int labelResId, int statusResId) {
        String label = getString(labelResId);
        String status = getString(statusResId);
        view.setText(label + "\t" + status);
    }

    private void postDiagnosticStep(final int diagnosticsVersion, final TextView view, final int labelResId, final boolean success) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (diagnosticsVersion != providerDiagnosticsVersion || !isUrlEditableProviderSelected()) {
                    return;
                }
                setDiagnosticText(view, labelResId, success ? R.string.diagnostics_status_success : R.string.diagnostics_status_failed);
            }
        });
    }

    private void postDiagnosticsHint(final int diagnosticsVersion, final boolean visible) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (diagnosticsVersion != providerDiagnosticsVersion || !isUrlEditableProviderSelected()) {
                    return;
                }
                providerDiagnosticsHint.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void checkProviderReachability(final int diagnosticsVersion, final String customUrl, final String apiKey,
                                           final boolean gatewayOk, final boolean internetOk) {
        fetchModelsForProvider(customUrl, apiKey, new ApiCallback<ArrayList<String>>() {
            public void onSuccess(ArrayList<String> result) {
                boolean providerOk = result != null && !result.isEmpty();
                postDiagnosticStep(diagnosticsVersion, providerDiagnosticsProvider, R.string.diagnostics_provider_label, providerOk);
                if (gatewayOk && internetOk && !providerOk) {
                    postDiagnosticsHint(diagnosticsVersion, true);
                }
                finishProviderDiagnostics(diagnosticsVersion, customUrl, providerOk);
            }

            public void onError(ApiError error) {
                boolean providerOk = isReachableProviderError(error.getMessage());
                postDiagnosticStep(diagnosticsVersion, providerDiagnosticsProvider, R.string.diagnostics_provider_label, providerOk);
                if (gatewayOk && internetOk && !providerOk) {
                    postDiagnosticsHint(diagnosticsVersion, true);
                }
                finishProviderDiagnostics(diagnosticsVersion, customUrl, providerOk);
            }
        });
    }

    private void finishProviderDiagnostics(final int diagnosticsVersion, final String customUrl, final boolean success) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (diagnosticsVersion != providerDiagnosticsVersion || !isUrlEditableProviderSelected()) {
                    return;
                }
                providerCheckRunning = false;
                checkProviderConnection.setEnabled(true);
                providerStatus = success ? ConfigManager.PROVIDER_STATUS_OK : ConfigManager.PROVIDER_STATUS_FAILED;
                providerLastCheckTimestamp = System.currentTimeMillis();
                persistProviderCheckState(customUrl);
                updateProviderStatusLabel();
            }
        });
    }

    private String resolveGatewayHost() {
        try {
            WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) {
                return null;
            }
            DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
            if (dhcpInfo == null || dhcpInfo.gateway == 0) {
                return null;
            }
            return intToIp(dhcpInfo.gateway);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String intToIp(int address) {
        return (address & 0xff) + "."
                + ((address >> 8) & 0xff) + "."
                + ((address >> 16) & 0xff) + "."
                + ((address >> 24) & 0xff);
    }

    private int resolvePort(Uri uri) {
        int port = uri.getPort();
        if (port > 0) {
            return port;
        }
        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        return 80;
    }

    private boolean checkHostReachable(String host, int timeoutMs) {
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isReachable(timeoutMs)) {
                return true;
            }
        } catch (Exception ignored) {
        }
        int[] fallbackPorts = new int[] { 53, 80, 443 };
        for (int i = 0; i < fallbackPorts.length; i++) {
            if (checkSocketReachable(host, fallbackPorts[i], timeoutMs)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkSocketReachable(String host, int port, int timeoutMs) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private boolean checkProviderEndpoint(String customUrl, String apiKey) {
        HttpURLConnection connection = null;
        try {
            URL endpointUrl = new URL(buildModelsEndpoint(customUrl));
            connection = (HttpURLConnection) endpointUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("Accept", "application/json");
            if (apiKey != null && apiKey.length() != 0) {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            int code = connection.getResponseCode();
            return (code >= 200 && code < 300) || code == 401 || code == 403;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String buildModelsEndpoint(String customUrl) {
        String normalized = normalizeProviderUrl(customUrl);
        if (normalized.endsWith("/v1")) {
            return normalized + "/models";
        }
        if (normalized.endsWith("/v1beta/openai")) {
            return normalized + "/models";
        }
        return normalized + "/v1/models";
    }

    private boolean isReachableProviderError(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }
        String normalized = errorMessage.toLowerCase(Locale.US);
        return normalized.indexOf("401") != -1
                || normalized.indexOf("403") != -1
                || normalized.indexOf("unauthorized") != -1
                || normalized.indexOf("forbidden") != -1
                || normalized.indexOf("invalid api") != -1;
    }

    private void importApiKey(Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            keyText.setText(new Scanner(is, "UTF-8").useDelimiter("\\A").next());
            Toast.makeText(this, R.string.key_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startAvatarCrop(Uri sourceUri) {
        if (sourceUri == null) {
            return;
        }
        Intent intent = new Intent(this, AvatarCropActivity.class);
        intent.putExtra("source_uri", sourceUri.toString());
        startActivityForResult(intent, REQUEST_CODE_CROP_AVATAR);
    }

    private void saveSettings() {
        final Config conf = config.getConfig();
        final String apiKey = keyText.getText().toString().trim();
        final boolean languageChanged = !LocaleHelper.normalizeLanguage(config.getAppLanguage()).equals(LocaleHelper.normalizeLanguage(defaultSystemToEmpty(selectedLanguage)));
        final String providerUrl = resolveSelectedProviderUrl(conf.getBaseUrl());
        ArrayList<String> cachedProviderModels = config.getCachedModels(providerUrl);
        final String thinkingModel = resolveSafeThinkingModel(providerUrl, selectedThinkingModel != null ? selectedThinkingModel : resolveProviderThinkingModel(providerUrl, conf.getThinkingModel()), cachedProviderModels);
        final String providerChatModel = resolveProviderChatModel(providerUrl, providerUrl.equals(conf.getBaseUrl()) ? conf.getChatModel() : "", cachedProviderModels);

        config.setAppLanguage(defaultSystemToEmpty(selectedLanguage));
        config.setNickname(nickname.getText().toString().trim());
        config.setAvatarPath(pendingAvatarPath != null ? pendingAvatarPath : "");
        config.setUserName(userName.getText().toString().trim());
        config.setUserRole(userRole.getText().toString().trim());
        config.setUsageGoal(usageGoal.getText().toString().trim());
        config.setResponseStyle(defaultToEmpty(selectedResponseStyle));
        config.setResponseDetailLevel(defaultToEmpty(selectedResponseDetailLevel));
        config.setResponseEmotionality(defaultToEmpty(selectedResponseEmotionality));
        config.setCustomSystemPrompt(customSystemPrompt.getText().toString().trim());
        config.setProviderType(selectedProviderType != null ? selectedProviderType : "");
        config.setProviderUrl(providerUrl);
        config.setProviderUrlForType(selectedProviderType, providerUrl);
        config.setProviderStreamingMode(providerUrl, selectedStreamingMode);
        config.setProviderStatus(providerStatus);
        config.setProviderLastCheckTimestamp(providerLastCheckTimestamp);
        config.setProviderChatModel(providerUrl, providerChatModel);
        config.setProviderThinkingModel(providerUrl, thinkingModel);

        if (providerUrl.equals(conf.getBaseUrl())) {
            config.setConfig(new Config(providerUrl, apiKey, providerChatModel, thinkingModel, conf.getShrinkThink(), conf.getSystemPrompt(), conf.getUpdateDelay()));
            setResult(languageChanged ? RESULT_OK : RESULT_CANCELED, buildSettingsResult(languageChanged));
            finish();
            return;
        }

        final Loading loading = new Loading(context);
        fetchModelsForProvider(providerUrl, apiKey, new ApiCallback<ArrayList<String>>() {
            public void onSuccess(ArrayList<String> models) {
                config.setCachedModels(providerUrl, models);
                String currentChatModel = resolveProviderChatModel(providerUrl, providerChatModel, models);
                if ((currentChatModel == null || currentChatModel.length() == 0) && !models.isEmpty()) {
                    currentChatModel = ModelSelector.selectChatModel(models);
                }
                String currentThinkingModel = thinkingModel;
                if ((currentThinkingModel == null || currentThinkingModel.length() == 0 || !models.contains(currentThinkingModel)) && !models.isEmpty()) {
                    currentThinkingModel = ModelSelector.selectThinkingModel(models);
                }
                config.setProviderChatModel(providerUrl, currentChatModel);
                config.setProviderThinkingModel(providerUrl, currentThinkingModel);
                config.setConfig(new Config(providerUrl, apiKey, currentChatModel, currentThinkingModel, conf.getShrinkThink(), conf.getSystemPrompt(), conf.getUpdateDelay()));
                loading.dismiss();
                setResult(languageChanged ? RESULT_OK : RESULT_CANCELED, buildSettingsResult(languageChanged));
                finish();
            }

            public void onError(ApiError error) {
                loading.dismiss();
                String fallbackChatModel = resolveProviderChatModel(providerUrl, providerChatModel, config.getCachedModels(providerUrl));
                String fallbackThinkingModel = resolveProviderThinkingModel(providerUrl, thinkingModel);
                config.setConfig(new Config(providerUrl, apiKey, fallbackChatModel, fallbackThinkingModel, conf.getShrinkThink(), conf.getSystemPrompt(), conf.getUpdateDelay()));
                Toast.makeText(context, error.getMessage(), Toast.LENGTH_LONG).show();
                setResult(languageChanged ? RESULT_OK : RESULT_CANCELED, buildSettingsResult(languageChanged));
                finish();
            }
        });
    }

    private ArrayList<SelectionOption> buildStyleOptions() {
        ArrayList<SelectionOption> options = new ArrayList<SelectionOption>();
        options.add(new SelectionOption(getString(R.string.option_default), OPTION_DEFAULT));
        options.add(new SelectionOption(getString(R.string.option_short), "short"));
        options.add(new SelectionOption(getString(R.string.option_conversational), "conversational"));
        options.add(new SelectionOption(getString(R.string.option_technical), "technical"));
        options.add(new SelectionOption(getString(R.string.option_detailed), "detailed"));
        return options;
    }

    private ArrayList<SelectionOption> buildStreamingModeOptions() {
        ArrayList<SelectionOption> options = new ArrayList<SelectionOption>();
        options.add(new SelectionOption(getString(R.string.streaming_mode_auto), ConfigManager.STREAMING_MODE_AUTO));
        options.add(new SelectionOption(getString(R.string.streaming_mode_on), ConfigManager.STREAMING_MODE_ON));
        options.add(new SelectionOption(getString(R.string.streaming_mode_off), ConfigManager.STREAMING_MODE_OFF));
        return options;
    }

    private ArrayList<SelectionOption> buildDetailOptions() {
        ArrayList<SelectionOption> options = new ArrayList<SelectionOption>();
        options.add(new SelectionOption(getString(R.string.option_default), OPTION_DEFAULT));
        options.add(new SelectionOption(getString(R.string.option_brief), "brief"));
        options.add(new SelectionOption(getString(R.string.option_medium), "medium"));
        options.add(new SelectionOption(getString(R.string.option_detailed), "detailed"));
        options.add(new SelectionOption(getString(R.string.option_very_detailed), "very_detailed"));
        return options;
    }

    private ArrayList<SelectionOption> buildEmotionalityOptions() {
        ArrayList<SelectionOption> options = new ArrayList<SelectionOption>();
        options.add(new SelectionOption(getString(R.string.option_default), OPTION_DEFAULT));
        options.add(new SelectionOption(getString(R.string.option_no_emotion), "flat"));
        options.add(new SelectionOption(getString(R.string.option_neutral), "neutral"));
        options.add(new SelectionOption(getString(R.string.option_moderate), "moderate"));
        options.add(new SelectionOption(getString(R.string.option_expressive), "expressive"));
        return options;
    }

    private ArrayList<SelectionOption> buildLanguageOptions() {
        ArrayList<SelectionOption> options = new ArrayList<SelectionOption>();
        options.add(new SelectionOption(getString(R.string.language_system_default), OPTION_SYSTEM));
        options.add(new SelectionOption(getString(R.string.language_english), "en"));
        options.add(new SelectionOption(getString(R.string.language_russian), "ru"));
        return options;
    }

    private String findOptionLabel(List<SelectionOption> options, String value) {
        for (int i = 0; i < options.size(); i++) {
            SelectionOption option = options.get(i);
            if (option.value.equals(value)) {
                return option.label;
            }
        }
        return options.isEmpty() ? "" : options.get(0).label;
    }

    private String resolveProviderLabel(String url) {
        String label = ApiManager.getNameByUrl(url);
        return label != null ? label : url;
    }

    private String emptyToDefault(String value) {
        return value == null || value.trim().length() == 0 ? OPTION_DEFAULT : value;
    }

    private String defaultToEmpty(String value) {
        return OPTION_DEFAULT.equals(value) ? "" : value;
    }

    private String emptyToSystem(String value) {
        return value == null || value.trim().length() == 0 ? OPTION_SYSTEM : value;
    }

    private String defaultSystemToEmpty(String value) {
        return OPTION_SYSTEM.equals(value) ? "" : value;
    }

    private String resolveProviderType(String baseUrl) {
        String providerName = ApiManager.getNameByUrl(baseUrl);
        return providerName != null ? providerName : OPTION_CUSTOM_PROVIDER;
    }

    private String resolveProviderSelectionLabel() {
        return isCustomProviderSelected()
                ? getString(R.string.custom_provider)
                : (selectedProviderType != null && selectedProviderType.length() != 0
                ? selectedProviderType
                : resolveProviderLabel(selectedProviderUrl));
    }

    private String normalizeProviderUrl(String url) {
        String normalized = url != null ? url.trim() : "";
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (("LM Studio".equals(selectedProviderType) || "Ollama".equals(selectedProviderType)) && normalized.length() != 0) {
            String lower = normalized.toLowerCase(Locale.US);
            if (lower.endsWith("/chat/completions")) {
                normalized = normalized.substring(0, normalized.length() - "/chat/completions".length());
                lower = normalized.toLowerCase(Locale.US);
            }
            if (lower.endsWith("/models")) {
                normalized = normalized.substring(0, normalized.length() - "/models".length());
                lower = normalized.toLowerCase(Locale.US);
            }
            if (!lower.endsWith("/v1")) {
                normalized = normalized + "/v1";
            }
        }
        return normalized;
    }

    private String resolveSelectedProviderUrl(String fallbackUrl) {
        if (isUrlEditableProviderSelected()) {
            String typedUrl = normalizeProviderUrl(customProviderUrl.getText().toString());
            return typedUrl.length() != 0 ? typedUrl : fallbackUrl;
        }
        if (selectedProviderType != null && selectedProviderType.length() != 0) {
            return ApiManager.getUrlByName(selectedProviderType);
        }
        return selectedProviderUrl != null ? selectedProviderUrl : fallbackUrl;
    }

    private String resolveProviderUrlForSelection() {
        return resolveSelectedProviderUrl(config.getConfig().getBaseUrl());
    }

    private String resolveProviderThinkingModel(String providerUrl, String fallback) {
        String model = config.getProviderThinkingModel(providerUrl);
        if (model == null || model.length() == 0) {
            model = fallback;
        }
        return model != null ? model : "";
    }

    private String resolveSafeThinkingModel(String providerUrl, String candidate, List<String> availableModels) {
        String model = candidate;
        if (model == null || model.length() == 0) {
            model = resolveProviderThinkingModel(providerUrl, "");
        }
        if (availableModels != null && !availableModels.isEmpty() && (model == null || model.length() == 0 || !availableModels.contains(model))) {
            model = ModelSelector.selectThinkingModel(availableModels);
        }
        return model != null ? model : "";
    }

    private String resolveProviderChatModel(String providerUrl, String fallback, List<String> availableModels) {
        String model = config.getProviderChatModel(providerUrl);
        if (model == null || model.length() == 0) {
            model = fallback;
        }
        if (availableModels != null && !availableModels.isEmpty() && (model == null || model.length() == 0 || !availableModels.contains(model))) {
            model = ModelSelector.selectChatModel(availableModels);
        }
        return model != null ? model : "";
    }

    private void fetchModelsForProvider(String providerUrl, String apiKey, final ApiCallback<ArrayList<String>> callback) {
        final Config conf = config.getConfig();
        final String originalBaseUrl = conf.getBaseUrl();
        final String originalApiKey = conf.getApiKey();
        conf.setBaseUrl(providerUrl);
        conf.setApiKey(apiKey);
        api.getModels(new ApiCallback<ArrayList<String>>() {
            public void onSuccess(ArrayList<String> result) {
                restoreProviderCheckConfig(originalBaseUrl, originalApiKey);
                callback.onSuccess(result != null ? result : new ArrayList<String>());
            }

            public void onError(ApiError error) {
                restoreProviderCheckConfig(originalBaseUrl, originalApiKey);
                callback.onError(error);
            }
        });
    }

    private Intent buildSettingsResult(boolean languageChanged) {
        Intent result = new Intent();
        result.putExtra("language_changed", languageChanged);
        return result;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private class SectionClickListener implements View.OnClickListener {
        private final int section;

        SectionClickListener(int section) {
            this.section = section;
        }

        public void onClick(View view) {
            selectSection(section);
        }
    }

    private interface OptionSelectionListener {
        void onSelected(SelectionOption option);
    }

    private static class SelectionOption {
        final String label;
        final String value;

        SelectionOption(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    private class SelectorPopupAdapter extends BaseAdapter {
        private final List<SelectionOption> options;
        private final String selectedValue;
        private final LayoutInflater inflater = LayoutInflater.from(SettingsActivity.this);

        SelectorPopupAdapter(List<SelectionOption> options, String selectedValue) {
            this.options = options;
            this.selectedValue = selectedValue;
        }

        public int getCount() {
            return options.size();
        }

        public Object getItem(int position) {
            return options.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = inflater.inflate(R.layout.spinner_toolbar_dropdown_item, parent, false);
            }
            TextView title = (TextView) view.findViewById(android.R.id.text1);
            ImageView check = (ImageView) view.findViewById(R.id.check_icon);
            SelectionOption option = options.get(position);
            title.setText(option.label);
            check.setVisibility(option.value.equals(selectedValue) ? View.VISIBLE : View.INVISIBLE);
            return view;
        }
    }

    private static class SelectorRow {
        final View root;
        final TextView title;
        final TextView value;

        SelectorRow(View root) {
            this.root = root;
            this.title = (TextView) root.findViewById(R.id.setting_selector_title);
            this.value = (TextView) root.findViewById(R.id.setting_selector_value);
        }

        void setTitle(String title) {
            this.title.setText(title);
        }

        void setValue(String value) {
            this.value.setText(value);
        }
    }
}
