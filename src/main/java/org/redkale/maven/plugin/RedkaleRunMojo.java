/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.maven.plugin;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author zhangjx
 */
@Execute(phase = LifecyclePhase.PROCESS_CLASSES)
@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class RedkaleRunMojo extends AbstractMojo {

    /**
     * Location of the local repository.
     */
    @Parameter(defaultValue = "${localRepository}", required = true, readonly = true)
    private ArtifactRepository local;

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter
    protected String[] runArgs;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            org.redkale.boot.Application.main(runArgs);
        } catch (Throwable t) {
            throw new MojoExecutionException("redkale run error", t);
        }
    }
}
