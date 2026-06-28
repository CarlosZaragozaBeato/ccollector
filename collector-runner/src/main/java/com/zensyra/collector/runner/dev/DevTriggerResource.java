package com.zensyra.collector.runner.dev;

import com.zensyra.collector.runner.scheduling.SyncJobExecutor;
import com.zensyra.collector.core.sync.DataCollector;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Path("/dev/trigger")
@ApplicationScoped
@IfBuildProfile("dev")
@Produces(MediaType.APPLICATION_JSON)
public class DevTriggerResource {

    private static final Logger LOG = Logger.getLogger(DevTriggerResource.class);

    @Inject
    Instance<DataCollector> collectors;

    @Inject
    SyncJobExecutor syncJobExecutor;

    @POST
    @Path("/{jobId}")
    public Response triggerJob(@PathParam("jobId") String jobId) {
        for (DataCollector collector : collectors) {
            for (var job : collector.jobs()) {
                if (job.jobId().equals(jobId)) {
                    LOG.infof("DevTriggerResource: disparando job '%s' manualmente", jobId);
                    try {
                        syncJobExecutor.execute(job);
                    } catch (Exception e) {
                        LOG.errorf(e, "DevTriggerResource: job '%s' failed", jobId);
                        return Response.serverError()
                                .entity(Map.of("error", "job execution failed", "job", jobId))
                                .build();
                    }
                    return Response.ok(Map.of("triggered", jobId)).build();
                }
            }
        }
        return Response.status(404).entity(Map.of("error", "job not found", "jobId", jobId)).build();
    }

    @GET
    @Path("/jobs")
    public Response listJobs() {
        List<String> jobIds = new ArrayList<>();
        for (DataCollector collector : collectors) {
            for (var job : collector.jobs()) {
                jobIds.add(job.jobId());
            }
        }
        return Response.ok(jobIds).build();
    }
}
