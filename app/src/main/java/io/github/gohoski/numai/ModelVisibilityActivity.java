package io.github.gohoski.numai;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class ModelVisibilityActivity extends AppCompatActivity {
    private Context context;
    private ConfigManager config;
    private ApiService api;
    private String providerUrl;
    private String providerType;
    private String apiKey;
    private ListView modelsList;
    private final ArrayList<String> models = new ArrayList<String>();
    private final ArrayList<String> selectedModels = new ArrayList<String>();
    private boolean showAllModels;
    private ModelVisibilityAdapter adapter;
    private View refreshButton;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_visibility);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        context = this;
        config = ConfigManager.getInstance(this);
        api = new ApiService(this);
        providerUrl = getIntent().getStringExtra("provider_url");
        providerType = getIntent().getStringExtra("provider_type");
        apiKey = getIntent().getStringExtra("api_key");
        if (providerUrl == null || providerUrl.length() == 0) {
            providerUrl = config.getConfig().getBaseUrl();
        }
        if (providerType == null || providerType.length() == 0) {
            providerType = config.getProviderType();
        }
        if (apiKey == null) {
            apiKey = config.getConfig().getApiKey();
        }
        selectedModels.addAll(config.getSelectedChatModels(providerUrl));
        showAllModels = config.getShowAllModels(providerUrl);

        modelsList = (ListView) findViewById(R.id.models_list);
        refreshButton = findViewById(R.id.refresh_models_button);
        adapter = new ModelVisibilityAdapter();
        modelsList.setAdapter(adapter);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshModels(true);
            }
        });
        modelsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    showAllModels = !showAllModels;
                    config.setShowAllModels(providerUrl, showAllModels);
                    adapter.notifyDataSetChanged();
                    return;
                }

                String model = models.get(position - 1);
                if (selectedModels.contains(model)) {
                    selectedModels.remove(model);
                } else {
                    selectedModels.add(model);
                }
                config.setSelectedChatModels(providerUrl, selectedModels);
                adapter.notifyDataSetChanged();
            }
        });

        restoreCachedModels();
        if (models.isEmpty()) {
            refreshModels(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void restoreCachedModels() {
        models.clear();
        models.addAll(config.getCachedModels(providerUrl));
        trimInvalidSelections();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void refreshModels(boolean showLoading) {
        final Loading loading = showLoading ? new Loading(context) : null;
        if (refreshButton != null) {
            refreshButton.setEnabled(false);
        }
        fetchModelsForProvider(providerUrl, apiKey, new ApiCallback<ArrayList<String>>() {
            @Override
            public void onSuccess(ArrayList<String> result) {
                config.setCachedModels(providerUrl, result);
                models.clear();
                models.addAll(result);
                trimInvalidSelections();
                adapter.notifyDataSetChanged();
                if (loading != null) {
                    loading.dismiss();
                }
                if (refreshButton != null) {
                    refreshButton.setEnabled(true);
                }
            }

            @Override
            public void onError(ApiError error) {
                models.clear();
                trimInvalidSelections();
                adapter.notifyDataSetChanged();
                if (loading != null) {
                    loading.dismiss();
                }
                if (refreshButton != null) {
                    refreshButton.setEnabled(true);
                }
                Toast.makeText(context, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void trimInvalidSelections() {
        if (models.isEmpty()) {
            return;
        }
        for (int i = selectedModels.size() - 1; i >= 0; i--) {
            if (!models.contains(selectedModels.get(i))) {
                selectedModels.remove(i);
            }
        }
        config.setSelectedChatModels(providerUrl, selectedModels);
    }

    private void fetchModelsForProvider(String providerUrl, String apiKey, final ApiCallback<ArrayList<String>> callback) {
        final Config conf = config.getConfig();
        final String originalBaseUrl = conf.getBaseUrl();
        final String originalApiKey = conf.getApiKey();
        conf.setBaseUrl(providerUrl);
        conf.setApiKey(apiKey);
        api.getModels(new ApiCallback<ArrayList<String>>() {
            @Override
            public void onSuccess(ArrayList<String> result) {
                conf.setBaseUrl(originalBaseUrl);
                conf.setApiKey(originalApiKey);
                callback.onSuccess(result != null ? result : new ArrayList<String>());
            }

            @Override
            public void onError(ApiError error) {
                conf.setBaseUrl(originalBaseUrl);
                conf.setApiKey(originalApiKey);
                callback.onError(error);
            }
        });
    }

    private class ModelVisibilityAdapter extends BaseAdapter {
        public int getCount() {
            return models.size() + 1;
        }

        public Object getItem(int position) {
            return position == 0 ? getString(R.string.show_all_models) : models.get(position - 1);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.item_model_visibility, parent, false);
            }

            TextView modelName = (TextView) view.findViewById(R.id.model_name);
            ImageView check = (ImageView) view.findViewById(R.id.model_check);
            if (position == 0) {
                modelName.setText(R.string.show_all_models);
                check.setVisibility(showAllModels ? View.VISIBLE : View.INVISIBLE);
            } else {
                String model = models.get(position - 1);
                modelName.setText(model);
                check.setVisibility(selectedModels.contains(model) ? View.VISIBLE : View.INVISIBLE);
            }
            return view;
        }
    }
}
