package org.openwsgr.autowsgrmobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ConfigProfileUiController {
    private static final String STATE_PREFIX = "configProfileEditor.";
    private static final String STATE_SELECTED_ID = STATE_PREFIX + "selectedId";
    private static final String STATE_BUILT_IN = STATE_PREFIX + "builtIn";
    private static final String STATE_DIRTY = STATE_PREFIX + "dirty";
    private static final String STATE_EDITOR_TEXT = STATE_PREFIX + "editorText";
    private static final String STATE_DEFAULT_ID = STATE_PREFIX + "defaultId";
    private static final String STATE_NOTICE = STATE_PREFIX + "notice";
    private static final String STATE_LAST_ERROR = STATE_PREFIX + "lastError";

    interface Listener {
        void onDefaultProfileChanged(ConfigProfile profile, UnifiedAutomationConfig config);
    }

    private final Activity activity;
    private final ConfigProfileRepository repository;
    private final Listener listener;
    private final ConfigProfileUiState state = new ConfigProfileUiState();
    private List<ConfigProfile> profiles = Collections.emptyList();
    private Spinner profileSpinner;
    private EditText editor;
    private TextView status;
    private String defaultProfileId = ConfigProfileRepository.BUILTIN_ID;
    private String lastError = "";
    private String notice = "";
    private final ConfigProfileUiState.SelectionGate selectionGate =
            new ConfigProfileUiState.SelectionGate();
    private boolean changingEditor;

    ConfigProfileUiController(
            Activity activity,
            ConfigProfileRepository repository,
            Listener listener
    ) {
        this.activity = activity;
        this.repository = repository;
        this.listener = listener;
    }

    View buildView() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        profileSpinner = new Spinner(activity);
        root.addView(profileSpinner, matchWidth());

        editor = new EditText(activity);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setMinLines(12);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(13);
        editor.setHorizontallyScrolling(false);
        editor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout.LayoutParams editorParams = matchWidth();
        editorParams.setMargins(0, dp(8), 0, 0);
        root.addView(editor, editorParams);

        status = new TextView(activity);
        status.setTextSize(13);
        status.setPadding(0, dp(6), 0, dp(2));
        root.addView(status, matchWidth());

        root.addView(button("新建空白配置", new View.OnClickListener() {
            @Override public void onClick(View view) {
                newBlankProfile();
            }
        }));
        root.addView(button("保存", new View.OnClickListener() {
            @Override public void onClick(View view) {
                saveSelected();
            }
        }));
        root.addView(button("另存为", new View.OnClickListener() {
            @Override public void onClick(View view) {
                promptSaveAs();
            }
        }));
        root.addView(button("设为默认", new View.OnClickListener() {
            @Override public void onClick(View view) {
                setSelectedDefault();
            }
        }));

        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (!changingEditor) {
                    state.edited();
                    notice = "";
                    renderStatus();
                }
            }

            @Override public void afterTextChanged(Editable editable) {
            }
        });
        profileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int currentPosition = profileSpinner.getSelectedItemPosition();
                if (position >= 0 && position < profiles.size()
                        && selectionGate.shouldSelectProfile(position, currentPosition)
                        && !profiles.get(position).id.equals(state.selectedId())) {
                    selectProfile(profiles.get(position));
                }
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        renderStatus();
        return root;
    }

    void loadActive() {
        try {
            ConfigProfileRepository.LoadResult result = repository.loadActive();
            UnifiedAutomationConfig config = UnifiedAutomationConfig.parse(result.profile.id, result.profile.yaml);
            defaultProfileId = result.profile.id;
            selectProfile(result.profile);
            listener.onDefaultProfileChanged(result.profile, config);
            refreshProfilesOrThrow(result.profile.id);
            lastError = "";
            notice = result.warning == null ? "" : result.warning;
            renderStatus();
        } catch (Exception error) {
            showError(error);
        }
    }

    void refreshProfiles(String selectId) {
        try {
            refreshProfilesOrThrow(selectId);
            lastError = "";
            renderStatus();
        } catch (Exception error) {
            showError(error);
        }
    }

    String selectedProfileId() {
        return state.selectedId();
    }

    String lastError() {
        return lastError;
    }

    void saveInstanceState(Bundle outState) {
        if (outState == null || editor == null) {
            return;
        }
        ConfigProfileUiState.EditorSnapshot snapshot = state.snapshot(
                editorText(),
                defaultProfileId,
                notice,
                lastError
        );
        outState.putString(STATE_SELECTED_ID, snapshot.selectedId);
        outState.putBoolean(STATE_BUILT_IN, snapshot.builtIn);
        outState.putBoolean(STATE_DIRTY, snapshot.dirty);
        outState.putString(STATE_EDITOR_TEXT, snapshot.editorText);
        outState.putString(STATE_DEFAULT_ID, snapshot.defaultProfileId);
        outState.putString(STATE_NOTICE, snapshot.notice);
        outState.putString(STATE_LAST_ERROR, snapshot.lastError);
    }

    void restoreInstanceState(Bundle savedState) {
        if (savedState == null || !savedState.containsKey(STATE_SELECTED_ID)) {
            return;
        }
        final ConfigProfileUiState.EditorSnapshot snapshot = new ConfigProfileUiState.EditorSnapshot(
                savedState.getString(STATE_SELECTED_ID, ConfigProfileRepository.BUILTIN_ID),
                savedState.getBoolean(STATE_BUILT_IN, true),
                savedState.getBoolean(STATE_DIRTY, false),
                savedState.getString(STATE_EDITOR_TEXT, ""),
                savedState.getString(STATE_DEFAULT_ID, ConfigProfileRepository.BUILTIN_ID),
                savedState.getString(STATE_NOTICE, ""),
                savedState.getString(STATE_LAST_ERROR, "")
        );
        profileSpinner.post(new Runnable() {
            @Override public void run() {
                restoreSnapshot(snapshot);
            }
        });
    }

    void showError(Exception error) {
        lastError = message(error);
        notice = "";
        renderStatus();
    }

    private void refreshProfilesOrThrow(String selectId) throws IOException {
        profiles = repository.list();
        List<String> labels = new ArrayList<>();
        int selectedPosition = 0;
        for (int index = 0; index < profiles.size(); index++) {
            ConfigProfile profile = profiles.get(index);
            labels.add(profile.displayName + (profile.builtIn ? "（内置只读）" : ""));
            if (profile.id.equals(selectId)) {
                selectedPosition = index;
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                activity,
                android.R.layout.simple_spinner_item,
                labels
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        selectionGate.expectAdapterSelection(selectedPosition);
        profileSpinner.setAdapter(adapter);
        int currentPosition = profileSpinner.getSelectedItemPosition();
        if (currentPosition != selectedPosition) {
            selectionGate.expectSelection(currentPosition, selectedPosition);
            profileSpinner.setSelection(selectedPosition);
        }
        clearExpectedSelectionLater(selectedPosition);
        selectProfile(profiles.get(selectedPosition));
    }

    private void selectProfile(ConfigProfile profile) {
        state.select(profile);
        changingEditor = true;
        editor.setText(profile.yaml);
        editor.setSelection(editor.length());
        changingEditor = false;
        lastError = "";
        notice = "";
        renderStatus();
    }

    private void newBlankProfile() {
        try {
            ConfigProfile builtIn = repository.load(ConfigProfileRepository.BUILTIN_ID);
            state.select(builtIn);
            selectSpinner(ConfigProfileRepository.BUILTIN_ID);
            changingEditor = true;
            editor.setText(DefaultUnifiedConfig.text());
            editor.setSelection(editor.length());
            changingEditor = false;
            state.edited();
            lastError = "";
            notice = "已创建空白配置，请另存为。";
            renderStatus();
        } catch (Exception error) {
            showError(error);
        }
    }

    private void saveSelected() {
        if (ConfigProfileRepository.BUILTIN_ID.equals(state.selectedId())) {
            showError(new IOException("内置配置只读，请使用另存为"));
            return;
        }
        try {
            ConfigProfile saved = repository.save(state.selectedId(), editorText());
            state.saved(saved);
            if (saved.id.equals(defaultProfileId)) {
                notifyDefault(saved);
            }
            refreshProfilesOrThrow(saved.id);
            lastError = "";
            notice = "保存成功。";
            renderStatus();
        } catch (Exception error) {
            showError(error);
        }
    }

    private void promptSaveAs() {
        final EditText nameInput = new EditText(activity);
        nameInput.setHint("配置名称");
        nameInput.setSingleLine(true);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("另存为")
                .setView(nameInput)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (saveAs(nameInput.getText().toString())) {
                    dialog.dismiss();
                }
            }
        });
    }

    private boolean saveAs(String displayName) {
        ConfigProfile saved;
        try {
            saved = repository.saveAs(displayName, editorText(), true, false);
            defaultProfileId = saved.id;
            state.saved(saved);
            notifyDefault(saved);
        } catch (Exception error) {
            showError(error);
            return false;
        }
        try {
            refreshProfilesOrThrow(saved.id);
            lastError = "";
            notice = "另存为成功并已设为默认。";
            renderStatus();
        } catch (Exception error) {
            showError(error);
        }
        return true;
    }

    private void setSelectedDefault() {
        if (!state.canSetDefault()) {
            showError(new IOException("当前配置有未保存的修改，请先保存或另存为"));
            return;
        }
        try {
            ConfigProfile candidate = repository.load(state.selectedId());
            UnifiedAutomationConfig config = UnifiedAutomationConfig.parse(candidate.id, candidate.yaml);
            repository.setDefault(candidate.id);
            defaultProfileId = candidate.id;
            listener.onDefaultProfileChanged(candidate, config);
            lastError = "";
            notice = "已设为默认配置。";
            renderStatus();
        } catch (Exception error) {
            showError(error);
        }
    }

    private void notifyDefault(ConfigProfile profile) throws IOException {
        UnifiedAutomationConfig config = UnifiedAutomationConfig.parse(profile.id, profile.yaml);
        listener.onDefaultProfileChanged(profile, config);
    }

    private void selectSpinner(String id) {
        for (int index = 0; index < profiles.size(); index++) {
            if (profiles.get(index).id.equals(id)) {
                int currentPosition = profileSpinner.getSelectedItemPosition();
                if (selectionGate.expectSelection(currentPosition, index)) {
                    profileSpinner.setSelection(index);
                    clearExpectedSelectionLater(index);
                }
                return;
            }
        }
    }

    private void clearExpectedSelectionLater(final int position) {
        profileSpinner.post(new Runnable() {
            @Override public void run() {
                selectionGate.clearExpectedSelection(position);
            }
        });
    }

    private void restoreSnapshot(ConfigProfileUiState.EditorSnapshot snapshot) {
        selectSpinner(snapshot.selectedId);
        state.restore(snapshot);
        defaultProfileId = snapshot.defaultProfileId;
        notice = snapshot.notice;
        lastError = snapshot.lastError;
        changingEditor = true;
        editor.setText(snapshot.editorText);
        editor.setSelection(editor.length());
        changingEditor = false;
        renderStatus();
    }

    private String editorText() {
        return editor.getText().toString();
    }

    private void renderStatus() {
        if (status == null) {
            return;
        }
        ConfigProfile selected = selectedProfile();
        String name = selected == null ? state.selectedId() : selected.displayName;
        String path = selected == null || selected.file == null
                ? "内置"
                : selected.file.getAbsolutePath();
        StringBuilder text = new StringBuilder()
                .append("配置：").append(name)
                .append("；默认：").append(state.selectedId().equals(defaultProfileId) ? "是" : "否")
                .append("；状态：").append(state.dirty() ? "已修改" : "未修改")
                .append("\n路径：").append(path);
        if (state.requiresSaveAs()) {
            text.append("\n保存方式：需要另存为");
        }
        if (!notice.isEmpty()) {
            text.append("\n提示：").append(notice);
        }
        if (!lastError.isEmpty()) {
            text.append("\n错误：").append(lastError);
        }
        status.setText(text.toString());
    }

    private ConfigProfile selectedProfile() {
        for (ConfigProfile profile : profiles) {
            if (profile.id.equals(state.selectedId())) {
                return profile;
            }
        }
        return null;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setText(label);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = matchWidth();
        params.setMargins(0, dp(6), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static String message(Exception error) {
        if (error == null) {
            return "未知错误";
        }
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }
}
