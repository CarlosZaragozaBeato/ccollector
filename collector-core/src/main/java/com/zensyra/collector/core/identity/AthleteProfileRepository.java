package com.zensyra.collector.core.identity;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class AthleteProfileRepository implements PanacheRepositoryBase<AthleteProfile, UUID> {
}
