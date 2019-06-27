package com.zhangyue.we.anoprocesser.xml;

import com.zhangyue.we.anoprocesser.FileFilter;
import com.zhangyue.we.anoprocesser.Log;
import com.zhangyue.we.anoprocesser.Util;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * @author chengwei 2018/8/10
 */
public class LayoutManager {

    private static LayoutManager sInstance;
    private File mRootFile;
    private String mPackageName;
    private Filer mFiler;
    private HashMap<String, String> mMap;
    /**
     * key is styleName,value is style
     */
    private HashMap<String, Style> mStyles;
    /**
     * key is layoutName,value is javaName
     */
    private HashMap<String, String> mTranslateMap;

    /**
     * key is attrName,value is attr
     */
    private HashMap<String, Attr> mAttrs;

    /**
     * key is layoutName,value is layout list,like layout-land/main.xml,layout-v23/main.xml
     */
    private HashMap<String, ArrayList<File>> mLayouts;

    private LayoutManager() {
        mMap = new HashMap<>();
        mTranslateMap = new HashMap<>();
        mLayouts = new HashMap<>();
    }

    public static LayoutManager instance() {
        if (sInstance == null) {
            synchronized (LayoutManager.class) {
                if (sInstance == null) {
                    sInstance = new LayoutManager();
                }
            }
        }
        return sInstance;
    }

    public void setFiler(Filer filer) {
        this.mLayouts.clear();
        this.mFiler = filer;
        this.mRootFile = getRootFile();
        this.findPackageName();
        this.mAttrs = new Attr2FuncReader(new File(mRootFile, "X2C_CONFIG.xml")).parse();
    }

    public String translate(String layoutName) {
        if (mLayouts.size() == 0) {
            mLayouts = scanLayouts(mRootFile);
        }
        String fileName = null;
        if (mMap.containsKey(layoutName)) {
            fileName = mMap.get(layoutName);
        } else {
            ArrayList<File> layouts = mLayouts.get(layoutName);
            if (layouts != null) {
                Util.sortLayout(layouts);
                ArrayList<String> javaNames = new ArrayList<>();
                for (File file : layouts) {
                    LayoutReader reader = new LayoutReader(file, layoutName, mFiler, mPackageName);
                    fileName = reader.parse();
                    javaNames.add(fileName);
                    mMap.put(layoutName, fileName);
                }

                MapWriter mapWriter = new MapWriter(layouts, javaNames, mFiler);
                mapWriter.write();
            }
        }
        if (fileName != null) {
            mTranslateMap.put(fileName + ".java", layoutName + ".xml");
        }
        return fileName;
    }

    private HashMap<String, ArrayList<File>> scanLayouts(File root) {
        return new FileFilter(root)
                .include("layout")
                .include("layout-land")
                .include("layout-v28")
                .include("layout-v27")
                .include("layout-v26")
                .include("layout-v25")
                .include("layout-v24")
                .include("layout-v23")
                .include("layout-v22")
                .include("layout-v21")
                .include("layout-v20")
                .include("layout-v19")
                .include("layout-v18")
                .include("layout-v17")
                .include("layout-v16")
                .include("layout-v15")
                .include("layout-v14")
                .exclude("build")
                .exclude("java")
                .exclude("libs")
                .exclude("mipmap")
                .exclude("values")
                .exclude("drawable")
                .exclude("anim")
                .exclude("color")
                .exclude("menu")
                .exclude("raw")
                .exclude("xml")
                .filter();
    }

    public Style getStyle(String name) {
        if (name == null) {
            return null;
        }
        if (mStyles == null) {
            mStyles = new HashMap();
            new StyleReader(mRootFile, mStyles).parse();
        }
        return mStyles.get(name);
    }

    public void printTranslate() {
        if (mTranslateMap.size() == 0) {
            return;
        }
        int maxTab = 0;
        int tabCount;
        for (String layoutName : mTranslateMap.values()) {
            tabCount = layoutName.length() / 4 + 1;
            if (tabCount > maxTab) {
                maxTab = tabCount;
            }
        }

        StringBuilder stringBuilder;
        String layoutName;
        for (String javaName : mTranslateMap.keySet()) {
            layoutName = mTranslateMap.get(javaName);
            tabCount = layoutName.length() / 4;
            stringBuilder = new StringBuilder(layoutName);
            if (tabCount < maxTab) {
                for (int j = 0; j < maxTab - tabCount; j++) {
                    stringBuilder.append("\t");
                }
            }
            Log.w(String.format("%s->\t%s", stringBuilder.toString(), javaName));
        }
        mTranslateMap.clear();
    }


    private File getRootFile() {
        try {
            JavaFileObject fileObject = mFiler.createSourceFile("bb");
            String path = URLDecoder.decode(fileObject.toUri().toString(), "utf-8");
            String preFix = "file:/";
            if (path.startsWith(preFix)) {
                path = path.substring(preFix.length() - 1);
            }
            File file = new File(path);
            while (!file.getName().equals("build")) {
                file = file.getParentFile();
            }
            return file.getParentFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    private void findPackageName() {
        String sep = File.separator;
        File androidMainfest = new File(mRootFile + sep + "src" + sep + "main" + sep + "AndroidManifest.xml");
        if (!androidMainfest.exists()) {
            androidMainfest = new File(mRootFile + sep + "build" + sep + "intermediates" + sep + "manifests"
                    + sep + "full" + sep + "debug" + sep + "AndroidManifest.xml");
        }
        SAXParser parser = null;
        try {
            parser = SAXParserFactory.newInstance().newSAXParser();
            parser.parse(androidMainfest, new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                    super.startElement(uri, localName, qName, attributes);
                    if (qName.equals("manifest")) {
                        mPackageName = attributes.getValue("package");
                    }
                }
            });
            parser.reset();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (parser != null) {
                    parser.reset();
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }

    }

    public HashMap<String, Attr> getAttrs() {
        return mAttrs;
    }

    private boolean isLibrary() {
        File file = new File(mRootFile, "build.gradle");
        BufferedReader fileReader = null;
        boolean isLibrary = true;
        try {
            fileReader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = fileReader.readLine()) != null) {
                int indexOfApplication = line.indexOf("'com.android.application'");
                if (indexOfApplication >= 0) {
                    int indexOfNote = line.indexOf("//");
                    if (indexOfNote == -1 || indexOfNote > indexOfApplication) {
                        isLibrary = false;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Util.close(fileReader);
        }
        return isLibrary;
    }

}
