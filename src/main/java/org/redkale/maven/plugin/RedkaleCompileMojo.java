/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.maven.plugin;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.redkale.boot.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.*;

/**
 * native-image --initialize-at-run-time=java.security.SecureRandom -H:+ReportExceptionStackTraces --no-fallback -jar target/redkale-2.5.0-SNAPSHOT.jar
 *
 * @author zhangjx
 */
@Mojo(name = "compile", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class RedkaleCompileMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true, required = true)
    private File outputDirectory;

    @Parameter(defaultValue = "${project.artifact}", readonly = true, required = true)
    private Artifact projectArtifact;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter
    protected List<String> nativeimageArgs;

    @Parameter
    protected Boolean skipCopyConf;  //是否跳过复制conf到jar中去

    @Override
    @SuppressWarnings("unchecked")
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (outputDirectory.isDirectory()) {
            projectArtifact.setFile(outputDirectory);
        }
        try {
            List<String> paths = project.getCompileClasspathElements();
            URL[] urls = new URL[paths.size()];
            for (int i = 0; i < urls.length; i++) {
                getLog().debug("redkale.compile.path[" + i + "] = " + paths.get(i));
                urls[i] = new File(paths.get(i)).toURI().toURL();
            }
            RedkaleClassLoader contextLoader = new RedkaleClassLoader(urls, Thread.currentThread().getContextClassLoader());
            Thread.currentThread().setContextClassLoader(contextLoader);

            new PrepareCompiler().run();
            Logger.getLogger("com.google.inject.internal").setLevel(Level.INFO);

            //启动结束后
            final File nativeImageDir = new File(outputDirectory, "META-INF" + File.separatorChar + "native-image"
                + File.separatorChar + projectArtifact.getGroupId() + File.separatorChar + projectArtifact.getArtifactId());
            nativeImageDir.mkdirs();
            if (skipCopyConf == null || !skipCopyConf) {
                //复制conf目录
                File confFile = new File(project.getBasedir(), "conf");
                if (confFile.isDirectory()) {
                    File destFile = new File(outputDirectory, RedkaleClassLoader.RESOURCE_CACHE_CONF_PATH);
                    copyFile(confFile, destFile);
                }
            }

            { //动态字节码写入文件
                final Set<String> classLoadSet = new HashSet<>();
                final Map<String, byte[]> classBytesMap = new LinkedHashMap<>();
                RedkaleClassLoader.forEachDynClass(classBytesMap::put);
                for (Map.Entry<String, byte[]> en : classBytesMap.entrySet()) {
                    classLoadSet.add(en.getKey());
                    File file = new File(outputDirectory, en.getKey().replace('.', File.separatorChar) + ".class");
                    file.getParentFile().mkdirs();
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        out.write(en.getValue());
                        out.flush();
                        out.close();
                    }
                }
                RedkaleClassLoader.forEachServiceLoader((k, v) -> {
                    classLoadSet.add(k);
                });
                RedkaleClassLoader.forEachReflection((k, v) -> {
                    classLoadSet.add(k);
                });
                List<String> classLoadList = new ArrayList<>(classLoadSet);
                Collections.sort(classLoadList);
                File file = new File(outputDirectory, RedkaleClassLoader.RESOURCE_CACHE_CLASSES_PATH);
                file.getParentFile().mkdirs();
                try (FileOutputStream out = new FileOutputStream(file)) {
                    for (String v : classLoadList) {
                        out.write((v + "\r\n").getBytes(StandardCharsets.UTF_8));
                    }
                    out.flush();
                    out.close();
                }
            }
            { //反射写入文件
                final File reflectionFile = new File(nativeImageDir, "reflect-config.json");
                final Map<String, Object> reflectionMap = new TreeMap<>();
                RedkaleClassLoader.forEachReflection(reflectionMap::put);
                //解决 by: java.lang.NoSuchFieldException: producerIndex 问题
                try {
                    contextLoader.loadClass("io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueue");

                    reflectionMap.put("io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueueProducerIndexField",
                        Utility.ofMap("name", "io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueueProducerIndexField",
                            "fields", Utility.ofArray(Utility.ofMap("name", "producerIndex", "allowUnsafeAccess", true)))
                    );
                    reflectionMap.put("io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueueProducerLimitField",
                        Utility.ofMap("name", "io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueueProducerLimitField",
                            "fields", Utility.ofArray(Utility.ofMap("name", "producerLimit", "allowUnsafeAccess", true)))
                    );
                    reflectionMap.put("io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueueConsumerIndexField",
                        Utility.ofMap("name", "io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueueConsumerIndexField",
                            "fields", Utility.ofArray(Utility.ofMap("name", "consumerIndex", "allowUnsafeAccess", true)))
                    );
                    reflectionMap.put("io.netty.util.internal.shaded.org.jctools.queues.BaseMpscLinkedArrayQueueProducerFields",
                        Utility.ofMap("name", "io.netty.util.internal.shaded.org.jctools.queues.BaseMpscLinkedArrayQueueProducerFields",
                            "fields", Utility.ofArray(Utility.ofMap("name", "producerIndex", "allowUnsafeAccess", true)))
                    );
                    reflectionMap.put("io.netty.util.internal.shaded.org.jctools.queues.BaseMpscLinkedArrayQueueColdProducerFields",
                        Utility.ofMap("name", "io.netty.util.internal.shaded.org.jctools.queues.BaseMpscLinkedArrayQueueColdProducerFields",
                            "fields", Utility.ofArray(Utility.ofMap("name", "producerLimit", "allowUnsafeAccess", true)))
                    );
                    reflectionMap.put("io.netty.util.internal.shaded.org.jctools.queues.BaseMpscLinkedArrayQueueConsumerFields",
                        Utility.ofMap("name", "io.netty.util.internal.shaded.org.jctools.queues.BaseMpscLinkedArrayQueueConsumerFields",
                            "fields", Utility.ofArray(Utility.ofMap("name", "consumerIndex", "allowUnsafeAccess", true)))
                    );

                    reflectionMap.put("io.netty.channel.socket.nio.NioSocketChannel",
                        Utility.ofMap("name", "io.netty.channel.socket.nio.NioSocketChannel", "allPublicConstructors", true,
                            "methods", Utility.ofArray(Utility.ofMap("name", "<init>", "parameterTypes", new String[0])))
                    );
                    reflectionMap.put("io.netty.buffer.AbstractByteBufAllocator",
                        Utility.ofMap("name", "io.netty.buffer.AbstractByteBufAllocator", "allDeclaredMethods", true)
                    );
                    reflectionMap.put("io.netty.buffer.AdvancedLeakAwareByteBuf",
                        Utility.ofMap("name", "io.netty.buffer.AdvancedLeakAwareByteBuf", "allDeclaredMethods", true)
                    );
                    reflectionMap.put("io.netty.util.ReferenceCountUtil",
                        Utility.ofMap("name", "io.netty.util.ReferenceCountUtil", "allDeclaredMethods", true)
                    );
                } catch (Throwable t) {
                }
                try (FileOutputStream out = new FileOutputStream(reflectionFile)) {
                    out.write(JsonConvert.root().convertToBytes(reflectionMap.values()));
                    out.flush();
                    out.close();
                }
            }
            { //资源写入文件
                final File resourceFile = new File(nativeImageDir, "resource-config.json");
                final Set<String> resourceSet = new HashSet<>();
                if (RedkaleClassLoader.RESOURCE_CACHE_CLASSES_PATH.indexOf('/') == 0) {
                    resourceSet.add(RedkaleClassLoader.RESOURCE_CACHE_CLASSES_PATH.substring(1));
                } else {
                    resourceSet.add(RedkaleClassLoader.RESOURCE_CACHE_CLASSES_PATH);
                }
                RedkaleClassLoader.forEachServiceLoader((k, v) -> {
                    resourceSet.add("META-INF/services/" + k);
                });
                RedkaleClassLoader.forEachResourcePath(v -> {
                    resourceSet.add(v);
                });
                final List<String> resourceNames = new ArrayList<>(resourceSet);
                Collections.sort(resourceNames);
                final List<Object> resourceList = new ArrayList<>();
                for (String v : resourceNames) {
                    resourceList.add(Utility.ofMap("pattern", v));
                }
                final List<Object> bundleList = new ArrayList<>();
                RedkaleClassLoader.forEachBundleResource((k, v) -> {
                    for (String e : v) {
                        bundleList.add(Utility.ofMap("name", k + "_" + e));
                    }
                });
                Map<String, Object> resmap = new TreeMap<>();
                if (!bundleList.isEmpty()) resmap.put("bundles", bundleList);
                resmap.put("resources", resourceList);
                try (FileOutputStream out = new FileOutputStream(resourceFile)) {
                    out.write(JsonConvert.root().convertToBytes(resmap));
                    out.flush();
                    out.close();
                }
            }
            { //反射写入文件
                final File propertiesFile = new File(nativeImageDir, "native-image.properties");
                final StringBuilder sb = new StringBuilder();
                sb.append(RedkaleClassLoader.class.getName());
                RedkaleClassLoader.forEachBuildClass(v -> sb.append(",").append(v));
                RedkaleClassLoader.forEachBuildPackage(v -> sb.append(",").append(v));
                final Properties props = new Properties();
                final Set<String> args = new LinkedHashSet<>();
                if (nativeimageArgs != null) args.addAll(nativeimageArgs);
                args.add("--enable-http");
                args.add("--enable-https");
                Set<String> sysPropertyNames = Set.of("jdk.", "path.", "java.", "file.", "os.", "line.", "sun.", "user.", "awt.", "graalvm.", "org.graalvm.");
                System.getProperties().forEach((x, y) -> {
                    if (Utility.find(sysPropertyNames, s -> x.toString().startsWith(s)) == null) { //不是系统环境变量
                        args.add("-D" + x + "=" + y);
                    }
                });
                args.add("--initialize-at-build-time=" + sb);
                props.put("Args", String.join(" ", args));
                try (FileOutputStream out = new FileOutputStream(propertiesFile)) {
                    props.store(out, null);
                    out.flush();
                    out.close();
                }
            }
        } catch (Exception e) {
            getLog().error(e);
            throw new MojoExecutionException("redkale compiler error", e);
        }

    }

    private static int copyFile(File src, File dest) throws Exception {
        if (!src.isFile() && !src.isDirectory()) return -1;
        if (src.isFile()) {
            dest.getParentFile().mkdirs();
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            if (!dest.isDirectory()) dest.mkdirs();
            String cachePath = RedkaleClassLoader.RESOURCE_CACHE_CONF_PATH;
            if (cachePath.indexOf('/') == 0) cachePath = cachePath.substring(1);
            if (!cachePath.endsWith("/")) cachePath += "/";
            final int len = src.getPath().length() + 1;
            for (File f : src.listFiles()) {
                String fname = f.getPath().substring(len);
                RedkaleClassLoader.putResourcePath(cachePath + fname.replace('\\', '/'));
                copyFile(f, new File(dest, fname));
            }
        }
        return 0;
    }
}
