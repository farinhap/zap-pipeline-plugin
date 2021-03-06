package com.vrondakis.zap.workflow;

import com.vrondakis.zap.ZapFailBuildAction;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import com.vrondakis.zap.ZapArchive;
import com.vrondakis.zap.ZapDriver;
import com.vrondakis.zap.ZapDriverController;

import hudson.model.Result;

import javax.annotation.Nonnull;

/**
 * Executor for archiveZap() function in Jenkinsfile
 */

public class ArchiveZapExecution extends DefaultStepExecutionImpl {
    private ArchiveZapStepParameters archiveZapStepParameters;

    public ArchiveZapExecution(StepContext context, ArchiveZapStepParameters parameters) {
        super(context);
        this.archiveZapStepParameters = parameters;
    }

    @Override
    public boolean start() {
        listener.getLogger().println("zap: Archiving results...");
        System.out.println("zap: Archiving results...");
        ZapDriver zapDriver = ZapDriverController.getZapDriver(this.run);

        zapDriver.setFailBuild(archiveZapStepParameters.getFailAllAlerts(), archiveZapStepParameters.getFailHighAlerts(),
                archiveZapStepParameters.getFailMediumAlerts(), archiveZapStepParameters.getFailLowAlerts());

        try {
            ZapArchive zapArchive = new ZapArchive(this.run);
            boolean archiveResult = zapArchive.archiveRawReport(this.run, this.job, this.listener,
                    archiveZapStepParameters.getFalsePositivesFilePath());
            if (!archiveResult) {
                listener.getLogger().println("zap: Failed to archive results");
                getContext().onSuccess(true);
                return true;
            }

            // If any of the fail run parameters are set to a value more than 1
            if (zapDriver.getFailBuild().values().stream().anyMatch(count -> count > 0)) {
                if (zapArchive.shouldFailBuild(this.listener)) {
                    listener.getLogger().println(
                            "zap: Number of detected ZAP alerts is too high, failing run. Check the ZAP scanning report");
                    run.setResult(Result.FAILURE);
                    getContext().onFailure(new Throwable(
                            "zap: Number of detected ZAP alerts is too high, failing run. Check the ZAP scanning report"));

                    // Red text on build that shows the build has failed due to ZAP
                    this.run.addAction(new ZapFailBuildAction());
                    return false;
                }
            }

        } finally {
            boolean success = ZapDriverController.shutdownZap(this.run);
            if (!success)
                listener.getLogger().println("zap: Failed to shutdown ZAP (it's not running?)");
        }

        getContext().onSuccess(true);
        return true;
    }
}