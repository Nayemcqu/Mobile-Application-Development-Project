package com.cqu.genaiexpensetracker.privacy_policy;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.widget.TextView;
import android.text.Html;

import com.cqu.genaiexpensetracker.R;

/**
 * PrivacyPolicy Fragment displays the privacy policy information to the user.
 * This fragment is navigated via the navigation drawer and includes static HTML-formatted
 * text rendered from strings.xml for readability and formatting.
 *
 * Features:
 * - Scrollable, modern card-style layout
 * - Formatted content using HTML tags
 * - Connected to the navigation drawer under "Privacy Policy"
 *
 * Layout used: nav_privacy_policy.xml
 * String source: R.string.privacy_policy_text
 *
 * Author: Kapil Pandey
 */
public class PrivacyPolicy extends Fragment {

    /**
     * Required empty public constructor for fragment instantiation.
     */
    public PrivacyPolicy() {
        // Default constructor
    }

    /**
     * Inflates the layout for the Privacy Policy fragment.
     *
     * @param inflater           LayoutInflater to inflate views
     * @param container          Parent view group (if any)
     * @param savedInstanceState Previous saved state
     * @return The root view for the fragment
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.nav_privacy_policy, container, false);
    }

    /**
     * Called immediately after onCreateView. Used to bind views and logic.
     *
     * @param view               The view returned by onCreateView
     * @param savedInstanceState Saved state (if any)
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView textView = view.findViewById(R.id.privacy_text);
        textView.setText(Html.fromHtml(getString(R.string.privacy_policy_text), Html.FROM_HTML_MODE_COMPACT));
    }
}
