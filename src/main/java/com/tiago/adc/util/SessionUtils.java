package com.tiago.adc.util;

import com.google.cloud.datastore.*;
import java.time.Instant;

public class SessionUtils {

    private static final String SESSION_KIND = "Session";

    public static boolean isSessionValid(Datastore datastore, String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        Query<Entity> query = Query.newEntityQueryBuilder()
                .setKind(SESSION_KIND)
                .setFilter(
                        StructuredQuery.CompositeFilter.and(
                                StructuredQuery.PropertyFilter.eq("verificador", token),
                                StructuredQuery.PropertyFilter.eq("active", true)
                        )
                )
                .build();

        QueryResults<Entity> sessions = datastore.run(query);
        if (!sessions.hasNext()) {
            return false;
        }

        Entity session = sessions.next();
        Instant now = Instant.now();
        Instant validTo = Instant.parse(session.getString("valid_to"));

        return now.isBefore(validTo);  // true se a sessão ainda estiver válida
    }

    public static Entity getSessionEntity(Datastore datastore, String token) {
        Query<Entity> query = Query.newEntityQueryBuilder()
                .setKind(SESSION_KIND)
                .setFilter(
                        StructuredQuery.PropertyFilter.eq("verificador", token)
                )
                .build();

        QueryResults<Entity> results = datastore.run(query);
        return results.hasNext() ? results.next() : null;
    }
}
