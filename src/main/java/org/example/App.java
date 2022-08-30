package org.example;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import org.model.DirPath;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Hello world!
 */
public class App {

    Log log = Log.get(App.class);

    public static void main(String[] args) {
        App app = new App();
        app.compare();
    }


    public DirPath getDirPath() {
        ObjectMapper objectMapper = new YAMLMapper();
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream("application.yml");
        try {
            DirPath dirPath = objectMapper.readValue(resourceAsStream, new TypeReference<DirPath>() {
            });
            return dirPath;
        } catch (IOException e) {
            throw new RuntimeException("读取配置文件失败");
        }
    }

    public void compare() {
        DirPath dirPath = getDirPath();
        Map<String, Pair<File, File>> needCompare = compareZipFile(dirPath);
        compareFileContent(needCompare, dirPath);
    }


    /**
     * 比较文件中的每个txt内容
     *
     * @param needCompare
     */
    public void compareFileContent(Map<String, Pair<File, File>> needCompare, DirPath dirPath) {
        for (Map.Entry<String, Pair<File, File>> mapFIle : needCompare.entrySet()) {
            String key = mapFIle.getKey();
            Pair<File, File> value = mapFIle.getValue();
            File sourceFile = value.getKey();
            File targetFile = value.getValue();

            File sourceFileFolder = ZipUtil.unzip(sourceFile);
            File targetFileFolder = ZipUtil.unzip(targetFile);


            // 比较文件内容
            List<File> source = FileUtil.loopFiles(sourceFileFolder);
            List<File> target = FileUtil.loopFiles(targetFileFolder);

            for (File s : source) {
                for (File t : target) {
                    if (s.getName().equals(t.getName())) {
                        String md5s = DigestUtil.md5Hex(s);
                        String md5t = DigestUtil.md5Hex(t);
                        if (StrUtil.equals(md5s, md5t)) {
                            log.info("{}文件中内容一致", s.getName());
                        } else {
                            // 文件差异对比
                            List<String> sourceTxt = FileUtil.readLines(s, StrUtil.isBlank(dirPath.getCharset()) ? Charset.forName(dirPath.getCharset()) : Charset.defaultCharset());
                            List<String> targetTxt = FileUtil.readLines(t, StrUtil.isBlank(dirPath.getCharset()) ? Charset.forName(dirPath.getCharset()) : Charset.defaultCharset());
                            Patch<String> diff = DiffUtils.diff(sourceTxt, targetTxt);
                            List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(s.getName(), t.getName(), sourceTxt, diff, 0);
                            unifiedDiff.forEach(System.out::println);
                        }
                    }
                }
            }

            FileUtil.del(sourceFileFolder);
            FileUtil.del(targetFileFolder);
        }

    }


    /**
     * 比对md5
     *
     * @param dirPath
     */
    public Map<String, Pair<File, File>> compareZipFile(DirPath dirPath) {
        FileFilter fileFilter = (file) -> StrUtil.subPre(file.getName(), 8).equals(dirPath.getTradeDate());

        List<File> sourceFile = FileUtil.loopFiles(dirPath.getSource(), fileFilter);
        List<File> targetFile = FileUtil.loopFiles(dirPath.getTarget(), fileFilter);
        Map<String, File> targetMap = targetFile.stream().collect(Collectors.toMap(item -> StrUtil.subSuf(item.getName(), 40), item -> item));

        Map<String, Pair<File, File>> needCompare = new LinkedHashMap<>();
        for (File file : sourceFile) {
            String fileKey = StrUtil.subSuf(file.getName(), 40);
            String sourceMd5 = DigestUtil.md5Hex(file);
            File tgFile = targetMap.get(fileKey);
            if (Objects.isNull(tgFile)) {
                log.info("target文件夹中未生成:{}", fileKey);
                continue;
            }
            String targetMd5 = DigestUtil.md5Hex(tgFile);
            if (StrUtil.equals(targetMd5, sourceMd5)) {
                log.info("文件md5一致:{}", fileKey);
            } else {
                Pair<File, File> pairCompare = Pair.of(file, tgFile);
                needCompare.put(fileKey, pairCompare);
            }
        }
        needCompare.forEach((key, value) -> {
            log.info("文件md5不一致:{}，需要比较文件内容", key);
        });
        return needCompare;
    }
}
