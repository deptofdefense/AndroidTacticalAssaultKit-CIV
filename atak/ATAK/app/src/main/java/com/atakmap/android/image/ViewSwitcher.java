
package com.atakmap.android.image;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SpinnerAdapter;

import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

/**
 * ViewSwitcher graphical component
 */
public class ViewSwitcher extends RelativeLayout {

    public static final String TAG = "ViewSwitcher";

    public interface OnViewPositionChangedListener {
        void onViewPositionChanged(ViewSwitcher switcher, int newPos);
    }

    public ViewSwitcher(Context context, AttributeSet attrs) {
        super(context, attrs);
        _slideIn = AnimationUtils.loadAnimation(context, R.anim.slide_in);
        _slideOut = AnimationUtils.loadAnimation(context, R.anim.slide_out);
        _slideInRev = AnimationUtils
                .loadAnimation(context, R.anim.slide_in_rev);
        _slideOutRev = AnimationUtils.loadAnimation(context,
                R.anim.slide_out_rev);
    }

    public void setOnViewPositionChangedListener(
            OnViewPositionChangedListener l) {
        _listener = l;
    }

    public void setAdapter(SpinnerAdapter adapter) {
        _adapter = null;
        _swapView(0);
        _viewCount = 0;
        _viewIndex = 0;
        _adapter = adapter;
        if (_adapter != null) {
            _viewCount = _adapter.getCount();
            _swapView(0);
        }

    }

    /**
     * Sets the position of the view based on the index.
     * @param index is the value to set the view position to.  If greater than the number of views,
     *              just set the index to 0.
     */
    public void setViewPosition(int index) {
        Log.d(TAG, "index: " + index);
        if (index < 0) {
            index = 0;
        }
        if (index < _viewCount) {
            _swapView(index);
            _view.setVisibility(View.VISIBLE);
        } else if (_viewCount <= 0) {
            if (_view != null) {
                _view.setVisibility(View.INVISIBLE);
            }
        }
    }

    public void play(boolean play) {
        // rotate through the images with a 2 sec delay
        if (play) {
            if (pt == null) {
                pt = new PlayThread();
            }
            pt.play = true;
            pt.start();
        } else {
            try {
                if (pt != null) {
                    pt.play = false;
                    pt.join();
                    pt = null;
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "error: ", e);
            }
        }

    }

    private class PlayThread extends Thread {
        boolean play = false;

        @Override
        public void run() {
            while (play) {
                ViewSwitcher.this.post(new Runnable() {

                    @Override
                    public void run() {
                        int index = ViewSwitcher.this._viewIndex;
                        int count = ViewSwitcher.this._viewCount;

                        if ((++index) >= count) {
                            index = 0;
                        }

                        ViewSwitcher.this._slideInView(index);
                    }

                });

                synchronized (pt) {
                    try {
                        pt.wait(2000);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "error: ", e);
                    }
                }

            }

        }
    }

    private volatile PlayThread pt = null;

    /**
     * Sets the view position without any animation or visual effect, and possibly somewhat quicker?
     * 
     * @param index the current view index to move to.
     */
    public void quickSetViewPosition(final int index) {
        if (index >= 0 && index < _viewCount) {

            ImageView iv = _view
                    .findViewById(R.id.image_view_image);

            // for the first image we need to init components that aren't displayed yet
            if (_adapter != null) {
                if (iv.getParent().getParent() == null) {
                    View newView = _adapter.getView(index, _view, this);
                    if (_view != null)
                        removeView(_view);
                    _view = newView;
                    addView(newView);
                } else {
                    // then we can go back to using the old "quick" code. I'm assuming there was an
                    // advantage (speed?) to doing it this way,
                    // if not the above way always works.
                    ProgressBar pb = _view
                            .findViewById(R.id.image_view_progress);
                    iv.setImageBitmap(((ImageAdapter2) _adapter)
                            .getImage(index));
                    pb.setVisibility(View.INVISIBLE);
                    ((ImageAdapter2) _adapter).setIndex(index, _viewCount,
                            _view);
                    ((ImageAdapter2) _adapter).setData(index, _view);
                }
            }
            _viewIndex = index;
            onViewPositionChanged();
        }
    }

    public View getCurrentView() {
        return _view;
    }

    public int getViewPosition() {
        return _viewIndex;
    }

    public int getViewCount() {
        return _viewCount;
    }

    /**
     * Sets up the views based on the newCount passed in.
     * @param newCount the new number of views in the View Switcher.
     */
    public void setViewCount(final int newCount) {
        _viewCount = newCount;

        if (_view == null) {
            _view = _adapter.getView(_viewIndex, null, this);
        }

        ((ImageAdapter2) _adapter).setIndex(_viewIndex, newCount, _view);
    }

    public boolean next() {
        if (_viewIndex + 1 < _adapter.getCount()) {
            _swapView(_viewIndex + 1);
            return true;
        } else
            return false;
    }

    public boolean previous() {
        if (_viewIndex > 0) {
            _swapView(_viewIndex - 1);
            return true;
        } else
            return false;
    }

    private void _swapView(final int nextIndex) {
        if (_view != null) {
            final Animation.AnimationListener slideOutListener = new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    // _lockFling = true;
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    animation.setAnimationListener(null);
                    // _lockFling = false;
                    _slideInView(nextIndex);
                }
            };
            if (nextIndex < _viewIndex) {
                if (_slideOutRev.hasStarted() && !_slideOutRev.hasEnded()) {
                    // Arrow hit before animation ended, just skip
                    _slideOutRev.cancel();
                } else {
                    // Begin animation
                    _slideOutRev.setAnimationListener(slideOutListener);
                    _view.startAnimation(_slideOutRev);
                }
            } else if (nextIndex > _viewIndex) {
                if (_slideOut.hasStarted() && !_slideOut.hasEnded()) {
                    // Arrow hit before animation ended, just skip
                    _slideOut.cancel();
                } else {
                    // Begin animation
                    _slideOut.setAnimationListener(slideOutListener);
                    _view.startAnimation(_slideOut);
                }
            } else {
                // if it's the same we still have to update the view because it could have been
                // empty before
                _slideInView(nextIndex);
            }
        } else {
            _slideInView(nextIndex);
        }
    }

    private void _slideInView(int viewIndex) {
        if (_adapter != null && viewIndex < _viewCount) {
            View newView = _adapter.getView(viewIndex, _view, this);
            if (_view != null) {
                removeView(_view);
            }
            _view = newView;
            addView(newView);
            /*
             * newView.setOnTouchListener(new OnTouchListener() {
             * @Override public boolean onTouch(View view, MotionEvent ev) { return
             * _gestureDetector.onTouchEvent(ev); } });
             */
            final Animation.AnimationListener slideInListener = new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    // _lockFling = true;
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    animation.setAnimationListener(null);
                    // _lockFling = false;
                }
            };
            if (viewIndex > _viewIndex) {
                _slideIn.setAnimationListener(slideInListener);
                newView.startAnimation(_slideIn);
            } else if (viewIndex < _viewIndex) {
                _slideInRev.setAnimationListener(slideInListener);
                newView.startAnimation(_slideInRev);
            }

            Log.d(TAG, "index: " + viewIndex);
            _viewIndex = viewIndex;
            onViewPositionChanged();
        }
    }

    protected void onViewPositionChanged() {
        if (_listener != null) {
            _listener.onViewPositionChanged(this, _viewIndex);
        }
    }

    // private boolean _lockFling;
    private final Animation _slideIn;
    private final Animation _slideOut;
    private final Animation _slideInRev;
    private final Animation _slideOutRev;

    private int _viewIndex;
    private int _viewCount;
    private View _view;
    private SpinnerAdapter _adapter;
    private OnViewPositionChangedListener _listener;
}
