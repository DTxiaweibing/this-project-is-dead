package com.example.jsongenerator.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.jsongenerator.R;
import com.example.jsongenerator.model.GitHubFile;

import java.util.List;

public class GitHubFileListAdapter extends BaseAdapter {
    private Context context;
    private List<GitHubFile> fileList;
    private int selectedPos = -1;

    public GitHubFileListAdapter(Context context, List<GitHubFile> fileList) {
        this.context = context;
        this.fileList = fileList;
    }

    public void setSelectedPos(int pos) {
        this.selectedPos = pos;
        notifyDataSetChanged();
    }

    public int getSelectedPos() {
        return selectedPos;
    }

    @Override
    public int getCount() {
        return fileList.size();
    }

    @Override
    public Object getItem(int position) {
        return fileList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setGravity(Gravity.CENTER_VERTICAL);
            layout.setPadding(16, 14, 16, 14);

            ImageView ivIcon = new ImageView(context);
            ivIcon.setPadding(0, 0, 12, 0);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    24, 24);
            layout.addView(ivIcon, iconParams);

            TextView tvName = new TextView(context);
            tvName.setTextSize(14);
            tvName.setSingleLine(true);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            layout.addView(tvName, nameParams);

            holder = new ViewHolder();
            holder.ivIcon = ivIcon;
            holder.tvName = tvName;
            holder.layout = layout;
            convertView = layout;
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        GitHubFile file = fileList.get(position);

        if ("dir".equals(file.getType())) {
            holder.ivIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_folder));
            holder.ivIcon.setColorFilter(null);
            holder.tvName.setText(file.getName() + "/");
            holder.tvName.setTextColor(Color.parseColor("#1565C0"));
        } else {
            holder.ivIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_file));
            holder.ivIcon.setColorFilter(null);
            holder.tvName.setText(file.getName());
            holder.tvName.setTextColor(Color.BLACK);
        }

        if (position == selectedPos) {
            holder.layout.setBackgroundColor(0xFF2196F3);
            holder.tvName.setTextColor(0xFFFFFFFF);
            holder.ivIcon.setColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);
        } else {
            holder.layout.setBackgroundColor(0xFFFFFFFF);
            if (!"dir".equals(file.getType())) {
                holder.tvName.setTextColor(0xFF000000);
            }
            holder.ivIcon.setColorFilter(null);
        }

        return convertView;
    }

    private static class ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        LinearLayout layout;
    }
}