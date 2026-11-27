package com.example.customcontrols;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Root JSON-serializable container matching MojoLauncher / PojavLauncher's customcontrols format.
 */
public class CustomControls {
    public int version = 8;
    public float scaledAt = 100f;
    public String gameName = "";
    public ArrayList<ControlData> mControlDataList = new ArrayList<>();
    public ArrayList<ControlDrawerData> mDrawerDataList = new ArrayList<>();
    public ArrayList<ControlJoystickData> mJoystickDataList = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public CustomControls() {
    }

    public void ensureValid() {
        if (gameName == null) gameName = "";
        if (mControlDataList == null) mControlDataList = new ArrayList<>();
        if (mDrawerDataList == null) mDrawerDataList = new ArrayList<>();
        if (mJoystickDataList == null) mJoystickDataList = new ArrayList<>();
        for (ControlData data : mControlDataList) {
            if (data != null) data.ensureValid();
        }
        for (ControlDrawerData drawer : mDrawerDataList) {
            if (drawer != null) drawer.ensureValid();
        }
        for (ControlJoystickData joy : mJoystickDataList) {
            if (joy != null) joy.ensureValid();
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static CustomControls fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new CustomControls();
        }
        CustomControls parsed = GSON.fromJson(json, CustomControls.class);
        if (parsed == null) {
            parsed = new CustomControls();
        }
        parsed.ensureValid();
        return parsed;
    }

    public void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(this, writer);
        }
    }

    public static CustomControls load(File file) throws IOException {
        try (FileReader reader = new FileReader(file)) {
            CustomControls parsed = GSON.fromJson(reader, CustomControls.class);
            if (parsed == null) parsed = new CustomControls();
            parsed.ensureValid();
            return parsed;
        }
    }
}
