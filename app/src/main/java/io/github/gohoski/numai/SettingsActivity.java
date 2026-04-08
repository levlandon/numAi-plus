package io.github.gohoski.numai;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MotionEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * Created by Gleb on 15.10.2025.
 */

public class SettingsActivity extends AppCompatActivity {
    Context context;
    ConfigManager config;
    ApiService api;
    Spinner apiSpinner, thinkSpinner;
    EditText keyText;
    boolean fetched = false;
    String systemPrompt;
    EditText updateDelay;
    TextView selectedModelsSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        context = this;
        config = ConfigManager.getInstance();
        final Config conf = config.getConfig();
        api = new ApiService(this);
        systemPrompt = conf.getSystemPrompt();

        findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                navigateToMain();
            }
        });

        apiSpinner = (Spinner) findViewById(R.id.api_spinner);
        SettingsHelper.setupApiSpinner(context, apiSpinner, config, new SettingsHelper.ApiSelectionCallback() {
            @Override
            public void onApiSelected(String api) {
                fetched = false;
                System.out.println(api);
            }
        });

        keyText = (EditText) findViewById(R.id.apiKey);
        keyText.setText(conf.getApiKey());

        thinkSpinner = (Spinner) findViewById(R.id.think_spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{conf.getThinkingModel()}
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        thinkSpinner.setAdapter(adapter);
        selectedModelsSummary = (TextView) findViewById(R.id.selected_models_summary);
        applyCachedThinkingModels();
        updateSelectedModelsSummary();

        thinkSpinner.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    loadModels(thinkSpinner);
                    return true;
                }
                return false;
            }
        });
        findViewById(R.id.selected_models_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(context, ModelVisibilityActivity.class));
            }
        });
        updateDelay = (EditText) findViewById(R.id.update_delay);
        updateDelay.setText(conf.getUpdateDelay()+"");

        findViewById(R.id.ok).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final String urlByName = ApiManager.getUrlByName(apiSpinner.getSelectedItem().toString());
                if (conf.getBaseUrl().equals(urlByName)) {
                    config.setConfig(new Config(urlByName,
                            keyText.getText().toString(), conf.getChatModel(),
                            thinkSpinner.getSelectedItem().toString(),
                            conf.getShrinkThink(), systemPrompt, Integer.parseInt(updateDelay.getText().toString())));
                    Intent intent = new Intent(context, MainActivity.class);
                    startActivity(intent);
                    finish();
                } else if (!fetched) {
                    final Loading loading = new Loading(context);
                    final String orig = config.getConfig().getBaseUrl();
                    config.updateBaseUrl(urlByName);
                    api.getModels(new ApiCallback<ArrayList<String>>() {
                        @Override
                        public void onSuccess(ArrayList<String> models) {
                            config.setCachedModels(models);
                            String currentChatModel = conf.getChatModel();
                            if (currentChatModel == null || currentChatModel.length() == 0 || !models.contains(currentChatModel)) {
                                currentChatModel = ModelSelector.selectChatModel(models);
                            }
                            config.setConfig(new Config(urlByName,
                                    keyText.getText().toString(), currentChatModel,
                                    ModelSelector.selectThinkingModel(models),
                                    conf.getShrinkThink(), systemPrompt, Integer.parseInt(updateDelay.getText().toString())));
                            loading.dismiss();
                            Intent intent = new Intent(context, MainActivity.class);
                            startActivity(intent);
                            finish();
                        }

                        @Override
                        public void onError(ApiError error) {
                            error.printStackTrace();
                            Toast.makeText(context, error.getMessage(), Toast.LENGTH_LONG).show();loading.dismiss();
                            config.updateBaseUrl(orig);
                        }
                    });
                }
            }
        });

        findViewById(R.id.changeSystemPrompt).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final EditText edittext = new EditText(context);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.FILL_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(20, 10, 20, 10);
                edittext.setLayoutParams(params);
                edittext.setSingleLine(false);
                edittext.setMinLines(4);
                edittext.setTextSize(14);
                edittext.setPadding(10, 10, 10, 10);
                edittext.setText(systemPrompt);
                new AlertDialog.Builder(context)
                    .setTitle(R.string.change_system)
                    .setView(edittext)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            systemPrompt = edittext.getText().toString();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            }
        });

        findViewById(R.id.from_file).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("text/plain");
                startActivityForResult(Intent.createChooser(intent, getString(R.string.select_txt)), 2);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyCachedThinkingModels();
        updateSelectedModelsSummary();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            navigateToMain();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void navigateToMain() {
        Intent intent = new Intent(context, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 2 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    keyText.setText(new Scanner(is, "UTF-8").useDelimiter("\\A").next());
                    Toast.makeText(this, R.string.key_success, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void loadModels(final Spinner spinner) {
//        if (!keyText.getText().toString().equals(config.getConfig().getApiKey())) {
//            Toast.makeText(this, R.string.change_key_pls, Toast.LENGTH_LONG).show();
//            return;
//        }
        if (fetched) {
            spinner.performClick();
            return;
        }
        final Loading loading = new Loading(context);
        api.getModels(new ApiCallback<ArrayList<String>>() {
            @Override
            public void onSuccess(ArrayList<String> result) {
                config.setCachedModels(result);
                applyThinkingModels(result);
                loading.dismiss();
                spinner.performClick();
            }

            @Override
            public void onError(ApiError error) {
                error.printStackTrace();
                loading.dismiss();
                Toast.makeText(context, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyCachedThinkingModels() {
        ArrayList<String> cachedModels = config.getCachedModels();
        if (!cachedModels.isEmpty()) {
            applyThinkingModels(cachedModels);
        }
    }

    private void applyThinkingModels(ArrayList<String> models) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                context,
                android.R.layout.simple_spinner_item,
                models
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        thinkSpinner.setAdapter(adapter);
        Config conf = config.getConfig();
        int selectedIndex = models.indexOf(conf.getThinkingModel());
        thinkSpinner.setSelection(selectedIndex >= 0 ? selectedIndex : 0);
        fetched = !models.isEmpty();
    }

    private void updateSelectedModelsSummary() {
        if (selectedModelsSummary == null) return;
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
}
