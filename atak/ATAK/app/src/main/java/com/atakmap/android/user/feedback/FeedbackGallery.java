
package com.atakmap.android.user.feedback;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.gui.ImportFileBrowserDialog;
import com.atakmap.android.util.FileProviderHelper;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;

import java.io.File;
import java.util.List;

public class FeedbackGallery extends LinearLayout
        implements View.OnClickListener,
        ListView.OnItemClickListener,
        View.OnLayoutChangeListener {

    private static final String TAG = "FeedbackGallery";

    private GridView _relatedGrid;
    private FeedbackGalleryAdapter _relatedAdapter;
    private Context _context;
    private boolean _deleteMode = false;
    private Feedback entry;
    private SharedPreferences prefs;

    public FeedbackGallery(Context context) {
        this(context, null);
    }

    public FeedbackGallery(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FeedbackGallery(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        LayoutInflater inf = LayoutInflater.from(context);
        inf.inflate(R.layout.user_feedback_gallery, this, true);
        addOnLayoutChangeListener(this);
    }

    public void init(Context context) {

        _context = context;
        prefs = PreferenceManager.getDefaultSharedPreferences(context);

        _relatedGrid = (GridView) findViewById(R.id.related);
        _relatedGrid.setOnItemClickListener(this);

        findViewById(R.id.remove_related).setOnClickListener(this);
        findViewById(R.id.ms_add).setOnClickListener(this);
        findViewById(R.id.ms_delete).setOnClickListener(this);
        findViewById(R.id.ms_cancel).setOnClickListener(this);

        _relatedAdapter = new FeedbackGalleryAdapter(context);

        _relatedGrid.setAdapter(_relatedAdapter);
        _relatedGrid.setVisibility(View.VISIBLE);

        ((ImageButton) findViewById(R.id.remove_related))
                .setVisibility(View.VISIBLE);

        _relatedGrid.setNumColumns(3);
    }

    public void refresh(Feedback entry) {
        this.entry = entry;
        _relatedAdapter.setFiles(entry.getAssociatedFiles());
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position,
            long id) {
        FeedbackFile content = _relatedAdapter.getItem(position);
        if (content == null)
            return;

        if (_deleteMode)
            _relatedAdapter.toggleSelect(content);
        else {
            openFile(getContext(), content.getFile());
        }
    }

    @Override
    public void onClick(View sender) {
        int id = sender.getId();

        // Remove related files
        if (id == R.id.remove_related) {
            _relatedAdapter.setMultiSelect(_deleteMode = true);
            refreshGallery();
        }

        // Cancel delete mode
        else if (id == R.id.ms_cancel) {
            _relatedAdapter.setMultiSelect(_deleteMode = false);
            refreshGallery();
        } else if (id == R.id.ms_add) {
            _relatedAdapter.setMultiSelect(_deleteMode = false);
            new ImportFileBrowserDialog(_context)
                    .setTitle(_context.getString(R.string.import_file))
                    .setOnDismissListener(
                            new ImportFileBrowserDialog.DialogDismissed() {
                                @Override
                                public void onFileSelected(File f) {
                                    long size = entry.getFeedbackSize();
                                    int maxSize = Integer
                                            .parseInt(prefs.getString(
                                                    "filesharingSizeThresholdNoGo",
                                                    "20"));
                                    if (IOProviderFactory.length(f)
                                            + size > maxSize * 1024L * 1024L) {
                                        Toast.makeText(_context,
                                                "Feedback exceeds the current maximum length for a mission packages ("
                                                        +
                                                        maxSize + "mb)",
                                                Toast.LENGTH_SHORT).show();
                                    } else {
                                        entry.addAssociatedFile(f);
                                        _relatedAdapter.setMultiSelect(false);
                                        refresh(entry);
                                        refreshGallery();
                                    }
                                }

                                @Override
                                public void onDialogClosed() {
                                    refreshGallery();
                                }
                            })
                    .show();

        } else if (id == R.id.ms_delete) {
            List<FeedbackFile> files = _relatedAdapter.getSelectedFiles();
            if (files.size() == 0) {
                Toast.makeText(_context, R.string.no_files_selected_to_remove,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            AlertDialog.Builder b = new AlertDialog.Builder(_context);
            b.setTitle(R.string.are_you_sure);
            StringBuilder s = new StringBuilder(
                    _context.getString(R.string.remove) + "\n");
            for (FeedbackFile f : files)
                s.append("\t").append(f.getName()).append("\n");

            b.setMessage(s.toString());
            b.setPositiveButton(R.string.yes,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            _relatedAdapter.setMultiSelect(_deleteMode = false);
                            for (FeedbackFile f : files)
                                entry.removeAssociatedFile(f);
                            refresh(entry);
                            refreshGallery();
                        }
                    });
            b.setNegativeButton(R.string.cancel,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            _relatedAdapter.setMultiSelect(_deleteMode = false);
                            refreshGallery();
                        }
                    });
            b.show();
        }
    }

    private void refreshGallery() {
        View hint = findViewById(R.id.hint);
        View actions = findViewById(R.id.actions);
        View msActions = findViewById(R.id.ms_actions);

        if (!FileSystemUtils.isEmpty(entry.getAssociatedFiles())) {
            _relatedGrid.setVisibility(View.VISIBLE);
            hint.setVisibility(View.GONE);
        } else {
            _relatedGrid.setVisibility(View.GONE);
            hint.setVisibility(View.VISIBLE);
        }

        actions.setVisibility(View.GONE);
        msActions.setVisibility(View.GONE);

        if (_deleteMode)
            msActions.setVisibility(View.VISIBLE);
        else
            actions.setVisibility(View.VISIBLE);

    }

    @Override
    public void onLayoutChange(View view, int left, int top, int right,
            int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {

        if (oldRight - oldLeft == right - left) {
            return;
        }

        refreshGallery();
    }

    /**
     * Open a file with the system appropriate application
     * @param context the context to use
     * @param f the file to open
     */
    public static void openFile(Context context, File f) {
        // Create URI
        Uri uri = FileProviderHelper.fromFile(context, f);

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            // Check what kind of file you are trying to open, by comparing the url with extensions.
            // When the if condition is matched, plugin sets the correct intent (mime) type,
            // so Android knew what application to use to open the file
            ResourceFile.MIMEType mt = ResourceFile
                    .getMIMETypeForFile(f.getAbsolutePath());
            if (mt == null) {
                Toast.makeText(context,
                        context.getString(
                                R.string.unable_to_determine_file_type,
                                f.getName()),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            intent.setDataAndType(uri, mt.MIME);
            FileProviderHelper.setReadAccess(intent);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context,
                    context.getString(R.string.unable_to_find_application,
                            f.getName()),
                    Toast.LENGTH_SHORT).show();
        }
    }

}
