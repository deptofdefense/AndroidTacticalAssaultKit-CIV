
package com.atakmap.android.helloworld;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.helloworld.plugin.R;
import com.atakmap.android.maps.MapView;

import java.util.ArrayList;
import java.util.List;

/**
 * A drop-down menu that demonstrates use of a ViewPager to show tabs of content
 */
public class TabViewDropDown extends DropDownReceiver implements
        View.OnClickListener, ViewPager.OnPageChangeListener {

    private final Context _plugin;
    private final View _view;
    private final TextView _title;
    private final View[] _tabDots;
    private final ViewPager _viewPager;

    public TabViewDropDown(MapView mapView, Context plugin) {
        super(mapView);
        _plugin = plugin;

        _view = LayoutInflater.from(_plugin).inflate(R.layout.tab_swipe_view,
                mapView, false);
        _title = _view.findViewById(R.id.tab_title);
        _tabDots = new View[] {
                _view.findViewById(R.id.tab_left_dot),
                _view.findViewById(R.id.tab_middle_dot),
                _view.findViewById(R.id.tab_right_dot)
        };

        List<Fragment> fragments = new ArrayList<>();
        fragments.add(new TestFragment().init(_plugin, 1));
        fragments.add(new TestFragment().init(_plugin, 2));
        fragments.add(new TestFragment().init(_plugin, 3));

        TestPagerAdapter adapter = new TestPagerAdapter(
                ((FragmentActivity) mapView.getContext())
                        .getSupportFragmentManager(),
                fragments);
        _viewPager = _view.findViewById(R.id.tab_pager);
        _viewPager.setAdapter(adapter);
        _viewPager.setOnPageChangeListener(this);
    }

    @Override
    public void disposeImpl() {
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
    }

    @Override
    public void onClick(View v) {
    }

    public void show() {
        showDropDown(_view, THREE_EIGHTHS_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                THIRD_HEIGHT);
    }

    @Override
    public void onPageSelected(int position) {
        for (int i = 0; i < _tabDots.length; i++)
            _tabDots[i].setSelected(position == i);
        _title.setText("Tab Display - Tab #" + (position + 1));
    }

    @Override
    public void onPageScrolled(int position, float positionOffset,
            int positionOffsetPixels) {
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    public static class TestFragment extends Fragment {

        private Context _plugin;
        private int _tabNum;

        public TestFragment init(Context plugin, int tabNum) {
            _plugin = plugin;
            _tabNum = tabNum;
            return this;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View v = LayoutInflater.from(_plugin).inflate(
                    R.layout.fragment_foo, container, false);
            TextView tv = v.findViewById(R.id.textView1);
            Button btn = v.findViewById(R.id.button1);

            tv.setText("Tab #" + _tabNum);
            btn.setText("Button #" + _tabNum);

            return v;
        }
    }

    public static class TestPagerAdapter extends FragmentPagerAdapter {

        private final List<Fragment> _fragments;

        public TestPagerAdapter(FragmentManager fm, List<Fragment> frags) {
            super(fm);
            _fragments = frags;
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public Fragment getItem(int position) {
            return _fragments.get(position);
        }

        @Override
        public int getItemPosition(Object object) {
            return PagerAdapter.POSITION_NONE;
        }
    }
}
