package com.example.jsongenerator.model; // 替换为你的包名

public class GitHubFile {
    private String name;
    private String path;
    private String type;
    private String downloadUrl;
    private String sha;

    public GitHubFile(String name, String path, String type, String downloadUrl) {
        this(name, path, type, downloadUrl, null);
    }

    public GitHubFile(String name, String path, String type, String downloadUrl, String sha) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.downloadUrl = downloadUrl;
        this.sha = sha;
    }

    public String getName() { return name; }
    public String getPath() { return path; }
    public String getType() { return type; }
    public String getDownloadUrl() { return downloadUrl; }
    public String getSha() { return sha; }
}
