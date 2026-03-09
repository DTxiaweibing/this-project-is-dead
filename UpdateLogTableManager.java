package com.example.jsongenerator.manager;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.jsongenerator.R;

import java.util.ArrayList;
import java.util.List;

public class UpdateLogTableManager {
    private Context context;
    private LinearLayout container;
    private int rowHeightDp = 50;

    public UpdateLogTableManager(Context context, LinearLayout container) {
        this.context = context;
        this.container = container;
    }

    public void initTable(List<String> initialRows) {
        container.removeAllViews();
        for (String text : initialRows) {
            addRow(text);
        }
    }

    public void addRow(String text) {
        final EditText editText = createEditText(text);
        container.addView(editText);
    }

    public void addRowAt(int index, String text) {
        final EditText editText = createEditText(text);
        container.addView(editText, index);
    }

    private EditText createEditText(String text) {
        final EditText editText = new EditText(context);
        editText.setLayoutParams(new LinearLayout.LayoutParams(
                                     ViewGroup.LayoutParams.MATCH_PARENT,
                                     dpToPx(rowHeightDp)
                                 ));
        editText.setText(text);
        editText.setTextSize(13);
        editText.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        editText.setBackgroundResource(R.drawable.edittext_cell_bg);
        editText.setSingleLine(true);

        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_NEXT ||
                        (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                        int index = container.indexOfChild(editText);
                        if (index == container.getChildCount() - 1) {
                            addRowAt(index + 1, "");
                        }
                        View nextChild = container.getChildAt(index + 1);
                        if (nextChild != null) {
                            nextChild.requestFocus();
                        }
                        return true;
                    }
                    return false;
                }
            });

        return editText;
    }

    public String collectLog() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof EditText) {
                String text = ((EditText) child).getText().toString().trim();
                if (!text.isEmpty()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(text);
                }
            }
        }
        return sb.toString();
    }

    public List<String> getAllRows() {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof EditText) {
                rows.add(((EditText) child).getText().toString());
            }
        }
        return rows;
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
