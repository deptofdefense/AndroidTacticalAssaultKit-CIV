
package com.atakmap.android.image;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

import com.atakmap.android.util.AttachmentManager;
import com.atakmap.android.cotdetails.CoTInfoBroadcastReceiver;
import com.atakmap.android.drawing.details.GenericPointDetailsView;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.tools.AtakLayerDrawableUtil;
import com.atakmap.app.R;

public class ImageDetailsView extends GenericPointDetailsView {

    private ImageButton _attachmentsButton;

    /*************************** CONSTRUCTORS *****************************/

    public ImageDetailsView(Context context) {
        super(context);
    }

    public ImageDetailsView(Context context, final AttributeSet inAtr) {
        super(context, inAtr);
    }

    @Override
    protected void _init() {
        super._init();

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

    // this is a candidate for migration to use the attachment manager.
    private void updateAttachmentsButton() {
        final int numAttachments = AttachmentManager
                .getNumberOfAttachments(_point.getUID());

        post(new Runnable() {
            @Override
            public void run() {
                LayerDrawable ld = (LayerDrawable) ImageDetailsView.this
                        .getContext().getResources()
                        .getDrawable(R.drawable.attachment_badge);
                if (ld != null) {
                    AtakLayerDrawableUtil.getInstance(
                            ImageDetailsView.this.getContext()).setBadgeCount(
                                    ld, numAttachments);
                    _attachmentsButton.setImageDrawable(ld);
                } else {
                    _attachmentsButton.setImageResource(R.drawable.attachment);
                }
            }
        });
    }

    @Override
    protected void sendSelected(final String uid) {
        super.save();
        // Prompt user to save marker attachments
        CoTInfoBroadcastReceiver.promptSendAttachments(_point, null, null,
                new Runnable() {
                    @Override
                    public void run() {
                        // Send marker only
                        ImageDetailsView.super.sendSelected(uid);
                    }
                });
    }

    /**
     * Update the visual aspects of the current View, namely the # of attachments
     */
    public void updateVisual() {
        updateAttachmentsButton();
    }
}
