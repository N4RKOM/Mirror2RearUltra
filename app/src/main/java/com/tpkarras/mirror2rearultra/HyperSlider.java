package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.SeekBar;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;

import java.util.ArrayList;
import java.util.List;

/**
 * A capsule slider: a thick rounded bar that fills from the left, with no
 * handle, as HyperOS draws volume.
 *
 * <p>Replaces the Material slider, which cannot draw this shape correctly.
 * Once the track is thick enough to grab without a thumb, Material stops the
 * active track one corner radius short of the end, so a slider at its maximum
 * visibly fails to fill. Here the fill is a clip drawable over the track, so
 * full value means full bar.
 *
 * <p>Built on {@link AppCompatSeekBar} rather than a bare {@code View} so the
 * control keeps the platform's range semantics for free: TalkBack announces it
 * as a seek bar with a percentage, and it responds to accessibility and
 * keyboard adjustment actions.
 *
 * <p>The public API deliberately mirrors the Material slider it replaced -
 * {@link #setValue}, {@link #getValue} and {@link #addOnChangeListener} - so
 * the screens that use it did not have to be rewritten around a new shape.
 */
public class HyperSlider extends AppCompatSeekBar {

    public interface OnChangeListener {
        void onValueChange(HyperSlider slider, float value, boolean fromUser);
    }

    private float valueFrom;
    private float valueTo = 100f;
    private float stepSize = 1f;
    private final List<OnChangeListener> listeners = new ArrayList<>(2);
    /** Set while a programmatic value change is being applied. */
    private boolean applyingValue;

    public HyperSlider(Context context) {
        this(context, null);
    }

    public HyperSlider(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, androidx.appcompat.R.attr.seekBarStyle);
    }

    public HyperSlider(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (attrs != null) {
            TypedArray typed = context.obtainStyledAttributes(
                    attrs, R.styleable.HyperSlider, defStyleAttr, 0);
            valueFrom = typed.getFloat(R.styleable.HyperSlider_hyperValueFrom, valueFrom);
            valueTo = typed.getFloat(R.styleable.HyperSlider_hyperValueTo, valueTo);
            stepSize = typed.getFloat(R.styleable.HyperSlider_hyperStepSize, stepSize);
            typed.recycle();
        }
        if (stepSize <= 0f) {
            stepSize = 1f;
        }
        setMax(steps());
        super.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // A programmatic setValue must not be reported as a user edit,
                // or binding stored state would write it straight back.
                notifyListeners(valueAt(progress), fromUser && !applyingValue);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    /**
     * Dims the bar when it is switched off.
     *
     * <p>The bar carries no handle, so at a value of nought a live one and a
     * dead one look alike: both are an empty capsule. A crop chosen with a
     * frame leaves these three standing by, and a bright bar that ignores a
     * finger reads as a fault rather than as a decision.
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.4f);
    }

    public void addOnChangeListener(OnChangeListener listener) {
        listeners.add(listener);
    }

    /** Current value in the range set on this slider, not the raw progress. */
    public float getValue() {
        return valueAt(getProgress());
    }

    public void setValue(float value) {
        applyingValue = true;
        setProgress(progressFor(value));
        applyingValue = false;
    }

    private int steps() {
        return Math.max(1, Math.round((valueTo - valueFrom) / stepSize));
    }

    private float valueAt(int progress) {
        return valueFrom + progress * stepSize;
    }

    private int progressFor(float value) {
        float clamped = Math.max(valueFrom, Math.min(valueTo, value));
        return Math.round((clamped - valueFrom) / stepSize);
    }

    private void notifyListeners(float value, boolean fromUser) {
        for (int index = 0; index < listeners.size(); index++) {
            listeners.get(index).onValueChange(this, value, fromUser);
        }
    }
}
