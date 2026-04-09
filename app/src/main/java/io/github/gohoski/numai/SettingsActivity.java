package io.github.gohoski.numai;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ListPopupWindow;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class SettingsActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_IMPORT_KEY = 2;
    private static final int REQUEST_CODE_PICK_AVATAR = 3;
    private static final int REQUEST_CODE_CROP_AVATAR = 4;

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
    private View sectionModels;
    private View sectionProvider;
    private Button tabProfile;
    private Button tabPersonalization;
    private Button tabModels;
    private Button tabProvider;

    private SelectorRow providerSelector;
    private SelectorRow thinkingModelSelector;
    private SelectorRow responseStyleSelector;
    private SelectorRow responseDetailSelector;
    private SelectorRow responseEmotionalitySelector;

    private final ArrayList<String> cachedThinkingModels = new ArrayList<String>();
    private boolean fetchedThinkingModels = false;

    private String pendingAvatarPath;
    private String selectedProviderUrl;
    private String selectedThinkingModel;
    private String selectedResponseStyle;
    private String selectedResponseDetailLevel;
    private String selectedResponseEmotionality;

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

        sectionProfile = findViewById(R.id.section_profile);
        sectionPersonalization = findViewById(R.id.section_personalization);
        sectionModels = findViewById(R.id.section_models);
        sectionProvider = findViewById(R.id.section_provider);

        tabProfile = (Button) findViewById(R.id.tab_profile);
        tabPersonalization = (Button) findViewById(R.id.tab_personalization);
        tabModels = (Button) findViewById(R.id.tab_models);
        tabProvider = (Button) findViewById(R.id.tab_provider);

        providerSelector = new SelectorRow(findViewById(R.id.provider_selector));
        thinkingModelSelector = new SelectorRow(findViewById(R.id.thinking_model_selector));
        responseStyleSelector = new SelectorRow(findViewById(R.id.response_style_selector));
        responseDetailSelector = new SelectorRow(findViewById(R.id.response_detail_selector));
        responseEmotionalitySelector = new SelectorRow(findViewById(R.id.response_emotionality_selector));
    }

    private void setupTabs() {
        tabProfile.setOnClickListener(new SectionClickListener(0));
        tabPersonalization.setOnClickListener(new SectionClickListener(1));
        tabModels.setOnClickListener(new SectionClickListener(2));
        tabProvider.setOnClickListener(new SectionClickListener(3));
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
                startActivity(new Intent(context, ModelVisibilityActivity.class));
            }
        });
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
        selectedThinkingModel = conf.getThinkingModel();
        selectedResponseStyle = emptyToDefault(config.getResponseStyle());
        selectedResponseDetailLevel = emptyToDefault(config.getResponseDetailLevel());
        selectedResponseEmotionality = emptyToDefault(config.getResponseEmotionality());

        providerSelector.setValue(resolveProviderLabel(selectedProviderUrl));
        thinkingModelSelector.setValue(selectedThinkingModel != null && selectedThinkingModel.length() != 0
                ? selectedThinkingModel
                : getString(R.string.select_model));
        responseStyleSelector.setValue(findOptionLabel(buildStyleOptions(), selectedResponseStyle));
        responseDetailSelector.setValue(findOptionLabel(buildDetailOptions(), selectedResponseDetailLevel));
        responseEmotionalitySelector.setValue(findOptionLabel(buildEmotionalityOptions(), selectedResponseEmotionality));
        renderAvatar();
        applyCachedThinkingModels();
        updateSelectedModelsSummary();
    }

    private void selectSection(int section) {
        sectionProfile.setVisibility(section == 0 ? View.VISIBLE : View.GONE);
        sectionPersonalization.setVisibility(section == 1 ? View.VISIBLE : View.GONE);
        sectionModels.setVisibility(section == 2 ? View.VISIBLE : View.GONE);
        sectionProvider.setVisibility(section == 3 ? View.VISIBLE : View.GONE);

        updateTab(tabProfile, section == 0);
        updateTab(tabPersonalization, section == 1);
        updateTab(tabModels, section == 2);
        updateTab(tabProvider, section == 3);
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
            options.add(new SelectionOption(name, ApiManager.getUrlByName(name)));
        }
        options.add(new SelectionOption(getString(R.string.other), getString(R.string.other)));
        showSingleOptionSelector(providerSelector.root, options, selectedProviderUrl, new OptionSelectionListener() {
            public void onSelected(SelectionOption option) {
                if (getString(R.string.other).equals(option.value)) {
                    showCustomProviderDialog();
                    return;
                }
                selectedProviderUrl = option.value;
                providerSelector.setValue(option.label);
                fetchedThinkingModels = false;
            }
        });
    }

    private void showCustomProviderDialog() {
        final EditText editText = new EditText(this);
        editText.setSingleLine();
        new AlertDialog.Builder(this)
                .setTitle(R.string.other)
                .setMessage(R.string.base_url)
                .setView(editText)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String customUrl = editText.getText().toString().trim();
                        if (customUrl.length() == 0) {
                            return;
                        }
                        if (customUrl.endsWith("/")) {
                            customUrl = customUrl.substring(0, customUrl.length() - 1);
                        }
                        selectedProviderUrl = customUrl;
                        providerSelector.setValue(customUrl);
                        fetchedThinkingModels = false;
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void loadThinkingModelsAndShow() {
        if (fetchedThinkingModels && !cachedThinkingModels.isEmpty()) {
            showThinkingModelSelector();
            return;
        }
        final Loading loading = new Loading(context);
        api.getModels(new ApiCallback<ArrayList<String>>() {
            public void onSuccess(ArrayList<String> result) {
                cachedThinkingModels.clear();
                cachedThinkingModels.addAll(result);
                config.setCachedModels(result);
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
        cachedThinkingModels.addAll(config.getCachedModels());
        fetchedThinkingModels = !cachedThinkingModels.isEmpty();
        if ((selectedThinkingModel == null || selectedThinkingModel.length() == 0) && !cachedThinkingModels.isEmpty()) {
            selectedThinkingModel = cachedThinkingModels.get(0);
            thinkingModelSelector.setValue(selectedThinkingModel);
        }
    }

    private void updateSelectedModelsSummary() {
        if (config.getShowAllModels()) {
            selectedModelsSummary.setText(R.string.selected_models_all_summary);
            return;
        }
        int count = config.getSelectedChatModels().size();
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
        final String providerUrl = selectedProviderUrl != null ? selectedProviderUrl : conf.getBaseUrl();
        final String thinkingModel = selectedThinkingModel != null ? selectedThinkingModel : conf.getThinkingModel();

        config.setNickname(nickname.getText().toString().trim());
        config.setAvatarPath(pendingAvatarPath != null ? pendingAvatarPath : "");
        config.setUserName(userName.getText().toString().trim());
        config.setUserRole(userRole.getText().toString().trim());
        config.setUsageGoal(usageGoal.getText().toString().trim());
        config.setResponseStyle(defaultToEmpty(selectedResponseStyle));
        config.setResponseDetailLevel(defaultToEmpty(selectedResponseDetailLevel));
        config.setResponseEmotionality(defaultToEmpty(selectedResponseEmotionality));
        config.setCustomSystemPrompt(customSystemPrompt.getText().toString().trim());

        if (providerUrl.equals(conf.getBaseUrl())) {
            config.setConfig(new Config(providerUrl, apiKey, conf.getChatModel(), thinkingModel, conf.getShrinkThink(), conf.getSystemPrompt(), conf.getUpdateDelay()));
            finish();
            return;
        }

        final Loading loading = new Loading(context);
        final String originalBaseUrl = conf.getBaseUrl();
        config.updateBaseUrl(providerUrl);
        api.getModels(new ApiCallback<ArrayList<String>>() {
            public void onSuccess(ArrayList<String> models) {
                config.setCachedModels(models);
                String currentChatModel = conf.getChatModel();
                if (currentChatModel == null || currentChatModel.length() == 0 || !models.contains(currentChatModel)) {
                    currentChatModel = ModelSelector.selectChatModel(models);
                }
                String currentThinkingModel = thinkingModel;
                if (currentThinkingModel == null || currentThinkingModel.length() == 0 || !models.contains(currentThinkingModel)) {
                    currentThinkingModel = ModelSelector.selectThinkingModel(models);
                }
                config.setConfig(new Config(providerUrl, apiKey, currentChatModel, currentThinkingModel, conf.getShrinkThink(), conf.getSystemPrompt(), conf.getUpdateDelay()));
                loading.dismiss();
                finish();
            }

            public void onError(ApiError error) {
                loading.dismiss();
                config.updateBaseUrl(originalBaseUrl);
                Toast.makeText(context, error.getMessage(), Toast.LENGTH_LONG).show();
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
