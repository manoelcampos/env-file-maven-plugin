package org.mjourard.envfile;


import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import io.github.cdimascio.dotenv.DotenvException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Goal which loads a dotenv (.env) file into the environment variables for the rest of the maven phase which the plugin is defined for
 */
@Mojo(name = "loadenv", defaultPhase = LifecyclePhase.TEST)
public class MyMojo extends AbstractMojo {
    /**
     * Directory of the env file.
     * TODO: redo default directory to look in root of directory where pom.xml exists.
     * ${project.basedir} should work but is evaluating to empty
     */
    @Parameter(property = "envFileDirectory", required = true)
    private String envFileDirectory;

    /**
     * Name of the env file, including the extension if it has one
     *
     */
    @Parameter(defaultValue = ".env", property = "envFileName", required = true)
    private String envFileName;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (envFileDirectory == null || envFileDirectory.isEmpty()) {
            throw new MojoFailureException("env file directory was empty");
        }

        if (envFileName == null || envFileName.isEmpty()) {
            throw new MojoFailureException("env file name was empty");
        }

        String tempEnvFileDirectory =  evaluatePath(envFileDirectory);
        getLog().info("Loading env file from '" + makePathDirectory(tempEnvFileDirectory) + envFileName + "'");

        Dotenv dotenv = null;

        try {
             dotenv = Dotenv.configure()
                    .directory(tempEnvFileDirectory)
                    .filename(envFileName)
                    .systemProperties()
                    .load();
        } catch (DotenvException dee) {
            throw new MojoExecutionException("Error while loading env file", dee);
        }

        Map<String, String> newEnv = new HashMap<>();
        for(DotenvEntry entry : dotenv.entries()) {
            newEnv.put(entry.getKey(), entry.getValue());
        }

        try {
            setEnv(newEnv);
        } catch (Exception e) {
            throw new MojoExecutionException("Error while loading environment variables from parsed env file", e);
        }
    }

    private String evaluatePath(String path) {
        return FileSystems.getDefault().getPath(path).normalize().toAbsolutePath().toString();
    }

    public static String makePathDirectory(String path) {
        if (Files.isDirectory(Paths.get(path))) {
            return path;
        }
        return path + File.separator;
    }

    protected static void setEnv(Map<String, String> newenv) throws Exception {
        try {
            Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            theEnvironmentField.setAccessible(true);
            Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
            env.putAll(newenv);
            Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
            theCaseInsensitiveEnvironmentField.setAccessible(true);
            Map<String, String> cienv = (Map<String, String>)     theCaseInsensitiveEnvironmentField.get(null);
            cienv.putAll(newenv);
        } catch (NoSuchFieldException e) {
            Class[] classes = Collections.class.getDeclaredClasses();
            Map<String, String> env = System.getenv();
            for(Class cl : classes) {
                if("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
                    Field field = cl.getDeclaredField("m");
                    field.setAccessible(true);
                    Object obj = field.get(env);
                    Map<String, String> map = (Map<String, String>) obj;
                    map.clear();
                    map.putAll(newenv);
                }
            }
        }
    }
}
