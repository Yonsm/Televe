package io.library.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;

public interface DrawerDelegate {

    /**
     * Returns the context of ActionBar/Toolbar
     */
    Context getThemedContext();

    /**
     * Set the indicator drawable and content description.
     *
     * @param drawable - Drawable to set as up indicator
     */
    void setUpIndicator(Drawable drawable);

    /**
     * Set the indicator content description.
     *
     * @param descRes - Content description to set
     */
    void setDescription(int descRes);
}
