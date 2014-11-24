/**
 * Copyright 2012 Google Inc. All Rights Reserved.
 */
package com.google.appengine.appcfg;

import static org.codehaus.plexus.util.StringUtils.isNotEmpty;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import com.google.appengine.SdkResolver;
import com.google.appengine.tools.admin.AppCfg;
import com.google.common.base.Joiner;

/**
 * Abstract class for supporting appcfg commands.
 *
 * @author Matt Stephenson <mattstep@google.com>
 */
public abstract class AbstractAppCfgMojo extends AbstractMojo implements Contextualizable {

  private static final String SECURITY_DISPATCHER_CLASS_NAME = "org.sonatype.plexus.components.sec.dispatcher.SecDispatcher";

  /**
   * Plexus container, needed to manually lookup components.
   * 
   * To be able to use Password Encryption
   * http://maven.apache.org/guides/mini/guide-encryption.html
   */
  protected PlexusContainer container;

  /**
   * The entry point to Aether, i.e. the component doing all the work.
   *
   * @component
   */

  protected RepositorySystem repoSystem;

  /**
   * The current repository/network configuration of Maven.
   *
   * @parameter default-value="${repositorySystemSession}"
   * @readonly
   */
  protected RepositorySystemSession repoSession;

  /**
   * The Maven settings reference.
   * 
   * @parameter expression="${settings}"
   * @required
   * @readonly
   */
  protected Settings settings;

  /**
   * The project's remote repositories to use for the resolution of project
   * dependencies.
   *
   * @parameter default-value="${project.remoteProjectRepositories}"
   * @readonly
   */
  protected List<RemoteRepository> projectRepos;

  /**
   * The project's remote repositories to use for the resolution of plugins and
   * their dependencies.
   *
   * @parameter default-value="${project.remotePluginRepositories}"
   * @readonly
   */
  protected List<RemoteRepository> pluginRepos;

  /**
   * The server to connect to.
   *
   * @parameter expression="${appengine.server}"
   */
  protected String server;

  /**
   * The server id in maven settings.xml to use for emailAccount(username) and
   * password when connecting to GAE.
   * 
   * If password present in settings "--passin" is set automatically.
   * 
   * @parameter expression="${gae.serverId}"
   */
  protected String serverId;

  /**
   * The username to use.
   *
   * @parameter expression="${appengine.email}"
   * @deprecated use maven settings.xml/server/username and "serverId" parameter
   */
  @Deprecated
  protected String email;

  /**
   * Override for the Host header setn with all RPCs.
   *
   * @parameter expression="${appengine.host}"
   */
  protected String host;

  /**
   * Proxies requests through the given proxy server. If --proxy_https is also
   * set, only HTTP will be proxied here, otherwise both HTTP and HTTPS will.
   *
   * @parameter expression="${appengine.proxyHost}"
   */
  protected String proxyHost;

  /**
   * Proxies HTTPS requests through the given proxy server.
   *
   * @parameter expression="${appengine.proxyHttps}"
   */
  protected String proxyHttps;

  /**
   * Do not save/load access credentials to/from disk.
   *
   * @parameter expression="${appengine.noCookies}"
   */
  protected boolean noCookies;

  /**
   * Always read the login password from stdin.
   *
   * @parameter expression="${appengine.passin}"
   */
  protected boolean passin;

  /**
   * Do not use HTTPS to communicate with the Admin Console.
   *
   * @parameter expression="${appengine.insecure}"
   */
  protected boolean insecure;

  /**
   * Override application id from appengine-web.xml or app.yaml.
   *
   * @parameter expression="${appengine.appId}"
   */
  protected String appId;

  /**
   * Override version from appengine-web.xml or app.yaml.
   *
   * @parameter expression="${appengine.version}"
   */
  protected String version;

  /**
   * Use OAuth2 instead of password auth. Defaults to true.
   *
   * @parameter default-value=true expression="${appengine.oauth2}"
   */
  protected boolean oauth2;

  /**
   * Split large jar files (> 10M) into smaller fragments.
   *
   * @parameter expression="${appengine.enableJarSplitting}"
   */
  protected boolean enableJarSplitting;

  /**
   * When --enable-jar-splitting is set, files that match the list of comma
   * separated SUFFIXES will be excluded from all jars.
   *
   * @parameter expression="${appengine.jarSplittingExcludes}"
   */
  protected String jarSplittingExcludes;

  /**
   * Do not delete temporary (staging) directory used in uploading.
   *
   * @parameter expression="${appengine.retainUploadDir}"
   */
  protected boolean retainUploadDir;

  /**
   * The character encoding to use when compiling JSPs.
   *
   * @parameter expression="${appengine.compileEncoding}"
   */
  protected boolean compileEncoding;

  /**
   * Number of days worth of log data to get. The cut-off point is midnight UTC.
   * Use 0 to get all available logs. Default is 1.
   *
   * @parameter expression="${appengine.numDays}"
   */
  protected Integer numDays;

  /**
   * Severity of app-level log messages to get. The range is 0 (DEBUG) through 4
   * (CRITICAL). If omitted, only request logs are returned.
   *
   * @parameter expression="${appengine.severity}"
   */
  protected String severity;

  /**
   * Append to existing file.
   *
   * @parameter expression="${appengine.append}"
   */
  protected boolean append;

  /**
   * Number of scheduled execution times to compute.
   *
   * @parameter expression="${appengine.numRuns}"
   */
  protected Integer numRuns;

  /**
   * Force deletion of indexes without being prompted.
   *
   * @parameter expression="${appengine.force}"
   */
  protected boolean force;

  /**
   * The name of the backend to perform actions on.
   *
   * @parameter expression="${appengine.backendName}"
   */
  protected String backendName;

  /**
   * Delete the JSP source files after compilation.
   *
   * @parameter expression="${appengine.deleteJsps}"
   */
  protected boolean deleteJsps;

  /**
   * Jar the WEB-INF/classes content.
   *
   * @parameter expression="${appengine.enableJarClasses}"
   */
  protected boolean enableJarClasses;

  /**
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  protected MavenProject project;

  /**
   * Instance id to for vm debug.
   *
   * @parameter expression="${appengine.instance}"
   */
  protected String instance;

  @Override
  public void contextualize(final Context context) throws ContextException {
    container = (PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY);
  }

  protected List<String> collectParameters() {
    List<String> arguments = new ArrayList<String>();

    if (server != null && !server.isEmpty()) {
      arguments.add("-s");
      arguments.add(server);
    }

    if (getServerSettings() != null) {
      arguments.add("-e");
      arguments.add(getServerSettings().getUsername());
    } else if (email != null && !email.isEmpty()) {
      arguments.add("-e");
      arguments.add(email);
    }

    if (host != null && !host.isEmpty()) {
      arguments.add("-H");
      arguments.add(host);
    }

    if (proxyHost != null && !proxyHost.isEmpty()) {
      arguments.add("--proxy=" + proxyHost);
    }

    if (proxyHttps != null && !proxyHttps.isEmpty()) {
      arguments.add("--proxy_https=" + proxyHttps);
    }

    if (noCookies) {
      arguments.add("--no_cookies");
    }

    if (passin || getServerSettings() != null) {
      arguments.add("--passin");
    }

    if (insecure) {
      arguments.add("--insecure");
    }

    if (appId != null && !appId.isEmpty()) {
      arguments.add("-A");
      arguments.add(appId);
    }

    if (version != null && !version.isEmpty()) {
      arguments.add("-V");
      arguments.add(version);
    }

    if (oauth2 && getServerSettings() == null) {
      arguments.add("--oauth2");
    }

    if (enableJarSplitting) {
      arguments.add("--enable_jar_splitting");
    }

    if (jarSplittingExcludes != null && !jarSplittingExcludes.isEmpty()) {
      arguments.add("--jar_splitting_excludes=" + jarSplittingExcludes);
    }

    if (retainUploadDir) {
      arguments.add("--retain_upload_dir");
    }

    if (compileEncoding) {
      arguments.add("--compile_encoding");
    }

    if (numDays != null) {
      arguments.add("--num_days=" + numDays.toString());
    }

    if (severity != null && !severity.isEmpty()) {
      arguments.add("--severity=" + severity);
    }

    if (append) {
      arguments.add("-a");
    }

    if (numRuns != null) {
      arguments.add("--num_runs=" + numRuns.toString());
    }

    if (force) {
      arguments.add("-f");
    }

    if (deleteJsps) {
      arguments.add("--delete_jsps");
    }

    if (enableJarClasses) {
      arguments.add("--enable_jar_classes");
    }

    return arguments;
  }

  protected void executeAppCfgCommand(String action, String appDir) throws MojoExecutionException {
    List<String> arguments = collectParameters();

    arguments.add(action);
    arguments.add(appDir);
    executeAppCfg(arguments);
  }

  protected void executeAppCfgBackendsCommand(String action, String appDir) throws MojoExecutionException {
    List<String> arguments = collectParameters();

    arguments.add("backends");
    arguments.add(action);
    arguments.add(appDir);
    arguments.add(backendName);
    executeAppCfg(arguments);
  }

  protected void resolveAndSetSdkRoot() throws MojoExecutionException {
    File sdkBaseDir = SdkResolver.getSdk(project, repoSystem, repoSession, pluginRepos, projectRepos);

    try {
      System.setProperty("appengine.sdk.root", sdkBaseDir.getCanonicalPath());
    } catch (IOException e) {
      throw new MojoExecutionException("Could not open SDK zip archive.", e);
    }
  }

  private void executeAppCfg(List<String> arguments) throws MojoExecutionException {
    getLog().info("Running " + Joiner.on(" ").join(arguments));
    String[] arg = arguments.toArray(new String[arguments.size()]);
    if (getServerSettings() != null) {
      forkPasswordExpectThread(arg, decryptPassword(getServerSettings().getPassword()));
    } else {
      try {
        AppCfg.main(arg);
      } catch (Exception ex) {
        throw new MojoExecutionException("Error executing appcfg command=" + arguments, ex);
      }
    }
  }

  private void forkPasswordExpectThread(final String[] args, final String password) {
    getLog().info("Use Settings configuration from server id {" + serverId + "}");
    // Parent for all threads created by AppCfg
    final ThreadGroup threads = new ThreadGroup("AppCfgThreadGroup");

    // Main execution Thread that belong to ThreadGroup threads
    final Thread thread = new Thread(threads, "AppCfgMainThread") {

      @Override
      public void run() {
        final PrintStream outOrig = System.out;
        final InputStream inOrig = System.in;

        try (PipedInputStream inReplace = new PipedInputStream(); OutputStream stdin = new PipedOutputStream(inReplace); ) {
          System.setIn(inReplace);
          

          System.setOut(new PrintStream(new PasswordExpectOutputStream(threads, outOrig, new Runnable() {
            @Override
            public void run() {
              try (BufferedWriter stdinWriter = new BufferedWriter(new OutputStreamWriter(stdin))){
                stdinWriter.write(password);
                stdinWriter.newLine();
                stdinWriter.flush();
              } catch (final IOException e) {
                getLog().error("Unable to enter password", e);
              }
            }
          }), true));
          AppCfg.main(args);
        } catch (IOException e) {
          getLog().error("Unable to redirect output", e);
        } catch (Throwable e) {
          getLog().error("Unable to execute AppCfg", e);
        } finally {
          System.setOut(outOrig);
          System.setIn(inOrig);
        }
      }
    };
    thread.start();
    try {
      thread.join();
    } catch (final InterruptedException e) {
      getLog().error("Interrupted waiting for process supervisor thread to finish", e);
    }
  }

  private String decryptPassword(final String password) {
    if (isNotEmpty(password)) {
      try {
        final Class<?> securityDispatcherClass = container.getClass().getClassLoader().loadClass(SECURITY_DISPATCHER_CLASS_NAME);
        final Object securityDispatcher = container.lookup(SECURITY_DISPATCHER_CLASS_NAME, "maven");
        final Method decrypt = securityDispatcherClass.getMethod("decrypt", String.class);

        return (String) decrypt.invoke(securityDispatcher, password);

      } catch (final Exception e) {
        getLog().warn("security features are disabled. Cannot find plexus security dispatcher", e);
      }
    }
    getLog().debug("password could not be decrypted");
    return password;
  }

  private Server getServerSettings() {
    if (serverId != null && !serverId.isEmpty()) {
      return settings.getServer(serverId);
    } else {
      return null;
    }
  }
}