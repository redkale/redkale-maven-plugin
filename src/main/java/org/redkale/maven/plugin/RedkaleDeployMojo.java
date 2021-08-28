/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.maven.plugin;

import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 *
 * @author zhangjx
 */
@Execute(phase = LifecyclePhase.PACKAGE)
@Mojo(name = "deploy", threadSafe = true)
public class RedkaleDeployMojo extends AbstractMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        
    }
}
