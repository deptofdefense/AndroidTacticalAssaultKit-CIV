
package gov.tak.api.widgets;

import java.util.List;

public interface IParentWidget extends IMapWidget {

    int getChildWidgetCount();

    IMapWidget getChildWidgetAt(int index);

    void addChildWidget(IMapWidget widget);

    void addChildWidgetAt(int index, IMapWidget widget);
    IMapWidget removeChildWidgetAt(int index);

    boolean removeChildWidget(IMapWidget widget);

    List<IMapWidget> getSortedChildrenWidgets();

    List<IMapWidget> getChildren();

    interface OnWidgetListChangedListener  {


        void onWidgetAdded(IParentWidget parent, int index,
                           IMapWidget child);

        void onWidgetRemoved(IParentWidget parent, int index,
                             IMapWidget child);
    }

    void addOnWidgetListChangedListener(OnWidgetListChangedListener listChangedListener);
    void removeOnWidgetListChangedListener(OnWidgetListChangedListener listChangedListener);


    boolean onChildWidgetCanBeAdded(int index, IMapWidget widget);
}
