package com.craiovadata.guessthecountry;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Dialog Fragment containing rating form.
 */
public class InfoDialogFragment extends DialogFragment {

    public static final String TAG = "InfoDialog";
    private Item item;
    private boolean isImageDescr;

    @BindView(R.id.textView_details)
    TextView textViewDetails;

    @BindView(R.id.textView_title)
    TextView textViewTitle;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.dialog_info, container, false);
        ButterKnife.bind(this, v);

        if (item != null) {

            isImageDescr = Objects.requireNonNull(getActivity()).getPreferences(Context.MODE_PRIVATE).getBoolean("isImageDescr", false);
            updateUI(isImageDescr);
        } else
            dismiss();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        Objects.requireNonNull(getDialog().getWindow()).setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

    }

    @OnClick(R.id.info_cancel)
    public void onOkClicked(View view) {
        dismiss();
    }

    @OnClick(R.id.details_switch_button)
    public void onSwitchInfoButtonClicked(View view) {
        isImageDescr = !isImageDescr;
        updateUI(isImageDescr);
    }


    public void setItem(Item item) {
        this.item = item;
    }

    private void updateUI(boolean isImageDescr) {
        String title, descr;
        if (isImageDescr) {
            title = "\u263C  \n" + item.getImg_title();
            descr = item.getImg_description();
        } else {
            title = "\u266B \n" + item.getMusic_title();
            descr = item.getMusic_description();
        }
        textViewTitle.setText(title);
        textViewDetails.setText(descr);
    }

    @Override
    public void onStop() {
        super.onStop();
        Objects.requireNonNull(getActivity()).getPreferences(Context.MODE_PRIVATE).edit().putBoolean("isImageDescr", isImageDescr).apply();
    }
}
