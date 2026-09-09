package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** One bounded migration traversal, never a GET/startup history scan. */
public final class V27__backfill_observation_subjects extends BaseJavaMigration {
    @Override public void migrate(Context context) {
        var db=new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(),true));
        com.lolfm.career.CareerObservationIndex.backfill(db);
        com.lolfm.career.CareerObservationIndex.backfillTradingClubs(db);
    }
}
