
package com.atakmap.android.gui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.TextView;

import com.atakmap.app.R;

public class HorizontalSliderComponent extends DexSliderComponent {
    public HorizontalSliderComponent(Context context) {
        super(context);
    }

    public HorizontalSliderComponent(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public Drawable getThumb(double progress, boolean isTilt) {
        ((TextView) thumbView.findViewById(R.id.progress_text_view))
                .setText(String.valueOf((int) progress) + "°");

        thumbView.measure(MeasureSpec.UNSPECIFIED,
                MeasureSpec.UNSPECIFIED);
        Bitmap bitmap = Bitmap.createBitmap(thumbView.getMeasuredWidth(),
                thumbView.getMeasuredHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        thumbView.layout(0, 0, thumbView.getMeasuredWidth(),
                thumbView.getMeasuredHeight());
        thumbView.draw(canvas);
        Drawable d = new BitmapDrawable(getResources(), bitmap);
        if (isTilt && maxTilt == 0) {
            d.setAlpha(64);
        } else {
            d.setAlpha(255);
        }
        return d;
    }
}
