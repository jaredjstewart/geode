/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.internal;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import org.apache.geode.distributed.internal.DistributionConfig;
import org.apache.geode.internal.logging.LogService;
import org.apache.geode.internal.util.CollectionUtils;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The delegating <tt>ClassLoader</tt> used by GemFire to load classes and other resources. This
 * <tt>ClassLoader</tt> delegates to any <tt>ClassLoader</tt>s added to the list of custom class
 * loaders, thread context <tt>ClassLoader</tt> s unless they have been excluded}, the
 * <tt>ClassLoader</tt> which loaded the GemFire classes, and finally the system
 * <tt>ClassLoader</tt>.
 * <p>
 * The thread context class loaders can be excluded by setting the system property
 * <tt>gemfire.excludeThreadContextClassLoader</tt>:
 * <ul>
 * <li><tt>-Dgemfire.excludeThreadContextClassLoader=true</tt>
 * <li><tt>System.setProperty("gemfire.excludeThreadContextClassLoader", "true");
 * </tt>
 * </ul>
 * <p>
 * Class loading and resource loading order:
 * <ul>
 * <li>1. Any custom loaders in the order they were added
 * <li>2. <tt>Thread.currentThread().getContextClassLoader()</tt> unless excludeTCCL == true
 * <li>3. <tt>ClassPathLoader.class.getClassLoader()</tt>
 * <li>4. <tt>ClassLoader.getSystemClassLoader()</tt> If the attempt to acquire any of the above
 * class loaders results in either a {@link java.lang.SecurityException SecurityException} or a
 * null, then that class loader is quietly skipped. Duplicate class loaders will be skipped.
 * @since GemFire 6.5.1.4
 */
public final class ClassPathLoader {
  /*
   * This class it not an extension of ClassLoader due to reasons outlined in
   * https://svn.gemstone.com/trac/gemfire/ticket/43080
   * 
   * See also http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-5.html
   */
  private static final Logger logger = LogService.getLogger();

  public static final String EXCLUDE_TCCL_PROPERTY =
      DistributionConfig.GEMFIRE_PREFIX + "excludeThreadContextClassLoader";
  public static final boolean EXCLUDE_TCCL_DEFAULT_VALUE = false;
  private boolean excludeTCCL;

  public static final String EXT_LIB_DIR_PARENT_PROPERTY =
      DistributionConfig.GEMFIRE_PREFIX + "ClassPathLoader.EXT_LIB_DIR";

  // This calculates the location of the extlib directory relative to the
  // location of the gemfire jar file. If for some reason the ClassPathLoader
  // class is found in a directory instead of a JAR file (as when testing),
  // then it will be relative to the location of the root of the package and
  // class.
  private static final ClassLoader PARENT_CLASSLOADER = buildExtLibClassLoader();

  private URLClassLoader classLoaderForDeployedJars;
  private JarDeployer jarDeployer;

  private static final AtomicReference<ClassPathLoader> latest =
      new AtomicReference<>();


  public ClassPathLoader(boolean excludeTCCL) {
    this.excludeTCCL = excludeTCCL;
    this.jarDeployer = new JarDeployer();
    this.classLoaderForDeployedJars = new URLClassLoader(new URL[]{},PARENT_CLASSLOADER);
  }

  public ClassPathLoader(boolean excludeTCCL, File workingDir) {
    this.excludeTCCL = excludeTCCL;
    this.jarDeployer = new JarDeployer(workingDir);
    this.classLoaderForDeployedJars = new URLClassLoader(new URL[]{},PARENT_CLASSLOADER);
  }

  public static ClassPathLoader setLatestToDefault() {
    latest.set(new ClassPathLoader(Boolean.getBoolean(EXCLUDE_TCCL_PROPERTY)));
    return latest.get();
  }

  public static ClassPathLoader setLatestToDefault(File workingDir) {
    latest.set(new ClassPathLoader(Boolean.getBoolean(EXCLUDE_TCCL_PROPERTY), workingDir));
    return latest.get();
  }

  public JarDeployer getJarDeployer() {
    return this.jarDeployer;
  }

  private static ClassLoader buildExtLibClassLoader() {
    try {
      File EXT_LIB_DIR = getExtLibDir();
      if (EXT_LIB_DIR.exists()) {
        if (!EXT_LIB_DIR.isDirectory() || !EXT_LIB_DIR.canRead()) {
          logger.warn("Cannot read from directory when attempting to load JAR files: {}",
              EXT_LIB_DIR.getAbsolutePath());
        } else {
          URL[] extLibJarURLs = getJarURLsFromFiles(EXT_LIB_DIR).stream().toArray(URL[]::new);
          return new URLClassLoader(extLibJarURLs, ClassPathLoader.class.getClassLoader());
        }
      }
    } catch (SecurityException sex) {
      // Nothing to do, just don't add it
    }

    return ClassPathLoader.class.getClassLoader();
  }

  /**
   * Starting at the files or directories identified by 'files', search for valid JAR files and
   * return a list of their URLs. Sub-directories will also be searched.
   * @param files Files or directories to search for valid JAR content.
   * @return A list of URLs for all JAR files found.
   */
  private static List<URL> getJarURLsFromFiles(final File... files) {
    final List<URL> urls = new ArrayList<URL>();

    Assert.assertTrue(files != null, "file list cannot be null");

    for (File file : files) {
      if (file.exists()) {
        if (file.isDirectory()) {
          urls.addAll(getJarURLsFromFiles(file.listFiles()));
        } else {
          if (!DeployedJar.hasValidJarContent(file)) {
            logger.warn("Invalid JAR content when attempting to create ClassLoader for file: {}",
                file.getAbsolutePath());
            continue;
          }

          try {
            urls.add(file.toURI().toURL());
          } catch (MalformedURLException muex) {
            logger.warn(
                "Encountered invalid URL when attempting to create ClassLoader for file: {}:{}",
                file.getAbsolutePath(), muex.getMessage());
            continue;
          }
        }
      }
    }

    return urls;
  }

  // This is exposed for testing.
  static ClassPathLoader createWithDefaults(final boolean excludeTCCL) {
    return new ClassPathLoader(excludeTCCL);
  }

  public ClassPathLoader addOrReplace(DeployedJar deployedJar) {
    final boolean isDebugEnabled = logger.isTraceEnabled();
    if (isDebugEnabled) {
      logger.trace("adding jar: {}", deployedJar.toString());
    }

    this.classLoaderForDeployedJars = jarDeployer.rebuildClassLoaderForDeployedJars(PARENT_CLASSLOADER);
    return this;
  }



  public void remove(final String jarName) {
    final boolean isDebugEnabled = logger.isTraceEnabled();
    if (isDebugEnabled) {
      logger.trace("removing jar: {}", jarName);
    }

    this.classLoaderForDeployedJars = jarDeployer.rebuildClassLoaderForDeployedJars(PARENT_CLASSLOADER);
  }

  public URL getResource(final String name) {
    final boolean isDebugEnabled = logger.isTraceEnabled();
    if (isDebugEnabled) {
      logger.trace("getResource({})", name);
    }

    for (ClassLoader classLoader : getClassLoaders()) {
      if (isDebugEnabled) {
        logger.trace("getResource trying: {}", classLoader);
      }
      try {
        URL url = classLoader.getResource(name);

        if (url != null) {
          if (isDebugEnabled) {
            logger.trace("getResource found by: {}", classLoader);
          }
          return url;
        }
      } catch (SecurityException e) {
        //try next classLoader
      }
    }

    return null;
  }

  public Class<?> forName(final String name) throws ClassNotFoundException {
    final boolean isDebugEnabled = logger.isTraceEnabled();
    if (isDebugEnabled) {
      logger.trace("forName({})", name);
    }

    for (ClassLoader classLoader : this.getClassLoaders()) {
      if (isDebugEnabled) {
        logger.trace("forName trying: {}", classLoader);
      }
      try {
        Class<?> clazz = Class.forName(name, true, classLoader);

        if (clazz != null) {
          if (isDebugEnabled) {
            logger.trace("forName found by: {}", classLoader);
          }
          return clazz;
        }
      } catch (SecurityException | ClassNotFoundException e) {

        //try next classLoader
      }
    }

    throw new ClassNotFoundException(name);
  }

  /**
   * See {@link Proxy#getProxyClass(ClassLoader, Class...)}
   */
  public Class<?> getProxyClass(final Class<?>[] classObjs) {
    IllegalArgumentException ex = null;

    for (ClassLoader classLoader : this.getClassLoaders()) {
      try {
        return Proxy.getProxyClass(classLoader, classObjs);
      } catch (SecurityException sex) {
        // Continue to next classloader
      } catch (IllegalArgumentException iaex) {
        ex = iaex;
        // Continue to next classloader
      }
    }

    if (ex != null) {
      throw ex;
    }
    return null;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder(getClass().getName());
    sb.append("@").append(System.identityHashCode(this)).append("{");
    sb.append(", excludeTCCL=").append(this.excludeTCCL);
    sb.append(", classLoaders=[");
    sb.append(
        this.getClassLoaders().stream().map(ClassLoader::toString).collect(joining(", ")));
    sb.append("]}");
    return sb.toString();
  }

  /**
   * Finds the resource with the given name. This method will first search the class loader of the
   * context class for the resource. That failing, this method will invoke
   * {@link #getResource(String)} to find the resource.
   * @param contextClass The class whose class loader will first be searched
   * @param name The resource name
   * @return A <tt>URL</tt> object for reading the resource, or <tt>null</tt> if the resource could
   * not be found or the invoker doesn't have adequate privileges to get the resource.
   */
  public URL getResource(final Class<?> contextClass, final String name) {
    if (contextClass != null) {
      URL url = contextClass.getResource(name);
      if (url != null) {
        return url;
      }
    }
    return getResource(name);
  }

  /**
   * Returns an input stream for reading the specified resource.
   *
   * <p>
   * The search order is described in the documentation for {@link #getResource(String)}.
   * </p>
   * @param name The resource name
   * @return An input stream for reading the resource, or <tt>null</tt> if the resource could not be
   * found
   */
  public InputStream getResourceAsStream(final String name) {
    URL url = getResource(name);
    try {
      return url != null ? url.openStream() : null;
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Returns an input stream for reading the specified resource.
   * <p>
   * The search order is described in the documentation for {@link #getResource(Class, String)}.
   * @param contextClass The class whose class loader will first be searched
   * @param name The resource name
   * @return An input stream for reading the resource, or <tt>null</tt> if the resource could not be
   * found
   */
  public InputStream getResourceAsStream(final Class<?> contextClass, final String name) {
    if (contextClass != null) {
      InputStream is = contextClass.getResourceAsStream(name);
      if (is != null) {
        return is;
      }
    }
    return getResourceAsStream(name);
  }

  /**
   * Finds all the resources with the given name. This method will first search the class loader of
   * the context class for the resource before searching all other {@link ClassLoader}s.
   * @param contextClass The class whose class loader will first be searched
   * @param name The resource name
   * @return An enumeration of {@link java.net.URL <tt>URL</tt>} objects for the resource. If no
   * resources could be found, the enumeration will be empty. Resources that the class loader
   * doesn't have access to will not be in the enumeration.
   * @throws IOException If I/O errors occur
   * @see ClassLoader#getResources(String)
   */
  public Enumeration<URL> getResources(final Class<?> contextClass, final String name)
      throws IOException {
    final boolean isDebugEnabled = logger.isTraceEnabled();

    if (isDebugEnabled) {
      logger.trace(new StringBuilder("getResources(").append(name).append(")"));
    }

    final LinkedHashSet<URL> urls = new LinkedHashSet<URL>();

    try {
      if (contextClass != null) {
        CollectionUtils.addAll(urls, contextClass.getClassLoader().getResources(name));
      }
    } catch (IOException ignore) {
      // ignore and search others
    }

    Enumeration<URL> resources = null;
    ClassLoader tccl = null;
    if (!excludeTCCL) {
      tccl = Thread.currentThread().getContextClassLoader();
    }

    IOException ioException = null;
    for (ClassLoader classLoader : this.getClassLoaders()) {
      ioException = null; // reset to null for next ClassLoader

      try {
        if (isDebugEnabled) {
          logger.trace("getResources trying classLoader: {}", classLoader);
        }
        resources = classLoader.getResources(name);
        if (resources != null && resources.hasMoreElements()) {
          if (logger.isTraceEnabled()) {
            logger.trace(
                new StringBuilder("getResources found by classLoader: ").append(classLoader));
          }
          CollectionUtils.addAll(urls, resources);
        }
      } catch (IOException ignore) {
        ioException = ignore;
        // Continue to next ClassLoader
      }
    }

    if (ioException != null) {
      if (isDebugEnabled) {
        logger.trace("getResources throwing IOException");
      }
      throw ioException;
    }

    if (isDebugEnabled) {
      logger.trace("getResources returning empty enumeration");
    }

    return Collections.enumeration(urls);
  }

  /**
   * Finds all the resources with the given name.
   * @param name The resource name
   * @return An enumeration of {@link java.net.URL <tt>URL</tt>} objects for the resource. If no
   * resources could be found, the enumeration will be empty. Resources that the class loader
   * doesn't have access to will not be in the enumeration.
   * @throws IOException If I/O errors occur
   * @see ClassLoader#getResources(String)
   */
  public Enumeration<URL> getResources(String name) throws IOException {
    return getResources(null, name);
  }

  private List<ClassLoader> getClassLoaders() {
    ArrayList<ClassLoader> classLoaders = new ArrayList<>();

    if (!excludeTCCL) {
      classLoaders.add(Thread.currentThread().getContextClassLoader());
    }

    if (classLoaderForDeployedJars != null) {
      classLoaders.add(classLoaderForDeployedJars);
    }

    return classLoaders;
  }

  /**
   * Wrap this {@link ClassPathLoader} with a {@link ClassLoader} facade.
   * @return {@link ClassLoader} facade.
   * @since GemFire 8.1
   */
  public ClassLoader asClassLoader() {
    return new ClassLoader() {
      @Override
      public Class<?> loadClass(String name) throws ClassNotFoundException {
        return ClassPathLoader.this.forName(name);
      }

      @Override
      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        return ClassPathLoader.this.forName(name);
      }

      @Override
      public URL getResource(String name) {
        return ClassPathLoader.this.getResource(name);
      }

      @Override
      public Enumeration<URL> getResources(String name) throws IOException {
        return ClassPathLoader.this.getResources(name);
      }

      @Override
      public InputStream getResourceAsStream(String name) {
        return ClassPathLoader.this.getResourceAsStream(name);
      }
    };
  }

  public static ClassPathLoader getLatest() {
    if (latest.get() == null) {
      setLatestToDefault();
    }
    return latest.get();
  }

  protected static File getExtLibDir() {
    final String EXT_LIB_DIR_PARENT_DEFAULT =
        ClassPathLoader.class.getProtectionDomain().getCodeSource().getLocation().getPath();

    return new File(
        (new File(System.getProperty(EXT_LIB_DIR_PARENT_PROPERTY, EXT_LIB_DIR_PARENT_DEFAULT)))
            .getParent(),
        "ext");
  }

  /**
   * Helper method equivalent to <code>ClassPathLoader.getLatest().asClassLoader();</code>.
   * @return {@link ClassLoader} for current {@link ClassPathLoader}.
   * @since GemFire 8.1
   */
  public static final ClassLoader getLatestAsClassLoader() {
    return latest.get().asClassLoader();
  }

}
