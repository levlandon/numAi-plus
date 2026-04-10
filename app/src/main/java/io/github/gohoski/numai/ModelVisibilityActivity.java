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
        selectedModels.addAll(config.getSelectedChatModels());
        showAllModels = config.getShowAllModels();

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
                    config.setShowAllModels(showAllModels);
                    adapter.notifyDataSetChanged();
                    return;
                }

                String model = models.get(position - 1);
                if (selectedModels.contains(model)) {
                    selectedModels.remove(model);
                } else {
                    selectedModels.add(model);
                }
                config.setSelectedChatModels(selectedModels);
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
        models.addAll(config.getCachedModels());
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void refreshModels(boolean showLoading) {
        final Loading loading = showLoading ? new Loading(context) : null;
        if (refreshButton != null) {
            refreshButton.setEnabled(false);
        }
        api.getModels(new ApiCallback<ArrayList<String>>() {
            @Override
            public void onSuccess(ArrayList<String> result) {
                config.setCachedModels(result);
                models.clear();
                models.addAll(result);
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
