package com.zensyra.collector.runner.admin;

import com.zensyra.collector.core.sync.DataCollector;
import com.zensyra.collector.core.sync.SyncJobRecordRepository;
import com.zensyra.collector.runner.scheduling.JobRegistry;
import com.zensyra.collector.runner.scheduling.SyncJobExecutor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Path("/admin")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class AdminTriggerResource {

    private static final Logger LOG = Logger.getLogger(AdminTriggerResource.class);

    @ConfigProperty(name = "admin.token")
    Optional<String> adminToken;

    @Inject
    Instance<DataCollector> collectors;

    @Inject
    JobRegistry jobRegistry;

    @Inject
    SyncJobExecutor syncJobExecutor;

    @Inject
    SyncJobRecordRepository syncJobRecordRepository;

    private Response authenticate(String token) {
        if (adminToken.isEmpty() || adminToken.get().isBlank()) {
            return Response.status(503).entity(Map.of("error", "admin endpoint not configured")).build();
        }
        if (token == null || !MessageDigest.isEqual(
                adminToken.get().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            return Response.status(401).entity(Map.of("error", "unauthorized")).build();
        }
        return null;
    }

    @POST
    @Path("/trigger/{jobId}")
    public Response triggerJob(
            @HeaderParam("X-Admin-Token") String token,
            @PathParam("jobId") String jobId) {

        Response authError = authenticate(token);
        if (authError != null) return authError;

        for (DataCollector collector : collectors) {
            for (var job : collector.jobs()) {
                if (job.jobId().equals(jobId)) {
                    LOG.infof("AdminTriggerResource: disparando job '%s' manualmente", jobId);
                    try {
                        syncJobExecutor.execute(job);
                    } catch (Exception e) {
                        LOG.errorf(e, "AdminTriggerResource: job '%s' falló", jobId);
                        return Response.serverError()
                                .entity(Map.of("error", "job execution failed", "job", jobId))
                                .build();
                    }
                    return Response.ok(
                            Map.of("triggered", jobId, "at", Instant.now().toString())
                    ).build();
                }
            }
        }
        return Response.status(404).entity(Map.of("error", "job not found", "jobId", jobId)).build();
    }

    @GET
    @Path("/jobs")
    public Response listJobs(@HeaderParam("X-Admin-Token") String token) {
        Response authError = authenticate(token);
        if (authError != null) return authError;

        List<Map<String, Object>> jobs = new ArrayList<>();
        for (DataCollector collector : collectors) {
            for (var job : collector.jobs()) {
                var record = syncJobRecordRepository.findByJobId(job.jobId());
                Map<String, Object> entry = new HashMap<>();
                entry.put("jobId", job.jobId());
                entry.put("lastSuccessAt", record.map(r -> (Object) r.getLastSuccessAt()).orElse(null));
                entry.put("lastFailureAt", record.map(r -> (Object) r.getLastFailureAt()).orElse(null));
                jobs.add(entry);
            }
        }
        return Response.ok(jobs).build();
    }
}
