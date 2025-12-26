package com.yoloho.enhanced.common.util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);
    private final static String resouceFilePrefixOld = "/USER_DIR";
    private final static String resouceFilePrefixNew = "${user_dir}";
    private final static Pattern filenamePattern = Pattern.compile("[0-9a-zA-Z.\\u4e00-\\u9fa5_\\-|\\[\\]=+()@!~`'\":;><,]+$");

	/**
	 * 获取文件的扩展名
	 * @param fileName
	 * @return
	 */
	public static String getExtension(String fileName) {
		return fileName.indexOf(".") != -1 ? fileName.substring(fileName.lastIndexOf(".") + 1) : null;
	}
	
	/**
	 * 获取路径中所指的文件名，如果没取到文件名，返回空字符串（不是null）
	 * @param filenameWithPath
	 * @return
	 */
	public static String getFilename(String filenameWithPath) {
	    Matcher matcher = filenamePattern.matcher(filenameWithPath);
	    if (matcher.find()) {
	        return matcher.group();
	    }
	    return "";
	}
	
	/**
	 * 根据当前线程获取默认的classloader，
	 * 因为暂时不明确应该放在哪里，这里暂时先私有
	 * @return
	 */
	private static ClassLoader getDefaultClassLoader() {
        ClassLoader cl = null;
        try {
            cl = Thread.currentThread().getContextClassLoader();
        }
        catch (Throwable ex) {
            // Cannot access thread context ClassLoader - falling back...
        }
        if (cl == null) {
            // No thread context class loader -> use class loader of this class.
            cl = FileUtil.class.getClassLoader();
            if (cl == null) {
                // getClassLoader() returning null indicates the bootstrap ClassLoader
                try {
                    cl = ClassLoader.getSystemClassLoader();
                }
                catch (Throwable ex) {
                    // Cannot access system ClassLoader - oh well, maybe the caller can live with null...
                }
            }
        }
        return cl;
    }
	
	/**
	 * 基于上述的道理暂先私有
	 * @param resourceUrl
	 * @param description
	 * @return
	 * @throws FileNotFoundException
	 */
	private static File getFile(URL resourceUrl, String description) throws FileNotFoundException, IOException {
        if (resourceUrl == null) {
            return null;
        }
        if ("file".equals(resourceUrl.getProtocol())) {
            try {
                return new File(toURI(resourceUrl).getSchemeSpecificPart());
            }
            catch (URISyntaxException ex) {
                // Fallback for URLs that are not valid URIs (should hardly ever happen).
                return new File(resourceUrl.getFile());
            }
        } else {
            // 非file文件，尝试转存生成临时文件
            File tempFile = File.createTempFile("temp", ".tmp");
            tempFile.deleteOnExit();
            try (FileOutputStream out = new FileOutputStream(tempFile); 
                    InputStream inputStream = resourceUrl.openStream();) {
                byte[] buffer = new byte[8129];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            logger.debug("createTempFile success, oriFile:{}", resourceUrl);
            return tempFile;
        }
    }
	
	/**
	 * 转换url中的空格
	 * @param url
	 * @return
	 * @throws URISyntaxException
	 */
	private static URI toURI(URL url) throws URISyntaxException {
        return toURI(url.toString());
    }

    /**
     * 转换url中的空格
     * @param location
     * @return
     * @throws URISyntaxException
     */
    private static URI toURI(String location) throws URISyntaxException {
        return new URI(StringUtils.replace(location, " ", "%20"));
    }
    
	public static File getFileFromVariousPlace(String path) {
	    if (StringUtils.isEmpty(path)) {
	        return null;
	    }
	    boolean loaded = false;
	    logger.info("开始重新定位资源 {}", path);
	    String filename = getFilename(path);
	    String pathReal = null;
	    String userDir = System.getProperty("user.dir");
	    String userHome = System.getProperty("user.home");
	    String tmpDir = System.getProperty("java.io.tmpdir"); //这个不清楚有无安全隐患
	    String catalinaBase = System.getProperty("catalina.base");
	    String[] candidateBases = new String[] {userDir, userHome, tmpDir, catalinaBase};
	    File file = null;
        if (path.startsWith(resouceFilePrefixOld)) {
            pathReal = path.substring(resouceFilePrefixOld.length());
        } else if (path.startsWith(resouceFilePrefixNew)) {
            pathReal = path.substring(resouceFilePrefixNew.length());
        } else {
            pathReal = path;
        }
        int cur = 0;
        while (cur < candidateBases.length) {
            if (!loaded) {
                try {
                    String baseDir = candidateBases[cur ++];
                    if (StringUtils.isEmpty(baseDir)) {
                        continue;
                    }
                    String fullpath = String.format("%s%s%s", baseDir, File.separatorChar, pathReal);
                    file = new File(fullpath);
                    if (file.exists()) {
                        logger.info("重新定位资源 {} 成功", fullpath);
                        loaded = true;
                        break;
                    }
                } catch (Exception e) {
                }
            }
        }
        ClassLoader cl = getDefaultClassLoader();
        if (!loaded) {
            //try to load from classpath
            logger.info("重定位失败，尝试从classpath中加载资源 pathReal: {}", pathReal);
            try {
                logger.debug("cl:{}",cl);
                URL url = (cl != null ? cl.getResource(pathReal) : ClassLoader.getSystemResource(pathReal));
                logger.debug("cl url:{}",url);
                if (url != null) {
                    logger.debug("cl url: protocol:{}, path:{}, authority:{}",url.getProtocol(), url.getPath(), url.getAuthority());
                    file = getFile(url, filename);
                    logger.debug("cl file: {}",file);
                    if (file != null) {
                        loaded = true;
                        logger.info("尝试从classpath中加载资源成功: {}", pathReal);
                    }
                }
            } catch (Exception e) {
            }
        }
        if (!loaded) {
            try {
                logger.info("重定位失败，尝试从classpath中加载资源 filename: {}", filename);
                URL url = (cl != null ? cl.getResource(filename) : ClassLoader.getSystemResource(filename));
                logger.debug("cl url: {}",url);
                if (url != null) {
                    logger.debug("cl url: protocol:{}, path:{}, authority:{}",url.getProtocol(), url.getPath(), url.getAuthority());
                    file = getFile(url, filename);
                    logger.debug("cl file:{}",file);
                    if (file != null) {
                        loaded = true;
                        logger.info("尝试从classpath中加载资源成功: {}", filename);
                    }
                }
            } catch (Exception e) {
            }
        }
        if (loaded) {
            return file;
        }
        return null;
	}

	/**
	 * 基于毫秒的唯一ID
	 * @return
	 */
	public static String uniqId() {
		return System.nanoTime() + "";
	}
}
