package io.github.gohoski.numai;

import android.content.Context;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Gleb on 16.08.2025.
 * adapter to easily control msg to UI
 */
class MessageAdapter extends ArrayAdapter<Message> {
    private static final int VIEW_TYPE_SENT = 0;
    private static final int VIEW_TYPE_RECEIVED = 1;

    private final Context context;
    private final Map<Message, AssistantUiState> assistantUiStates = new HashMap<Message, AssistantUiState>();

    MessageAdapter(Context context, List<Message> messages) {
        super(context, R.layout.message_sent, messages);
        Log.d("MessageAdapter", "Created adapter with " + messages.size() + " messages");
        this.context = context;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).isSent() ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    void prepareAssistantMessage(Message message, boolean reasoningEnabled) {
        AssistantUiState state = getAssistantUiState(message);
        state.reasoningEnabled = reasoningEnabled;
        state.reasoningActive = reasoningEnabled;
        state.reasoningText = "";
        state.reasoningDurationSec = 0;
        state.showResponseLoader = false;
    }

    void updateReasoningState(Message message, String reasoningText, boolean reasoningEnabled, boolean reasoningActive, int durationSec) {
        AssistantUiState state = getAssistantUiState(message);
        state.reasoningEnabled = reasoningEnabled;
        state.reasoningActive = reasoningActive;
        state.reasoningText = reasoningText != null ? reasoningText : "";
        state.reasoningDurationSec = Math.max(durationSec, 0);
        if (state.reasoningText.length() == 0) {
            state.reasoningExpanded = false;
        }
    }

    void setResponseLoaderVisible(Message message, boolean visible) {
        AssistantUiState state = getAssistantUiState(message);
        state.showResponseLoader = visible;
    }

    private AssistantUiState getAssistantUiState(Message message) {
        AssistantUiState state = assistantUiStates.get(message);
        if (state == null) {
            state = new AssistantUiState();
            assistantUiStates.put(message, state);
        }
        return state;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Message message = getItem(position);
        int viewType = getItemViewType(position);

        if (viewType == VIEW_TYPE_SENT) {
            return getSentView(convertView, parent, message);
        }
        return getReceivedView(position, convertView, parent, message);
    }

    private View getSentView(View convertView, ViewGroup parent, Message message) {
        SentViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.message_sent, parent, false);
            holder = new SentViewHolder();
            holder.imageContainer = (LinearLayout) convertView.findViewById(R.id.message_images);
            holder.messageText = (TextView) convertView.findViewById(R.id.message_text);
            convertView.setTag(holder);
        } else {
            holder = (SentViewHolder) convertView.getTag();
        }

        holder.messageText.setMaxWidth((int) (context.getResources().getDisplayMetrics().widthPixels * 0.72f));
        bindSentImages(holder, message);

        String content = message.getContent();
        boolean hasText = content != null && content.length() != 0;
        holder.messageText.setText(hasText ? content : "");
        holder.messageText.setVisibility(hasText ? View.VISIBLE : View.GONE);

        LinearLayout.LayoutParams textParams = (LinearLayout.LayoutParams) holder.messageText.getLayoutParams();
        textParams.topMargin = holder.imageContainer.getVisibility() == View.VISIBLE && hasText ? dpToPx(8) : 0;
        holder.messageText.setLayoutParams(textParams);

        convertView.requestLayout();
        return convertView;
    }

    private void bindSentImages(SentViewHolder holder, Message message) {
        holder.imageContainer.removeAllViews();
        List<String> images = message.getInputImages();
        if (images == null || images.isEmpty()) {
            holder.imageContainer.setVisibility(View.GONE);
            return;
        }

        holder.imageContainer.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(context);
        LinearLayout row = null;
        for (int i = 0; i < images.size(); i++) {
            Bitmap bitmap = decodeMessageBitmap(images.get(i));
            if (bitmap == null) continue;

            if (row == null || row.getChildCount() == 3) {
                row = new LinearLayout(context);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.gravity = Gravity.RIGHT;
                row.setLayoutParams(rowParams);
                row.setOrientation(LinearLayout.HORIZONTAL);
                holder.imageContainer.addView(row);
            }

            View thumbView = inflater.inflate(R.layout.message_image_thumbnail, row, false);
            ImageView preview = (ImageView) thumbView.findViewById(R.id.thumbnail_image);
            preview.setImageBitmap(bitmap);
            final String rawImage = images.get(i);
            thumbView.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showImagePreview(rawImage);
                }
            });
            row.addView(thumbView);
        }

        if (holder.imageContainer.getChildCount() == 0) {
            holder.imageContainer.setVisibility(View.GONE);
        }
    }

    private View getReceivedView(int position, View convertView, ViewGroup parent, Message message) {
        final ReceivedViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.message_received, parent, false);

            holder = new ReceivedViewHolder();
            holder.messageText = (TextView) convertView.findViewById(R.id.message_text);
            holder.llm = (TextView) convertView.findViewById(R.id.llm);
            holder.thinkingCard = convertView.findViewById(R.id.thinking_card);
            holder.thinkingLayout = (LinearLayout) convertView.findViewById(R.id.thinkingLayout);
            holder.thinkingHeader = convertView.findViewById(R.id.thinking_header);
            holder.thinkingStatus = (TextView) convertView.findViewById(R.id.thinking_status);
            holder.thinkingToggleIcon = (ImageButton) convertView.findViewById(R.id.thinking_toggle_icon);
            holder.thinkingProcess = (TextView) convertView.findViewById(R.id.thinkingProcess);
            holder.responseLoader = convertView.findViewById(R.id.response_loader);
            holder.response = convertView.findViewById(R.id.response);
            convertView.setTag(holder);
        } else {
            holder = (ReceivedViewHolder) convertView.getTag();
        }

        final AssistantUiState state = getAssistantUiState(message);
        final String displayContent = message.getContent() != null ? message.getContent() : "";
        final String reasoningText = state.reasoningText != null ? state.reasoningText : "";

        holder.messageText.setText(displayContent);
        if (shouldShowModelLabel(position, message)) {
            holder.llm.setText(message.getLlm());
            holder.llm.setVisibility(View.VISIBLE);
        } else {
            holder.llm.setText("");
            holder.llm.setVisibility(View.GONE);
        }

        bindReasoningSection(holder, state, reasoningText);

        holder.response.setVisibility(View.VISIBLE);
        boolean showLoader = !state.reasoningEnabled && state.showResponseLoader && displayContent.length() == 0;
        if (showLoader) {
            Animation pulse = AnimationUtils.loadAnimation(context, R.anim.pulse);
            holder.responseLoader.setVisibility(View.VISIBLE);
            holder.responseLoader.startAnimation(pulse);
            holder.messageText.setVisibility(View.GONE);
        } else {
            holder.responseLoader.clearAnimation();
            holder.responseLoader.setVisibility(View.GONE);
            holder.messageText.setVisibility(displayContent.length() == 0 ? View.GONE : View.VISIBLE);
        }

        convertView.requestLayout();
        return convertView;
    }

    private void bindReasoningSection(final ReceivedViewHolder holder, final AssistantUiState state, final String reasoningText) {
        boolean showReasoning = state.reasoningEnabled &&
                (state.reasoningActive || state.reasoningDurationSec > 0 || reasoningText.length() != 0);

        if (!showReasoning) {
            holder.thinkingStatus.clearAnimation();
            holder.thinkingCard.setVisibility(View.GONE);
            holder.thinkingProcess.setVisibility(View.GONE);
            return;
        }

        holder.thinkingCard.setVisibility(View.VISIBLE);
        holder.thinkingLayout.setVisibility(View.VISIBLE);

        int shownDuration = state.reasoningActive ? Math.max(1, state.reasoningDurationSec) : Math.max(0, state.reasoningDurationSec);
        holder.thinkingStatus.setText(context.getString(
                state.reasoningActive ? R.string.reasoning_active_status : R.string.reasoning_done_status,
                Integer.valueOf(shownDuration)
        ));

        if (state.reasoningActive) {
            holder.thinkingStatus.startAnimation(AnimationUtils.loadAnimation(context, R.anim.thinking_shimmer));
        } else {
            holder.thinkingStatus.clearAnimation();
        }

        boolean expandable = reasoningText.length() != 0;
        holder.thinkingToggleIcon.setVisibility(expandable ? View.VISIBLE : View.GONE);
        holder.thinkingToggleIcon.setImageResource(state.reasoningExpanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);

        if (state.reasoningExpanded && expandable) {
            holder.thinkingProcess.setVisibility(View.VISIBLE);
            holder.thinkingProcess.setText(reasoningText);
        } else {
            holder.thinkingProcess.setVisibility(View.GONE);
            holder.thinkingProcess.setText(reasoningText);
        }

        holder.thinkingHeader.setOnClickListener(null);
        holder.thinkingToggleIcon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (reasoningText.length() == 0) return;
                state.reasoningExpanded = !state.reasoningExpanded;
                notifyDataSetChanged();
            }
        });
    }

    private Bitmap decodeMessageBitmap(String rawImage) {
        if (rawImage == null || rawImage.length() == 0) {
            return null;
        }

        String encodedImage = rawImage;
        int prefixSeparator = rawImage.indexOf(',');
        if (prefixSeparator != -1 && prefixSeparator + 1 < rawImage.length()) {
            encodedImage = rawImage.substring(prefixSeparator + 1);
        }

        try {
            byte[] bytes = Base64.decode(encodedImage);
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            Log.e("MessageAdapter", "Failed to decode attachment preview", e);
            return null;
        }
    }

    private void showImagePreview(String rawImage) {
        Bitmap bitmap = decodeMessageBitmap(rawImage);
        if (bitmap == null) {
            return;
        }
        final Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_image_preview);
        ImageView previewImage = (ImageView) dialog.findViewById(R.id.preview_image);
        View close = dialog.findViewById(R.id.preview_close);
        previewImage.setImageBitmap(bitmap);
        previewImage.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private boolean shouldShowModelLabel(int position, Message currentMessage) {
        String currentModel = currentMessage.getLlm();
        if (currentModel == null || currentModel.length() == 0) return false;
        for (int i = position - 1; i >= 0; i--) {
            Message previous = getItem(i);
            if (previous != null && !previous.isSent()) {
                String previousModel = previous.getLlm();
                return previousModel != null && previousModel.length() != 0 && !currentModel.equals(previousModel);
            }
        }
        return false;
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private static class AssistantUiState {
        boolean reasoningEnabled;
        boolean reasoningActive;
        boolean reasoningExpanded;
        boolean showResponseLoader;
        int reasoningDurationSec;
        String reasoningText = "";
    }

    private static class SentViewHolder {
        LinearLayout imageContainer;
        TextView messageText;
    }

    private static class ReceivedViewHolder {
        TextView messageText;
        TextView llm;
        View thinkingCard;
        LinearLayout thinkingLayout;
        View thinkingHeader;
        TextView thinkingStatus;
        ImageButton thinkingToggleIcon;
        TextView thinkingProcess;
        View responseLoader;
        View response;
    }
}
