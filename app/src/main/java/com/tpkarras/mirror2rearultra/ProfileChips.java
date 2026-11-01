package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.function.Consumer;

/** The row of profile chips shared by the home screen and the image settings. */
final class ProfileChips {
    private ProfileChips() { }

    /** Rebuilds {@code container} with one chip per profile, the selected one highlighted. */
    static void bind(Context context, LinearLayout container, List<MirrorProfile> profiles,
                     String selectedId, Consumer<MirrorProfile> onSelect) {
        bind(context, container, profiles, selectedId, onSelect, null);
    }

    /** As above, with a trailing "+" chip when {@code onAdd} is given. */
    static void bind(Context context, LinearLayout container, List<MirrorProfile> profiles,
                     String selectedId, Consumer<MirrorProfile> onSelect,
                     @Nullable Runnable onAdd) {
        container.removeAllViews();
        // One profile leaves nothing to choose; the row appears with the second.
        if (container.getParent() instanceof View) {
            ((View) container.getParent()).setVisibility(
                    profiles.size() > 1 ? View.VISIBLE : View.GONE);
        }
        float density = context.getResources().getDisplayMetrics().density;
        int horizontal = Math.round(16 * density);
        int vertical = Math.round(8 * density);
        for (MirrorProfile profile : profiles) {
            TextView chip = new TextView(context);
            chip.setText(MirrorSettings.profileDisplayName(context, profile));
            chip.setTextAppearance(R.style.HyperOS_Text_Body);
            chip.setTextColor(ContextCompat.getColorStateList(context, R.color.hyper_chip_text));
            chip.setBackgroundResource(R.drawable.hyper_chip_background);
            chip.setPadding(horizontal, vertical, horizontal, vertical);
            chip.setSelected(profile.id.equals(selectedId));
            chip.setOnClickListener(view -> onSelect.accept(profile));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(Math.round(8 * density));
            container.addView(chip, params);
        }
        if (onAdd != null) {
            TextView add = new TextView(context);
            add.setText("+");
            add.setTextAppearance(R.style.HyperOS_Text_Body);
            add.setTextColor(ContextCompat.getColorStateList(context, R.color.hyper_chip_text));
            add.setBackgroundResource(R.drawable.hyper_chip_background);
            add.setPadding(horizontal, vertical, horizontal, vertical);
            add.setContentDescription(context.getString(R.string.add_profile));
            add.setOnClickListener(view -> onAdd.run());
            container.addView(add, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }
}
