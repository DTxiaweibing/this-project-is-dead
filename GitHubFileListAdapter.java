package com.example.jsongenerator.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

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
        if (convertView == null) {
            convertView = View.inflate(context, android.R.layout.simple_list_item_1, null);
        }
        TextView tv = (TextView) convertView;
        GitHubFile file = fileList.get(position);
        tv.setText(file.getName());
        tv.setPadding(20, 20, 20, 20);
        tv.setTextSize(14);
        if (position == selectedPos) {
            tv.setBackgroundColor(0xFF2196F3);
            tv.setTextColor(0xFFFFFFFF);
        } else {
            tv.setBackgroundColor(0xFFFFFFFF);
            tv.setTextColor(0xFF000000);
        }
        return convertView;
    }
}
