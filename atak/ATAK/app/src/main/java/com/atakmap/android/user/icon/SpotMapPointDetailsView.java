
package com.atakmap.android.user.icon;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;

import com.atakmap.android.cotdetails.CoTInfoBroadcastReceiver;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.android.drawing.details.GenericPointDetailsView;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.image.ImageGalleryReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.tools.AtakLayerDrawableUtil;
import com.atakmap.app.R;

public class SpotMapPointDetailsView extends GenericPointDetailsView {

    private static final String TAG = "SpotMapPointDetailsView";

    private CheckBox _labelOnly;
    private ImageButton _attachmentsButton;

    /*************************** CONSTRUCTORS *****************************/

    public SpotMapPointDetailsView(Context context) {
        super(context);
    }

    public SpotMapPointDetailsView(Context context, final AttributeSet inAtr) {
        super(context, inAtr);
    }

    @Override
    protected void _init() {
        super._init();

        _labelOnly = this
                .findViewById(R.id.drawingGenPointLabelOnly);
        if (_point != null) {
            final String iconsetPath = _point.getMetaString(
                    UserIcon.IconsetPath, "");
            if (iconsetPath
                    .equals(SpotMapPalletFragment.LABEL_ONLY_ICONSETPATH)) {
                _colorButton.setEnabled(true);
                _labelOnly.setChecked(true);
            } else {
                _colorButton.setEnabled(true);
                _labelOnly.setChecked(false);
            }
        }
        _labelOnly.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                int color = _point.getMetaInteger("color", Color.WHITE);

                if (isChecked) {
                    _colorButton.setEnabled(false);
                    _point.setAlwaysShowText(true);
                    _point.setMetaString(UserIcon.IconsetPath,
                            SpotMapPalletFragment.LABEL_ONLY_ICONSETPATH);
                    _point.setIconVisibility(Marker.ICON_GONE);
                } else {
                    _colorButton.setEnabled(true);
                    _point.setAlwaysShowText(false);
                    _point.setMetaString(UserIcon.IconsetPath,
                            UserIcon.GetIconsetPath(
                                    SpotMapPallet.COT_MAPPING_SPOTMAP,
                                    SpotMapReceiver.SPOT_MAP_POINT_COT_TYPE,
                                    String.valueOf(color)));
                    _point.setIconVisibility(Marker.ICON_VISIBLE);
                }

                //refresh point
                _onColorSelected(color, "unused");
            }
        });

        _attachmentsButton = this
                .findViewById(R.id.drawingGenPointAttachmentsButton);

        updateAttachmentsButton();
        _attachmentsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        ImageGalleryReceiver.VIEW_ATTACHMENTS)
                                .putExtra("uid", _point.getUID()));

            }
        });
    }

    private void updateAttachmentsButton() {
        final int numAttachments = AttachmentManager
                .getNumberOfAttachments(_point.getUID());

        post(new Runnable() {
            @Override
            public void run() {
                LayerDrawable ld = (LayerDrawable) SpotMapPointDetailsView.this
                        .getContext().getResources()
                        .getDrawable(R.drawable.attachment_badge);
                if (ld != null) {
                    AtakLayerDrawableUtil.getInstance(
                            SpotMapPointDetailsView.this.getContext())
                            .setBadgeCount(ld, numAttachments);
                    _attachmentsButton.setImageDrawable(ld);
                } else {
                    _attachmentsButton.setImageResource(R.drawable.attachment);
                }
            }
        });
    }

    @Override
    protected void sendSelected(final String uid) {
        CoTInfoBroadcastReceiver.promptSendAttachments(_point, null, null,
                new Runnable() {
                    @Override
                    public void run() {
                        // Send marker only
                        SpotMapPointDetailsView.super.sendSelected(uid);
                    }
                });
    }

    @Override
    protected void _onColorSelected(int color, String label) {
        super._onColorSelected(color, label);
        if (_point != null) {
            final String iconsetPath = _point.getMetaString(
                    UserIcon.IconsetPath, "");
            if (iconsetPath
                    .equals(SpotMapPalletFragment.LABEL_ONLY_ICONSETPATH)) {
                _point.setTextColor(color);
            } else {
                _point.setTextColor(Color.WHITE);
            }
        }
    }

    /**
     * Update the visual aspects of the current View, namely the # of attachments
     */
    public void updateVisual() {
        updateAttachmentsButton();

        //setBackgroundResource(R.drawable.details_dropdown_background);
        //getBackground().setColorFilter(_point.getMetaInteger("color", Color.WHITE) , Mode.MULTIPLY);
    }
}
