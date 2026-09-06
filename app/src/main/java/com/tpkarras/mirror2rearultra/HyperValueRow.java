package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.ListPopupWindow;

/**
 * A settings row that shows a label, its current value, and a chevron, and
 * opens a single-choice list when tapped.
 *
 * <p>This replaces the exposed-dropdown text field the screens used before.
 * A bordered or filled input box with a floating label is a Material 3
 * signature; HyperOS states a bounded choice as a row whose value sits at the
 * trailing edge, and puts the options in a list when the row is opened. The
 * semantics are unchanged - it is still an exclusive choice from a fixed set.
 *
 * <p>The API is deliberately shaped like the {@code MaterialAutoCompleteTextView}
 * calls it replaced: {@link #setEntries} stands in for {@code setAdapter},
 * {@link #setOnItemSelectedListener} for {@code setOnItemClickListener}, and
 * {@link #setValue} for {@code setText(label, false)}.
 */
public class HyperValueRow extends LinearLayout {

    public interface OnItemSelectedListener {
        void onItemSelected(int position);
    }

    private final TextView titleView;
    private final TextView valueView;

    private String[] entries = new String[0];
    private int selectedIndex = -1;
    @Nullable private OnItemSelectedListener listener;

    public HyperValueRow(Context context) {
        this(context, null);
    }

    public HyperValueRow(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HyperValueRow(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_hyper_value_row, this, true);
        titleView = findViewById(R.id.hyper_row_title);
        valueView = findViewById(R.id.hyper_row_value);

        if (attrs != null) {
            TypedArray typed = context.obtainStyledAttributes(
                    attrs, R.styleable.HyperValueRow, defStyleAttr, 0);
            titleView.setText(typed.getString(R.styleable.HyperValueRow_rowTitle));
            valueView.setText(typed.getString(R.styleable.HyperValueRow_rowValue));
            typed.recycle();
        }

        setClickable(true);
        setFocusable(true);
        setOnClickListener(view -> showChooser());
        updateContentDescription();
    }

    /** Replaces the choices. Any current selection is cleared. */
    public void setEntries(String[] values) {
        entries = values == null ? new String[0] : values;
        selectedIndex = -1;
    }

    public void setOnItemSelectedListener(@Nullable OnItemSelectedListener value) {
        listener = value;
    }

    /**
     * Shows {@code label} as the current value without notifying the listener,
     * so binding the UI from stored state cannot loop back into a save.
     */
    public void setValue(CharSequence label) {
        valueView.setText(label);
        selectedIndex = indexOf(label);
        updateContentDescription();
    }

    public void setTitle(CharSequence label) {
        titleView.setText(label);
        updateContentDescription();
    }

    public CharSequence getTitle() {
        return titleView.getText();
    }

    /** Index of the current value in the entries, or -1 if nothing matches. */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        titleView.setEnabled(enabled);
        valueView.setEnabled(enabled);
        // The row is one control, so the whole thing dims rather than only its
        // label, and it stops taking focus while it cannot be used.
        setAlpha(enabled ? 1f : 0.45f);
        setClickable(enabled);
        setFocusable(enabled);
    }

    /**
     * Opens the choices as a panel anchored to this row.
     *
     * <p>A centred dialog with radio buttons pulls attention away from the row
     * being edited and dims the page behind it. HyperOS keeps the list beside
     * the control, with the current entry marked in place, so the choice reads
     * as an extension of the row rather than an interruption.
     */
    private void showChooser() {
        if (entries.length == 0) {
            return;
        }
        ListPopupWindow popup = new ListPopupWindow(getContext());
        PickerAdapter adapter = new PickerAdapter();
        popup.setAdapter(adapter);
        popup.setAnchorView(this);
        popup.setModal(true);
        popup.setBackgroundDrawable(
                androidx.core.content.ContextCompat.getDrawable(
                        getContext(), R.drawable.hyper_popup_background));
        int width = measurePopupWidth(adapter);
        popup.setWidth(width);
        // Align the panel with the value it is changing, at the trailing edge
        // of the row, instead of letting it default to the leading edge where
        // it would sit under the label it has nothing to do with.
        popup.setDropDownGravity(Gravity.END);
        popup.setHorizontalOffset(-getPaddingEnd());
        // Drops below the row with a small gap. It must not cover the setting
        // it belongs to: the row states what is being chosen, and hiding it
        // leaves the list with no subject.
        popup.setVerticalOffset(Math.round(getResources().getDisplayMetrics().density * 4));
        popup.setOnItemClickListener((parent, view, position, id) -> {
            popup.dismiss();
            if (position < 0 || position >= entries.length) {
                return;
            }
            selectedIndex = position;
            valueView.setText(entries[position]);
            updateContentDescription();
            if (listener != null) {
                listener.onItemSelected(position);
            }
        });
        popup.show();
        if (popup.getListView() != null) {
            popup.getListView().setDivider(null);
            popup.getListView().setDividerHeight(0);
        }
    }

    /** Widest entry plus the checkmark, clamped so the panel stays readable. */
    private int measurePopupWidth(PickerAdapter adapter) {
        int minimum = Math.round(getResources().getDisplayMetrics().density * 200);
        int maximum = Math.max(minimum, Math.round(getWidth() * 0.95f));
        int widest = 0;
        int spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        View reused = null;
        for (int index = 0; index < adapter.getCount(); index++) {
            reused = adapter.getView(index, reused, this);
            reused.measure(spec, spec);
            widest = Math.max(widest, reused.getMeasuredWidth());
        }
        return Math.max(minimum, Math.min(widest, maximum));
    }

    private final class PickerAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return entries.length;
        }

        @Override
        public Object getItem(int position) {
            return entries[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, @Nullable View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_hyper_picker, parent, false);
            }
            boolean chosen = position == selectedIndex;
            TextView label = row.findViewById(R.id.hyper_picker_label);
            ImageView check = row.findViewById(R.id.hyper_picker_check);
            label.setText(entries[position]);
            check.setVisibility(chosen ? VISIBLE : INVISIBLE);
            // Drives both the tinted band and the accent label through the
            // activated state, so the two never disagree.
            row.setActivated(chosen);
            label.setActivated(chosen);
            return row;
        }
    }

    private int indexOf(CharSequence label) {
        if (label == null) {
            return -1;
        }
        for (int index = 0; index < entries.length; index++) {
            if (label.toString().equals(entries[index])) {
                return index;
            }
        }
        return -1;
    }

    /**
     * TalkBack reads the row as one control. Without this the label and the
     * value are announced as two unrelated pieces of text.
     */
    private void updateContentDescription() {
        CharSequence title = titleView.getText();
        CharSequence value = valueView.getText();
        if (value == null || value.length() == 0) {
            setContentDescription(title);
        } else {
            setContentDescription(title + ", " + value);
        }
    }
}
