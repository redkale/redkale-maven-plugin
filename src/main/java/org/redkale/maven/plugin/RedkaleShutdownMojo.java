/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.maven.plugin;

import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 *
 * @author zhangjx
 */
@Mojo(name = "shutdown", requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
public class RedkaleShutdownMojo extends AbstractMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            System.setProperty("CMD", "SHUTDOWN");
            org.redkale.boot.Application.main(null);
        } catch (Throwable t) {
            throw new MojoExecutionException("redkale shutdown error", t);
        }
    }
}
