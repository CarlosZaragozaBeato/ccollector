package com.zensyra.collector.runner.admin;

import com.zensyra.collector.core.credential.IntegrationCredential;
import com.zensyra.collector.core.credential.IntegrationCredentialRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;

@Path("/admin/credentials")
@ApplicationScoped
@AdminTokenAuth
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CredentialSeedResource {

    private static final Logger LOG = Logger.getLogger(CredentialSeedResource.class);

    @Inject
    IntegrationCredentialRepository repo;

    @POST
    @Path("/strava")
    @Transactional
    public Response seedStrava(StravaCredentialRequest request) {
        if (request == null || isBlank(request.clientId()) || isBlank(request.clientSecret())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "clientId and clientSecret are required"))
                    .build();
        }

        repo.deleteBySource(IntegrationSource.STRAVA);

        IntegrationCredential credential = new IntegrationCredential();
        credential.setSource(IntegrationSource.STRAVA);
        credential.setClientId(request.clientId());
        credential.setClientSecret(request.clientSecret());
        repo.persist(credential);

        LOG.infof("CredentialSeedResource: seeded STRAVA credentials for clientId '%s'", request.clientId());

        return Response.ok(Map.of(
                "source", "STRAVA",
                "clientId", request.clientId(),
                "seeded", true
        )).build();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public record StravaCredentialRequest(String clientId, String clientSecret) {}
}
